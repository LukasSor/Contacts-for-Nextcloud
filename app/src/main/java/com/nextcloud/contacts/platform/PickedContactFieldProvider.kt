package com.nextcloud.contacts.platform

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract
import com.nextcloud.contacts.data.local.AddressEntity
import com.nextcloud.contacts.data.local.ContactWithDetails
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * CoMaps queries the ACTION_PICK result for StructuredPostal.FORMATTED_ADDRESS (column data1).
 * This serves only that one chosen field. It is not the system contacts provider.
 */
class PickedContactFieldProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    companion object {
        const val AUTHORITY = "com.nextcloud.contacts.pickedfield"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/field")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val stored = PickedContactField.peek(uri)
        val columns = projection?.takeIf { it.isNotEmpty() }?.toList()
            ?: listOf(ContactsContract.Data.DATA1, ContactsContract.Contacts.DISPLAY_NAME)
        val cursor = MatrixCursor(columns.toTypedArray())
        if (stored != null) {
            cursor.addRow(columns.map { column -> cell(column, stored) })
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/contact"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun cell(column: String, stored: PickedContactField.Value): Any {
        return when (column.lowercase()) {
            "_id", "contact_id" -> 1L
            "data1",
            "data",
            "formatted_address",
            "number",
            "address",
            "email",
            -> stored.text
            "display_name", "display_name_alt", "display_name_primary" -> stored.displayName
            else -> if (column.endsWith("_id", ignoreCase = true)) 0L else ""
        }
    }
}

object PickedContactField {
    data class Value(val token: String, val text: String, val displayName: String, val storedAt: Long)

    private val current = AtomicReference<Value?>(null)

    fun store(text: String, displayName: String): Uri {
        val token = UUID.randomUUID().toString()
        current.set(
            Value(
                token = token,
                text = text,
                displayName = displayName,
                storedAt = SystemClock.elapsedRealtime(),
            ),
        )
        return PickedContactFieldProvider.CONTENT_URI.buildUpon().appendPath(token).build()
    }

    fun peek(uri: Uri): Value? {
        val token = uri.pathSegments.lastOrNull() ?: return null
        val value = current.get() ?: return null
        if (value.token != token) return null
        if (SystemClock.elapsedRealtime() - value.storedAt > TTL_MS) {
            current.compareAndSet(value, null)
            return null
        }
        return value
    }

    private const val TTL_MS = 60_000L
}

fun pickedFieldText(contact: ContactWithDetails, kind: ContactPickKind): String? = when (kind) {
    ContactPickKind.CONTACT -> null
    ContactPickKind.POSTAL_ADDRESS -> contact.addresses.firstNotNullOfOrNull { address ->
        shareableAddress(address).takeIf { it.isNotBlank() }
    }
    ContactPickKind.PHONE -> contact.phones.firstOrNull { it.value.isNotBlank() }?.value?.trim()
    ContactPickKind.EMAIL -> contact.emails.firstOrNull { it.value.isNotBlank() }?.value?.trim()
}

internal fun shareableAddress(address: AddressEntity): String {
    val parts = listOf(
        address.street,
        address.extendedAddress,
        address.city,
        address.region,
        address.postalCode,
        address.country,
        address.poBox,
    ).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isNotEmpty()) return parts.joinToString(", ")
    val raw = address.vcardRawValue.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    if (raw.isNotEmpty()) return raw.joinToString(", ")
    return address.geo.trim().replace(';', ',')
}
