package com.nextcloud.contacts.data.device

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import com.nextcloud.contacts.data.local.ContactInput

class DeviceContactsReader(private val contentResolver: ContentResolver) {

    fun readAll(): List<ContactInput> {
        val builders = linkedMapOf<Long, Builder>()

        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            "${ContactsContract.Contacts.IN_VISIBLE_GROUP} = 1",
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                builders[id] = Builder(cursor.getString(nameIdx).orEmpty())
            }
        } ?: return emptyList()

        if (builders.isEmpty()) return emptyList()

        val mimeTypes = arrayOf(
            StructuredName.CONTENT_ITEM_TYPE,
            Phone.CONTENT_ITEM_TYPE,
            Email.CONTENT_ITEM_TYPE,
            Organization.CONTENT_ITEM_TYPE,
            Note.CONTENT_ITEM_TYPE,
            Photo.CONTENT_ITEM_TYPE,
        )
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            null,
            "${ContactsContract.Data.MIMETYPE} IN (?,?,?,?,?,?)",
            mimeTypes,
            ContactsContract.Data.CONTACT_ID,
        )?.use { cursor ->
            val contactIdIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val mimeIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(contactIdIdx)
                val builder = builders[contactId] ?: continue
                when (cursor.getString(mimeIdx)) {
                    StructuredName.CONTENT_ITEM_TYPE -> builder.readName(cursor)
                    Phone.CONTENT_ITEM_TYPE -> builder.readPhone(cursor)
                    Email.CONTENT_ITEM_TYPE -> builder.readEmail(cursor)
                    Organization.CONTENT_ITEM_TYPE -> builder.readOrganization(cursor)
                    Note.CONTENT_ITEM_TYPE -> builder.readNote(cursor)
                    Photo.CONTENT_ITEM_TYPE -> builder.readPhotoBlob(cursor)
                }
            }
        }

        builders.keys.forEach { contactId ->
            if (builders[contactId]?.photoBytes != null) return@forEach
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, uri, true)?.use { stream ->
                builders[contactId]?.photoBytes = stream.readBytes()
            }
        }

        return builders.values.mapNotNull { it.toContactInput() }
    }

    private class Builder(private val displayName: String) {
        var givenName = ""
        var familyName = ""
        var organization = ""
        var notes = ""
        var photoBytes: ByteArray? = null
        val phones = mutableListOf<Pair<String, String>>()
        val emails = mutableListOf<Pair<String, String>>()

        fun readName(cursor: Cursor) {
            givenName = cursor.getString(cursor.getColumnIndexOrThrow(StructuredName.GIVEN_NAME)).orEmpty()
            familyName = cursor.getString(cursor.getColumnIndexOrThrow(StructuredName.FAMILY_NAME)).orEmpty()
        }

        fun readPhone(cursor: Cursor) {
            val number = cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER)).orEmpty().trim()
            if (number.isBlank()) return
            val type = mapPhoneType(cursor.getInt(cursor.getColumnIndexOrThrow(Phone.TYPE)))
            phones += type to number
        }

        fun readEmail(cursor: Cursor) {
            val address = cursor.getString(cursor.getColumnIndexOrThrow(Email.ADDRESS)).orEmpty().trim()
            if (address.isBlank()) return
            val type = mapEmailType(cursor.getInt(cursor.getColumnIndexOrThrow(Email.TYPE)))
            emails += type to address
        }

        fun readOrganization(cursor: Cursor) {
            if (organization.isBlank()) {
                organization = cursor.getString(
                    cursor.getColumnIndexOrThrow(Organization.COMPANY),
                ).orEmpty()
            }
        }

        fun readNote(cursor: Cursor) {
            if (notes.isBlank()) {
                notes = cursor.getString(cursor.getColumnIndexOrThrow(Note.NOTE)).orEmpty()
            }
        }

        fun readPhotoBlob(cursor: Cursor) {
            if (photoBytes != null) return
            val photoIdx = cursor.getColumnIndex(Photo.PHOTO)
            if (photoIdx >= 0 && !cursor.isNull(photoIdx)) {
                photoBytes = cursor.getBlob(photoIdx)
            }
        }

        fun toContactInput(): ContactInput? {
            val given = givenName.ifBlank {
                if (familyName.isBlank()) displayName else ""
            }
            val hasContent = given.isNotBlank() ||
                familyName.isNotBlank() ||
                organization.isNotBlank() ||
                phones.isNotEmpty() ||
                emails.isNotEmpty()
            if (!hasContent) return null

            return ContactInput(
                givenName = given,
                familyName = familyName,
                organization = organization,
                notes = notes,
                phones = phones.distinctBy { normalizePhone(it.second) }
                    .map { (type, value) -> com.nextcloud.contacts.data.local.VCardTypedFieldInput.phone(type, value) },
                emails = emails.distinctBy { it.second.lowercase() }
                    .map { (type, value) -> com.nextcloud.contacts.data.local.VCardTypedFieldInput.email(type, value) },
                photoBytes = photoBytes,
            )
        }
    }

    companion object {
        private fun mapPhoneType(type: Int): String = when (type) {
            Phone.TYPE_MOBILE, Phone.TYPE_WORK_MOBILE -> "mobile"
            Phone.TYPE_HOME -> "home"
            Phone.TYPE_WORK -> "work"
            Phone.TYPE_FAX_WORK, Phone.TYPE_FAX_HOME -> "fax"
            else -> "other"
        }

        private fun mapEmailType(type: Int): String = when (type) {
            Email.TYPE_WORK -> "work"
            Email.TYPE_MOBILE -> "mobile"
            else -> "home"
        }

        private fun normalizePhone(value: String): String =
            value.filter { it.isDigit() || it == '+' }
    }
}
