package com.nextcloud.contacts.platform

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.local.ContactDao
import com.nextcloud.contacts.data.local.ContactPhotoStore
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.platform.ContactsPermissionRequester
import com.nextcloud.contacts.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first

class SystemContactsExporter(
    private val context: Context,
    private val contactDao: ContactDao,
    private val photoStore: ContactPhotoStore,
    private val links: SystemContactLinks,
    private val userPreferences: UserPreferences,
) {
    private val resolver = context.contentResolver
    private val accountManager = AccountManager.get(context)

    suspend fun isEnabled(): Boolean = userPreferences.exportToDeviceContacts.first()

    suspend fun setEnabled(enabled: Boolean) {
        if (enabled) {
            requireWritePermission()
            ensureAccount()
            exportAll()
            userPreferences.setExportToDeviceContacts(true)
        } else {
            userPreferences.setExportToDeviceContacts(false)
            clearExported()
        }
    }

    suspend fun exportContact(appContactId: Long) {
        if (!isEnabled() || !hasWritePermission()) return
        val contact = contactDao.getById(appContactId) ?: return
        if (contact.contact.deleted) {
            deleteContact(appContactId)
            return
        }
        ensureAccount()
        val rawId = activeRawContactId(appContactId, contact)
        if (rawId != null) {
            replaceDataRows(rawId, contact)
            links.setLink(appContactId, rawId)
        } else {
            val inserted = insertRawContact(contact)
            links.setLink(appContactId, inserted)
        }
    }

    suspend fun deleteContact(appContactId: Long) {
        val rawId = links.getRawContactId(appContactId) ?: return
        if (hasWritePermission()) {
          
            resolver.delete(syncAdapterRawContactUri(rawId), null, null)
        }
        links.removeAppContact(appContactId)
    }

    suspend fun exportAll() {
        if (!isEnabled() || !hasWritePermission()) return
        ensureAccount()
        contactDao.getAllWithDetails().forEach { contact ->
            exportContact(contact.contact.id)
        }
        purgeStaleRawContacts()
    }

    suspend fun clearExported() {
        links.allAppContactIds().forEach { deleteContact(it) }
        links.clear()
    }

    suspend fun lookupAppContactId(systemUri: Uri): Long? {
        val contactUri = ContactsContract.Contacts.lookupContact(resolver, systemUri) ?: return null
        val contactId = contactUri.lastPathSegment?.toLongOrNull() ?: return null
        val rawContactId = queryPrimaryRawContactId(contactId) ?: return null
        return links.getAppContactId(rawContactId)
    }

    /**
     * Data row on our own raw contact, after export. CoMaps and other pickers
     * read column data1 (formatted address, phone number, or email).
     * Returns null when device export is off or this contact is not in that account.
     */
    suspend fun externalPickUri(appContactId: Long, kind: ContactPickKind): Uri? {
        if (!isEnabled() || !hasWritePermission()) return null
        exportContact(appContactId)
        if (kind == ContactPickKind.CONTACT) return lookupUriForAppContact(appContactId)
        val rawId = links.getRawContactId(appContactId) ?: return null
        val mime = when (kind) {
            ContactPickKind.POSTAL_ADDRESS -> StructuredPostal.CONTENT_ITEM_TYPE
            ContactPickKind.PHONE -> Phone.CONTENT_ITEM_TYPE
            ContactPickKind.EMAIL -> Email.CONTENT_ITEM_TYPE
            ContactPickKind.CONTACT -> return lookupUriForAppContact(appContactId)
        }
        val dataId = firstFilledDataId(rawId, mime) ?: return null
        return ContentUris.withAppendedId(Data.CONTENT_URI, dataId)
    }

    suspend fun lookupUriForAppContact(appContactId: Long): Uri? {
        val rawId = links.getRawContactId(appContactId) ?: return null
        val contactId = resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.CONTACT_ID),
            "${RawContacts._ID} = ?",
            arrayOf(rawId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        } ?: return null
        if (contactId <= 0L) return null
        val contactUri = android.content.ContentUris.withAppendedId(
            ContactsContract.Contacts.CONTENT_URI,
            contactId,
        )
        return ContactsContract.Contacts.getLookupUri(resolver, contactUri)
    }

    private fun queryPrimaryRawContactId(contactId: Long): Long? {
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()),
            "${RawContacts._ID} ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }

    private fun hasWritePermission(): Boolean =
        ContactsPermissionRequester.hasDeviceExportPermissions(context)

    private fun requireWritePermission() {
        if (!hasWritePermission()) {
            throw SecurityException("Contacts read and write permissions required")
        }
    }

    private fun ensureAccount() {
        val account = account()
        if (accountManager.getAccountsByType(ACCOUNT_TYPE).none { it.name == ACCOUNT_NAME }) {
            accountManager.addAccountExplicitly(account, null, null)
        }
    }

    private fun account(): Account = Account(ACCOUNT_NAME, ACCOUNT_TYPE)

    private suspend fun activeRawContactId(appContactId: Long, contact: ContactWithDetails): Long? {
        val linked = links.getRawContactId(appContactId)
        if (linked != null && rawContactIsActive(linked)) return linked
        return findActiveRawContactIdBySource(sourceId(contact))
    }

    private fun rawContactIsActive(rawContactId: Long): Boolean {
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.DELETED),
            "${RawContacts._ID} = ? AND ${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ?",
            arrayOf(rawContactId.toString(), ACCOUNT_TYPE, ACCOUNT_NAME),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return false
            val deletedColumn = cursor.getColumnIndex(RawContacts.DELETED)
            if (deletedColumn < 0) return true
            return cursor.getInt(deletedColumn) == 0
        }
        return false
    }

    private fun findActiveRawContactIdBySource(sourceId: String): Long? {
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.DELETED),
            "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ? AND ${RawContacts.SOURCE_ID} = ?",
            arrayOf(ACCOUNT_TYPE, ACCOUNT_NAME, sourceId),
            "${RawContacts._ID} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(RawContacts._ID)
            val deletedColumn = cursor.getColumnIndex(RawContacts.DELETED)
            if (idColumn < 0) return null
            while (cursor.moveToNext()) {
                val deleted = if (deletedColumn < 0) 0 else cursor.getInt(deletedColumn)
                if (deleted == 0) return cursor.getLong(idColumn)
            }
        }
        return null
    }

    private fun insertRawContact(contact: ContactWithDetails): Long {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .withValue(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
                .withValue(RawContacts.SOURCE_ID, sourceId(contact))
                .build(),
        )
        addDataRows(ops, rawContactBackReference = 0, rawContactId = null, contact)
        requireWritePermission()
        return try {
            val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            results.first().uri?.lastPathSegment?.toLongOrNull()
                ?: error(context.getString(R.string.msg_insert_contact_failed))
        } catch (e: SecurityException) {
            throw SecurityException("Contacts write permission required", e)
        }
    }

    private fun replaceDataRows(rawContactId: Long, contact: ContactWithDetails) {
        requireWritePermission()
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection("${Data.RAW_CONTACT_ID} = ?", arrayOf(rawContactId.toString()))
                .build(),
        )
        ops.add(
            ContentProviderOperation.newUpdate(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
            )
                .withValue(RawContacts.SOURCE_ID, sourceId(contact))
                .build(),
        )
        addDataRows(ops, rawContactBackReference = null, rawContactId = rawContactId, contact)
        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: SecurityException) {
            throw SecurityException("Contacts write permission required", e)
        }
    }

    private suspend fun purgeStaleRawContacts() {
        if (!hasWritePermission()) return
        val keep = links.linkedRawContactIds()
        val staleIds = ArrayList<Long>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.DELETED),
            "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ? AND " +
                "${RawContacts.DELETED} IN (0, 1)",
            arrayOf(ACCOUNT_TYPE, ACCOUNT_NAME),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(RawContacts._ID)
            val deletedColumn = cursor.getColumnIndex(RawContacts.DELETED)
            if (idColumn < 0) return@use
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val deleted = if (deletedColumn < 0) 0 else cursor.getInt(deletedColumn)
                val tombstone = deleted != 0
                val unlinkedLive = deleted == 0 && keep.isNotEmpty() && id !in keep
                if (tombstone || unlinkedLive) staleIds.add(id)
            }
        }
        staleIds.chunked(100).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            resolver.delete(
                syncAdapterRawContactsUri(),
                "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ? AND " +
                    "${RawContacts._ID} IN ($placeholders)",
                arrayOf(ACCOUNT_TYPE, ACCOUNT_NAME) + chunk.map { it.toString() },
            )
        }
    }

    private fun sourceId(contact: ContactWithDetails): String {
        val remoteUid = contact.contact.remoteUid?.trim().orEmpty()
        if (remoteUid.isNotEmpty()) return remoteUid
        return "local:${contact.contact.id}"
    }

    private fun syncAdapterRawContactsUri(): Uri =
        RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()

    private fun syncAdapterRawContactUri(rawContactId: Long): Uri =
        ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId).buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()

    private fun addDataRows(
        ops: ArrayList<ContentProviderOperation>,
        rawContactBackReference: Int?,
        rawContactId: Long?,
        contact: ContactWithDetails,
    ) {
        fun insertData(): ContentProviderOperation.Builder {
            val builder = ContentProviderOperation.newInsert(Data.CONTENT_URI)
            if (rawContactId != null) {
                builder.withValue(Data.RAW_CONTACT_ID, rawContactId)
            } else {
                builder.withValueBackReference(Data.RAW_CONTACT_ID, rawContactBackReference ?: 0)
            }
            return builder
        }

        ops.add(
            insertData()
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.GIVEN_NAME, contact.contact.givenName)
                .withValue(StructuredName.FAMILY_NAME, contact.contact.familyName)
                .withValue(StructuredName.DISPLAY_NAME, contact.contact.displayName)
                .build(),
        )

        contact.phones.forEach { phone ->
            ops.add(
                insertData()
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, phone.value)
                    .withValue(Phone.TYPE, mapPhoneType(phone.type))
                    .build(),
            )
        }

        contact.emails.forEach { email ->
            ops.add(
                insertData()
                    .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                    .withValue(Email.ADDRESS, email.value)
                    .withValue(Email.TYPE, Email.TYPE_HOME)
                    .build(),
            )
        }

        contact.addresses.forEach { address ->
            val formatted = shareableAddress(address)
            if (formatted.isBlank()) return@forEach
            ops.add(
                insertData()
                    .withValue(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(StructuredPostal.FORMATTED_ADDRESS, formatted)
                    .withValue(StructuredPostal.STREET, address.street)
                    .withValue(StructuredPostal.POBOX, address.poBox)
                    .withValue(StructuredPostal.CITY, address.city)
                    .withValue(StructuredPostal.REGION, address.region)
                    .withValue(StructuredPostal.POSTCODE, address.postalCode)
                    .withValue(StructuredPostal.COUNTRY, address.country)
                    .withValue(StructuredPostal.TYPE, mapAddressType(address.type))
                    .build(),
            )
        }

        photoStore.readPhoto(contact.contact.id)?.let { bytes ->
            ops.add(
                insertData()
                    .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, bytes)
                    .build(),
            )
        }
    }

    private fun firstFilledDataId(rawContactId: Long, mimeType: String): Long? {
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.DATA1),
            "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
            arrayOf(rawContactId.toString(), mimeType),
            "${Data._ID} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(Data._ID)
            val valueColumn = cursor.getColumnIndex(Data.DATA1)
            if (idColumn < 0) return null
            var fallback: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val value = if (valueColumn < 0) "" else cursor.getString(valueColumn).orEmpty()
                if (value.isNotBlank()) return id
                if (fallback == null) fallback = id
            }
            return fallback
        }
        return null
    }

    private fun mapAddressType(type: String): Int = when (type.lowercase()) {
        "work" -> StructuredPostal.TYPE_WORK
        "other" -> StructuredPostal.TYPE_OTHER
        else -> StructuredPostal.TYPE_HOME
    }

    private fun mapPhoneType(type: String): Int = when (type.lowercase()) {
        "mobile", "cell" -> Phone.TYPE_MOBILE
        "work" -> Phone.TYPE_WORK
        "fax" -> Phone.TYPE_FAX_WORK
        else -> Phone.TYPE_HOME
    }

    companion object {
        const val ACCOUNT_TYPE = KnownContactAccountTypes.NEXTCLOUD
        const val ACCOUNT_NAME = "Nextcloud Contacts"
    }
}
