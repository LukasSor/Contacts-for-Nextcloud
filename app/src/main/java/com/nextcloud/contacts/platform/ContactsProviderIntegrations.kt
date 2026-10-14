package com.nextcloud.contacts.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Discovers actionable provider rows from Android Contacts (any app that registers
 * custom vnd.* MIME types), without a hardcoded app registry.
 */
object ContactsProviderIntegrations {

    private val discoveredGroupsCache = java.util.concurrent.ConcurrentHashMap<String, List<AppGroup>>()
    private val discoverMutexes = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    /** Last grouped discovery for these phones (same contact detail session / revisits). */
    fun cachedAppGroups(phones: List<String>): List<AppGroup> {
        val key = phonesCacheKey(phones) ?: return emptyList()
        return discoveredGroupsCache[key].orEmpty()
    }

    fun cacheAppGroups(phones: List<String>, groups: List<AppGroup>) {
        val key = phonesCacheKey(phones) ?: return
        discoveredGroupsCache[key] = groups
    }

    /**
     * One ContentProvider discovery per phone set per process — concurrent callers
     * (connected apps + IM on contact detail) share the same result.
     */
    suspend fun discoverGroupedCoalesced(context: Context, phones: List<String>): List<AppGroup> {
        cachedAppGroups(phones).takeIf { it.isNotEmpty() }?.let { return it }
        val key = phonesCacheKey(phones) ?: return emptyList()
        val mutex = discoverMutexes.getOrPut(key) { Mutex() }
        return mutex.withLock {
            cachedAppGroups(phones).takeIf { it.isNotEmpty() }?.let { return@withLock it }
            val actions = discover(context, phones)
            val groups = groupActions(context, actions)
            cacheAppGroups(phones, groups)
            groups
        }
    }

    fun phonesCacheKey(phones: List<String>): String? {
        val normalized = phones.map { it.trim().lowercase() }.filter { it.isNotBlank() }.sorted()
        return normalized.takeIf { it.isNotEmpty() }?.joinToString("\u0000")
    }

    data class Action(
        val dataId: Long,
        val mimeType: String,
        val label: String,
        val detail: String?,
        val accountType: String?,
        val accountName: String?,
        val packageName: String?,
        val messagingAction: MessagingAction?,
    ) {
        val accountKey: String
            get() = ProviderIntegrationSupport.accountKey(accountType, accountName)
    }

    data class AppGroup(
        val key: String,
        val label: String,
        val packageName: String?,
        val accountType: String?,
        val actions: List<Action>,
    )

    fun discover(context: Context, phones: List<String>): List<Action> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val phoneDigits = phones.map { MessagingContactsIntents.digitsOnly(it) }.filter { it.isNotEmpty() }
        val contactIds = phones
            .flatMap { lookupContactIds(context, it) }
            .distinct()

        if (contactIds.isEmpty()) return emptyList()

        val rawAccounts = loadRawAccounts(context, contactIds)
        val actions = linkedMapOf<Long, Action>()

