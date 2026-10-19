package com.nextcloud.contacts.platform

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract.Intents
import com.nextcloud.contacts.data.local.ContactInput

sealed class ContactsLaunch {
    data object Home : ContactsLaunch()
    data object ContactList : ContactsLaunch()
    data class SearchContacts(val query: String) : ContactsLaunch()
    data object OpenSettings : ContactsLaunch()
    data class ViewContact(val appContactId: Long?) : ContactsLaunch()
    data class EditContact(val appContactId: Long?) : ContactsLaunch()
    data class InsertContact(val prefilled: ContactInput?) : ContactsLaunch()
    data class PickContact(val kind: ContactPickKind) : ContactsLaunch()
    data class ImportVCards(val uris: List<Uri>, val fallbackText: String?) : ContactsLaunch()
    data class ShowOrCreateContact(val phone: String?, val email: String?) : ContactsLaunch()
}

enum class ContactPickKind {
    CONTACT,
    POSTAL_ADDRESS,
    PHONE,
    EMAIL,
}

object ContactsIntentRouter {
    fun parse(intent: Intent?): ContactsLaunch {
        if (intent == null) return ContactsLaunch.Home
        if (isIncomingVCard(intent)) {
            return ContactsLaunch.ImportVCards(
                uris = vCardUris(intent),
                fallbackText = vCardFallbackText(intent),
            )
        }
        return when (intent.action) {
            Intent.ACTION_VIEW -> when (intent.data?.scheme?.lowercase()) {
                "tel" -> showOrCreate(intent)
                else -> ContactsLaunch.ViewContact(appContactId = null)
            }
            Intent.ACTION_EDIT -> ContactsLaunch.EditContact(appContactId = null)
            Intent.ACTION_INSERT,
            Intent.ACTION_INSERT_OR_EDIT,
            -> ContactsLaunch.InsertContact(parseInsertExtras(intent))
            Intent.ACTION_PICK,
            Intent.ACTION_GET_CONTENT,
            -> ContactsLaunch.PickContact(pickKind(intent))
            SHOW_OR_CREATE_CONTACT -> showOrCreate(intent)
            QUICK_CONTACT,
            QUICK_CONTACT_LEGACY,
            -> ContactsLaunch.ViewContact(appContactId = null)
            in LIST_ACTIONS -> ContactsLaunch.ContactList
            Intent.ACTION_SEARCH -> {
                val query = intent.getStringExtra(SearchManager.QUERY)?.trim().orEmpty()
                if (query.isEmpty()) ContactsLaunch.ContactList else ContactsLaunch.SearchContacts(query)
            }
            Intent.ACTION_APPLICATION_PREFERENCES -> ContactsLaunch.OpenSettings
            else -> {
                if (intent.hasCategory(Intent.CATEGORY_APP_CONTACTS)) {
                    ContactsLaunch.ContactList
                } else {
                    ContactsLaunch.Home
                }
            }
        }
    }

    fun systemUri(intent: Intent?): Uri? = intent?.data

    private fun showOrCreate(intent: Intent): ContactsLaunch.ShowOrCreateContact {
        val data = intent.data
        val phone = when (data?.scheme?.lowercase()) {
            "tel" -> phoneFrom(data)
            else -> null
        } ?: intent.getStringExtra(Intents.Insert.PHONE)?.trim()?.takeIf { it.isNotEmpty() }
        val email = when (data?.scheme?.lowercase()) {
            "mailto" -> emailFrom(data)
            else -> null
        } ?: intent.getStringExtra(Intents.Insert.EMAIL)?.trim()?.takeIf { it.isNotEmpty() }
        return ContactsLaunch.ShowOrCreateContact(phone = phone, email = email)
    }

    private fun pickKind(intent: Intent): ContactPickKind {
        val type = normalizeMime(intent.type).orEmpty()
        return when {
            "postal-address" in type -> ContactPickKind.POSTAL_ADDRESS
            "phone" in type -> ContactPickKind.PHONE
            "email" in type -> ContactPickKind.EMAIL
            else -> ContactPickKind.CONTACT
        }
    }

