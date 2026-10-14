package com.nextcloud.contacts.platform

import android.content.Context
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.local.AddressEntity
import com.nextcloud.contacts.data.local.ContactCategories
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.ContactFieldOptions
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.util.formatContactDateLabel
import com.nextcloud.contacts.util.parseContactDateForCountdown

/** Plain-text contact summary for calendar event descriptions (not vCard). */
object ContactPlainTextFormatter {

    fun formatForCalendar(
        context: Context,
        details: ContactWithDetails,
        eventKind: ContactCalendarEventKind,
    ): String {
        val sb = StringBuilder()
        appendNameSection(context, sb, details.contact)
        details.phones.filter { it.value.isNotBlank() }.forEach { phone ->
            appendTypedLine(
                sb,
                ContactFieldOptions.label(context, ContactFieldOptions.phones, phone.type),
                phone.value,
            )
        }
        details.emails.filter { it.value.isNotBlank() }.forEach { email ->
            appendTypedLine(
                sb,
                ContactFieldOptions.label(context, ContactFieldOptions.emails, email.type),
                email.value,
            )
        }
        details.addresses.forEach { address ->
            appendAddress(context, sb, address)
        }
        appendFieldLine(sb, context.getString(R.string.field_organization), details.contact.organization)
        appendFieldLine(sb, context.getString(R.string.field_job_title), details.contact.title)
        appendContactDateLine(sb, context.getString(R.string.field_birthday), details.contact.birthday)
        if (eventKind != ContactCalendarEventKind.BIRTHDAY) {
            appendFieldLine(sb, context.getString(R.string.field_place_of_birth), details.contact.birthplace)
        }
        appendContactDateLine(sb, context.getString(R.string.field_anniversary), details.contact.anniversary)
        appendContactDateLine(sb, context.getString(R.string.field_date_of_death), details.contact.deathDate)
        if (eventKind != ContactCalendarEventKind.DEATH) {
            appendFieldLine(sb, context.getString(R.string.field_place_of_death), details.contact.deathPlace)
        }
        val groups = ContactCategories.parse(details.contact.categories)
        if (groups.isNotEmpty()) {
            appendFieldLine(sb, context.getString(R.string.field_groups), ContactCategories.join(groups))
        }
        details.imAccounts.filter { it.value.isNotBlank() }.forEach { im ->
            appendTypedLine(sb, MessengerImMapping.typeLabel(context, im.type), im.value)
        }
        details.socialProfiles.filter { it.value.isNotBlank() }.forEach { profile ->
            appendTypedLine(
                sb,
                ContactFieldOptions.label(context, ContactFieldOptions.social, profile.type),
                profile.value,
            )
        }
        details.urls.filter { it.value.isNotBlank() }.forEach { url ->
            appendTypedLine(sb, ContactFieldOptions.urlLabel(context, url.type), url.value)
        }
        appendFieldLine(sb, context.getString(R.string.field_note), details.contact.notes)
        return sb.toString().trimEnd()
    }

    private fun appendNameSection(context: Context, sb: StringBuilder, contact: ContactEntity) {
        var wrotePart = false
        wrotePart = appendFieldLine(sb, context.getString(R.string.field_prefix), contact.prefix) || wrotePart
        wrotePart = appendFieldLine(sb, context.getString(R.string.field_given_name), contact.givenName) || wrotePart
        wrotePart = appendFieldLine(sb, context.getString(R.string.field_middle_name), contact.middleName) || wrotePart
        wrotePart = appendFieldLine(sb, context.getString(R.string.field_family_name), contact.familyName) || wrotePart
        wrotePart = appendFieldLine(sb, context.getString(R.string.field_suffix), contact.suffix) || wrotePart
        wrotePart = appendFieldLine(sb, context.getString(R.string.field_nickname), contact.nickname) || wrotePart
        wrotePart = appendFieldLine(sb, context.getString(R.string.field_phonetic_given), contact.phoneticGiven) || wrotePart
        wrotePart = appendFieldLine(sb, context.getString(R.string.field_phonetic_family), contact.phoneticFamily) || wrotePart
        if (!wrotePart) {
            appendFieldLine(sb, context.getString(R.string.field_name), contact.displayName)
        }
    }

    private fun appendAddress(context: Context, sb: StringBuilder, address: AddressEntity) {
        val formatted = listOf(
            address.street,
            address.extendedAddress,
            listOf(address.city, address.region, address.postalCode)
                .filter { it.isNotBlank() }
                .joinToString(", "),
            address.country,
            address.poBox,
        )
            .filter { it.isNotBlank() }
            .joinToString("\n")
        if (formatted.isBlank()) return
        val typeLabel = ContactFieldOptions.label(context, ContactFieldOptions.addresses, address.type)
        sb.append(typeLabel).append(":\n").append(formatted).append('\n')
    }

    private fun appendContactDateLine(sb: StringBuilder, label: String, raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val parsed = parseContactDateForCountdown(trimmed) ?: return
        appendFieldLine(sb, label, formatContactDateLabel(parsed))
    }

    private fun appendFieldLine(sb: StringBuilder, label: String, value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        sb.append(label).append(": ").append(trimmed).append('\n')
        return true
    }

    private fun appendTypedLine(sb: StringBuilder, typeLabel: String, value: String) {
        appendFieldLine(sb, typeLabel, value)
    }
}
