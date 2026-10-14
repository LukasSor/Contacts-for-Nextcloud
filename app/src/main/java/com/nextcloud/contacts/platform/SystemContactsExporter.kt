package com.nextcloud.contacts.platform

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
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
        val existingRawId = links.getRawContactId(appContactId)
        if (existingRawId != null) {
            updateRawContact(existingRawId, contact)
        } else {
            val rawId = insertRawContact(contact)
            links.setLink(appContactId, rawId)
        }
    }

    suspend fun deleteContact(appContactId: Long) {
        val rawId = links.getRawContactId(appContactId) ?: return
        if (hasWritePermission()) {
            resolver.delete(
                RawContacts.CONTENT_URI.buildUpon().appendPath(rawId.toString()).build(),
                null,
                null,
            )
        }
        links.removeAppContact(appContactId)
    }

    suspend fun exportAll() {
        if (!isEnabled() || !hasWritePermission()) return
        ensureAccount()
        contactDao.getAllWithDetails().forEach { contact ->
            exportContact(contact.contact.id)
        }
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

    private fun insertRawContact(contact: ContactWithDetails): Long {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .withValue(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
                .build(),
        )
        addDataRows(ops, rawContactBackReference = 0, contact)
        requireWritePermission()
        return try {
            val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            results.first().uri?.lastPathSegment?.toLongOrNull()
                ?: error(context.getString(R.string.msg_insert_contact_failed))
        } catch (e: SecurityException) {
            throw SecurityException("Contacts write permission required", e)
        }
    }

    private suspend fun updateRawContact(rawContactId: Long, contact: ContactWithDetails) {
        requireWritePermission()
        try {
            resolver.delete(
                RawContacts.CONTENT_URI.buildUpon().appendPath(rawContactId.toString()).build(),
                null,
                null,
            )
            val newRawId = insertRawContact(contact)
            links.setLink(contact.contact.id, newRawId)
        } catch (e: SecurityException) {
            throw SecurityException("Contacts write permission required", e)
        }
    }

    private fun addDataRows(
        ops: ArrayList<ContentProviderOperation>,
        rawContactBackReference: Int,
        contact: ContactWithDetails,
    ) {
        ops.add(
            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactBackReference)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.GIVEN_NAME, contact.contact.givenName)
                .withValue(StructuredName.FAMILY_NAME, contact.contact.familyName)
                .withValue(StructuredName.DISPLAY_NAME, contact.contact.displayName)
                .build(),
        )

        contact.phones.forEach { phone ->
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, rawContactBackReference)
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, phone.value)
                    .withValue(Phone.TYPE, mapPhoneType(phone.type))
                    .build(),
            )
        }

        contact.emails.forEach { email ->
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, rawContactBackReference)
                    .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                    .withValue(Email.ADDRESS, email.value)
                    .withValue(Email.TYPE, Email.TYPE_HOME)
                    .build(),
            )
        }

        photoStore.readPhoto(contact.contact.id)?.let { bytes ->
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, rawContactBackReference)
                    .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, bytes)
                    .build(),
            )
        }
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