    private fun parseInsertExtras(intent: Intent): ContactInput? {
        val hasData = listOf(
            Intents.Insert.NAME,
            Intents.Insert.PHONE,
            Intents.Insert.EMAIL,
            Intents.Insert.COMPANY,
            Intents.Insert.NOTES,
        ).any { intent.hasExtra(it) }
        if (!hasData) return null

        val fullName = intent.getStringExtra(Intents.Insert.NAME).orEmpty()
        val phones = buildList {
            intent.getStringExtra(Intents.Insert.PHONE)?.takeIf { it.isNotBlank() }?.let { add("mobile" to it) }
            intent.getStringExtra(Intents.Insert.SECONDARY_PHONE)?.takeIf { it.isNotBlank() }?.let { add("other" to it) }
            intent.getStringExtra(Intents.Insert.TERTIARY_PHONE)?.takeIf { it.isNotBlank() }?.let { add("other" to it) }
        }
        val emails = buildList {
            intent.getStringExtra(Intents.Insert.EMAIL)?.takeIf { it.isNotBlank() }?.let { add("home" to it) }
            intent.getStringExtra(Intents.Insert.SECONDARY_EMAIL)?.takeIf { it.isNotBlank() }?.let { add("home" to it) }
        }

        return ContactInput(
            givenName = fullName,
            familyName = "",
            organization = intent.getStringExtra(Intents.Insert.COMPANY).orEmpty(),
            notes = intent.getStringExtra(Intents.Insert.NOTES).orEmpty(),
            phones = phones.ifEmpty { listOf("" to "") }
                .map { (type, value) ->
                    com.nextcloud.contacts.data.local.VCardTypedFieldInput.phone(
                        type = type.ifBlank { "mobile" },
                        value = value,
                    )
                },
            emails = emails.ifEmpty { listOf("" to "") }
                .map { (type, value) ->
                    com.nextcloud.contacts.data.local.VCardTypedFieldInput.email(
                        type = type.ifBlank { "home" },
                        value = value,
                    )
                },
        )
    }

    private const val SHOW_OR_CREATE_CONTACT = "com.android.contacts.action.SHOW_OR_CREATE_CONTACT"
    private const val QUICK_CONTACT = "android.provider.action.QUICK_CONTACT"
    private const val QUICK_CONTACT_LEGACY = "com.android.contacts.action.QUICK_CONTACT"
    private val LIST_ACTIONS = setOf(
        "com.android.contacts.action.LIST_DEFAULT",
        "com.android.contacts.action.LIST_CONTACTS",
        "com.android.contacts.action.LIST_ALL_CONTACTS",
        "com.android.contacts.action.LIST_CONTACTS_WITH_PHONES",
        "com.android.contacts.action.LIST_STARRED",
        "com.android.contacts.action.LIST_FREQUENT",
        "com.android.contacts.action.LIST_STREQUENT",
    )
}

private val VCARD_ACTIONS = setOf(
    Intent.ACTION_VIEW,
    Intent.ACTION_SEND,
    Intent.ACTION_SEND_MULTIPLE,
)

private val VCARD_MIMES = setOf(
    "text/vcard",
    "text/x-vcard",
    "text/directory",
    "application/vcard",
    "text/vcf",
)

internal fun isIncomingVCard(intent: Intent): Boolean {
    if (intent.action !in VCARD_ACTIONS) return false
    if (isContactCursorMime(intent.type)) return false
    if (isVCardMime(intent.type)) return true
    if (intent.data?.let(::uriLooksLikeVCard) == true) return true
    if (streamUris(intent).any(::uriLooksLikeVCard)) return true
    if (vCardFallbackText(intent) != null) return true
    val description = intent.clipData?.description ?: return false
    for (index in 0 until description.mimeTypeCount) {
        if (isVCardMime(description.getMimeType(index))) return true
    }
    return false
}

internal fun vCardUris(intent: Intent): List<Uri> {
    val uris = LinkedHashSet<Uri>()
    intent.data?.let { uri ->
        if (uri.scheme.equals("tel", true) || uri.scheme.equals("mailto", true)) return@let
        uris.add(uri)
    }
    uris.addAll(streamUris(intent))
    return uris.toList()
}

internal fun vCardFallbackText(intent: Intent): String? =
    intent.getStringExtra(Intent.EXTRA_TEXT)
        ?.takeIf { it.contains("BEGIN:VCARD", ignoreCase = true) }

internal fun uriLooksLikeVCard(uri: Uri): Boolean {
    val candidates = listOf(uri.path, uri.lastPathSegment, Uri.decode(uri.toString()))
    return candidates.any { value ->
        val lower = value?.lowercase().orEmpty()
        lower.endsWith(".vcf") || lower.endsWith(".vcard")
    }
}

internal fun phoneFrom(uri: Uri): String? =
    Uri.decode(uri.schemeSpecificPart).trim().takeIf { it.isNotEmpty() }

internal fun emailFrom(uri: Uri): String? {
    val raw = uri.schemeSpecificPart.substringBefore('?').trim()
    return Uri.decode(raw).trim().takeIf { it.isNotEmpty() }
}

private fun isVCardMime(type: String?): Boolean = normalizeMime(type) in VCARD_MIMES

private fun isContactCursorMime(type: String?): Boolean =
    normalizeMime(type)?.startsWith("vnd.android.cursor.") == true

private fun normalizeMime(type: String?): String? =
    type?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

private fun streamUris(intent: Intent): List<Uri> {
    val uris = LinkedHashSet<Uri>()
    intent.parcelableUri(Intent.EXTRA_STREAM)?.let { uris.add(it) }
    intent.parcelableUriList(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
    intent.clipData?.let { clip ->
        for (index in 0 until clip.itemCount) {
            clip.getItemAt(index).uri?.let { uris.add(it) }
        }
    }
    return uris.toList()
}

private fun Intent.parcelableUri(name: String): Uri? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(name, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }

private fun Intent.parcelableUriList(name: String): ArrayList<Uri>? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableArrayListExtra(name, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(name)
    }