        contactIds.forEach { contactId ->
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data._ID,
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.DATA2,
                    ContactsContract.Data.DATA3,
                    ContactsContract.Data.RAW_CONTACT_ID,
                ),
                "${ContactsContract.Data.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(ContactsContract.Data._ID)
                val mimeColumn = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val summaryColumn = cursor.getColumnIndex(ContactsContract.Data.DATA2)
                val detailColumn = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                val rawIdColumn = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID)

                while (cursor.moveToNext()) {
                    if (mimeColumn < 0 || idColumn < 0) continue
                    val mimeType = cursor.getString(mimeColumn).orEmpty()
                    if (!isProviderActionMime(mimeType)) continue

                    val dataId = cursor.getLong(idColumn)
                    if (dataId in actions) continue

                    val rawContactId = if (rawIdColumn >= 0) cursor.getLong(rawIdColumn) else -1L
                    val rawAccount = rawAccounts[rawContactId]
                    val accountType = rawAccount?.accountType
                    val accountName = rawAccount?.accountName

                    if (!ProviderIntegrationSupport.isMessagingProviderAccount(accountType)) continue

                    val messagingAction = ProviderIntegrationSupport.inferMessagingAction(mimeType)
                    val summary = if (summaryColumn >= 0) cursor.getString(summaryColumn).orEmpty() else ""
                    val detail = if (detailColumn >= 0) cursor.getString(detailColumn).orEmpty() else ""

                    if (messagingAction == MessagingAction.Voice || messagingAction == MessagingAction.Video) {
                        val rowDigits = MessagingContactsIntents.digitsOnly(detail.ifBlank { summary })
                        if (rowDigits.isNotEmpty() && phoneDigits.isNotEmpty() &&
                            phoneDigits.none { MessagingContactsIntents.phonesMatch(it, rowDigits) }
                        ) {
                            continue
                        }
                    }

                    val packageName = ProviderIntegrationSupport
                        .resolveInstalledPackages(
                            context = context,
                            mimeType = mimeType,
                            accountType = accountType,
                            dataId = dataId,
                        )
                        .firstOrNull()
                        ?: ProviderIntegrationSupport.packageCandidates(
                            mimeType = mimeType,
                            accountType = accountType,
                        ).firstOrNull()

                    val label = ProviderIntegrationSupport.actionLabel(
                        context = context,
                        mimeType = mimeType,
                        messagingAction = messagingAction,
                        summary = summary,
                        detail = detail,
                    )

                    actions[dataId] = Action(
                        dataId = dataId,
                        mimeType = mimeType,
                        label = label,
                        detail = detail.takeIf { it.isNotBlank() && it != label },
                        accountType = accountType,
                        accountName = accountName,
                        packageName = packageName,
                        messagingAction = messagingAction,
                    )
                }
            }
        }

        return actions.values.sortedWith(::compareActions)
    }

    fun groupActions(context: Context, actions: List<Action>): List<AppGroup> {
        return actions
            .groupBy { it.accountKey }
            .map { (key, groupedActions) ->
                val first = groupedActions.first()
                val packageName = groupedActions.mapNotNull { it.packageName }.firstOrNull()
                AppGroup(
                    key = key,
                    label = ProviderIntegrationSupport.appLabel(
                        context = context,
                        accountType = first.accountType,
                        accountName = first.accountName,
                        packageName = packageName,
                    ),
                    packageName = packageName,
                    accountType = first.accountType,
                    actions = groupedActions.sortedWith(::compareActions),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun compareActions(a: Action, b: Action): Int {
        val orderA = a.messagingAction?.sortOrder ?: Int.MAX_VALUE
        val orderB = b.messagingAction?.sortOrder ?: Int.MAX_VALUE
        if (orderA != orderB) return orderA - orderB
        return a.label.lowercase().compareTo(b.label.lowercase())
    }

    private fun isProviderActionMime(mimeType: String): Boolean =
        mimeType.startsWith("vnd.android.cursor.item/vnd.", ignoreCase = true)

    private data class RawAccount(
        val accountType: String,
        val accountName: String,
    )

    private fun loadRawAccounts(context: Context, contactIds: List<Long>): Map<Long, RawAccount> {
        if (contactIds.isEmpty()) return emptyMap()
        val placeholders = contactIds.joinToString(",") { "?" }
        val result = mutableMapOf<Long, RawAccount>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME,
            ),
            "${ContactsContract.RawContacts.CONTACT_ID} IN ($placeholders) AND ${ContactsContract.RawContacts.DELETED} = 0",
            contactIds.map { it.toString() }.toTypedArray(),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
            val typeColumn = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
            val nameColumn = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
            while (cursor.moveToNext()) {
                if (idColumn < 0 || typeColumn < 0 || nameColumn < 0) continue
                result[cursor.getLong(idColumn)] = RawAccount(
                    accountType = cursor.getString(typeColumn).orEmpty(),
                    accountName = cursor.getString(nameColumn).orEmpty(),
                )
            }
        }
        return result
    }

    private fun lookupContactIds(context: Context, phone: String): List<Long> {
        val variants = buildSet {
            val trimmed = phone.trim()
            if (trimmed.isNotBlank()) add(trimmed)
            val digits = phone.filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                add(digits)
                add("+$digits")
            }
        }

        val ids = linkedSetOf<Long>()
        variants.forEach { variant ->
            val digits = variant.filter { it.isDigit() }
            if (digits.isEmpty()) return@forEach
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(digits),
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                while (cursor.moveToNext()) {
                    if (idColumn >= 0) ids.add(cursor.getLong(idColumn))
                }
            }
        }
        return ids.toList()
    }
}
