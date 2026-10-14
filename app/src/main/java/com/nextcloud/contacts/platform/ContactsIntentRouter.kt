package com.nextcloud.contacts.platform

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.Intents
import com.nextcloud.contacts.data.local.ContactInput

sealed class ContactsLaunch {
    data object Home : ContactsLaunch()
    data object List : ContactsLaunch()
    data class ViewContact(val appContactId: Long?) : ContactsLaunch()
    data class EditContact(val appContactId: Long?) : ContactsLaunch()
    data class InsertContact(val prefilled: ContactInput?) : ContactsLaunch()
    data object PickContact : ContactsLaunch()
}

object ContactsIntentRouter {
    fun parse(intent: Intent?): ContactsLaunch {
        if (intent == null) return ContactsLaunch.Home
        return when (intent.action) {
            Intent.ACTION_VIEW -> ContactsLaunch.ViewContact(appContactId = null)
            Intent.ACTION_EDIT -> ContactsLaunch.EditContact(appContactId = null)
            Intent.ACTION_INSERT,
            Intent.ACTION_INSERT_OR_EDIT,
            -> ContactsLaunch.InsertContact(parseInsertExtras(intent))
            Intent.ACTION_PICK,
            Intent.ACTION_GET_CONTENT,
            -> ContactsLaunch.PickContact
            "com.android.contacts.action.LIST_DEFAULT",
            "com.android.contacts.action.LIST_CONTACTS",
            "com.android.contacts.action.LIST_ALL_CONTACTS",
            "com.android.contacts.action.LIST_CONTACTS_WITH_PHONES",
            -> ContactsLaunch.List
            Intent.ACTION_SEARCH -> ContactsLaunch.List
            else -> {
                if (intent.hasCategory(Intent.CATEGORY_APP_CONTACTS)) {
                    ContactsLaunch.List
                } else {
                    ContactsLaunch.Home
                }
            }
        }
    }

    fun systemUri(intent: Intent?): Uri? = intent?.data

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
}
