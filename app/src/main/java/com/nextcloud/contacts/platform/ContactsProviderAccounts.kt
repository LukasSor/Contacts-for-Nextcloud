package com.nextcloud.contacts.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Distinct contact accounts stored in the Android Contacts Provider
 * (same source as "Visible accounts" in other contacts apps).
 */
data class DeviceContactAccount(
    val accountType: String,
    val accountName: String,
) {
    val key: String get() = ContactsProviderAccounts.accountKey(accountType, accountName)

    val displayName: String
        get() {
            return when (accountType) {
                KnownContactAccountTypes.GOOGLE ->
                    accountName.takeIf { it.isNotBlank() } ?: "Google"
                KnownContactAccountTypes.NEXTCLOUD -> "Nextcloud Contacts"
                KnownContactAccountTypes.DEVICE -> "Phone"
                KnownContactAccountTypes.SIM -> "SIM"
                else -> accountName.takeIf { it.isNotBlank() }
                    ?: ContactsProviderAccounts.friendlyAccountTypeLabel(accountType)
            }
        }

    val subtitle: String
        get() = accountType.ifBlank { "local" }
}

object ContactsProviderAccounts {

    fun discover(context: Context): List<DeviceContactAccount> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val accounts = linkedMapOf<String, DeviceContactAccount>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME,
            ),
            "${ContactsContract.RawContacts.DELETED} = 0",
            null,
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} COLLATE LOCALIZED ASC, " +
                "${ContactsContract.RawContacts.ACCOUNT_NAME} COLLATE LOCALIZED ASC",
        )?.use { cursor ->
            val typeColumn = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
            val nameColumn = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
            while (cursor.moveToNext()) {
                if (typeColumn < 0 || nameColumn < 0) continue
                val accountType = cursor.getString(typeColumn).orEmpty()
                val accountName = cursor.getString(nameColumn).orEmpty()
                val account = DeviceContactAccount(accountType, accountName)
                accounts[account.key] = account
            }
        }
        return accounts.values.toList()
    }

    fun isDisabled(
        accountType: String?,
        accountName: String?,
        disabledKeys: Set<String>,
    ): Boolean {
        if (accountType.isNullOrBlank()) return false
        return accountKey(accountType, accountName.orEmpty()) in disabledKeys
    }

    fun accountKey(accountType: String, accountName: String): String =
        "$accountType|$accountName"

    fun friendlyAccountTypeLabel(accountType: String): String {
        val token = accountType.substringAfterLast('.').replace('_', ' ')
        if (token.isBlank()) return accountType
        return token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
