package com.nextcloud.contacts.data

import com.nextcloud.contacts.data.local.ContactInput
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.local.FingerprintContactRow

object ContactFingerprint {
    fun from(input: ContactInput): String = build(
        name = input.displayName,
        phones = input.phones.map { normalizePhone(it.value) },
        emails = input.emails.map { it.value.lowercase().trim() },
    )

    fun from(contact: ContactWithDetails): String = build(
        name = contact.contact.displayName,
        phones = contact.phones.map { normalizePhone(it.value) },
        emails = contact.emails.map { it.value.lowercase().trim() },
    )

    fun from(
        displayName: String,
        phones: List<String>,
        emails: List<String>,
    ): String = build(
        name = displayName,
        phones = phones.map { normalizePhone(it) },
        emails = emails.map { it.lowercase().trim() },
    )

    private fun build(name: String, phones: List<String>, emails: List<String>): String {
        val phoneKey = phones.filter { it.isNotBlank() }.sorted().joinToString("|")
        val emailKey = emails.filter { it.isNotBlank() }.sorted().joinToString("|")
        val nameKey = name.lowercase().trim()
        return "$nameKey::$phoneKey::$emailKey"
    }

    private fun normalizePhone(value: String): String =
        value.filter { it.isDigit() || it == '+' }
}

/**
 * Name, phones, and emails only — the same key as [ContactFingerprint].
 * The lowest contact id wins when two rows share a key.
 */
class FingerprintIndex private constructor(
    private val idByFingerprint: MutableMap<String, Long>,
    private val fingerprintById: MutableMap<Long, String>,
) {
    fun find(details: ContactWithDetails): Long? = find(
        displayName = details.contact.displayName,
        phones = details.phones.map { it.value },
        emails = details.emails.map { it.value },
    )

    fun find(displayName: String, phones: List<String>, emails: List<String>): Long? {
        val key = ContactFingerprint.from(displayName, phones, emails)
        if (key == "::") return null
        return idByFingerprint[key]
    }

    fun remove(id: Long) {
        val key = fingerprintById.remove(id) ?: return
        if (idByFingerprint[key] == id) idByFingerprint.remove(key)
    }

    fun put(id: Long, displayName: String, phones: List<String>, emails: List<String>) {
        remove(id)
        val key = ContactFingerprint.from(displayName, phones, emails)
        if (key == "::") return
        fingerprintById[id] = key
        idByFingerprint.putIfAbsent(key, id)
    }

    fun putContact(details: ContactWithDetails) {
        put(
            id = details.contact.id,
            displayName = details.contact.displayName,
            phones = details.phones.map { it.value },
            emails = details.emails.map { it.value },
        )
    }

    companion object {
        fun build(
            contacts: List<FingerprintContactRow>,
            phonesByContact: Map<Long, List<String>>,
            emailsByContact: Map<Long, List<String>>,
        ): FingerprintIndex {
            val index = FingerprintIndex(linkedMapOf(), mutableMapOf())
            for (contact in contacts) {
                index.put(
                    id = contact.id,
                    displayName = contact.displayName,
                    phones = phonesByContact[contact.id].orEmpty(),
                    emails = emailsByContact[contact.id].orEmpty(),
                )
            }
            return index
        }
    }
}
