package com.nextcloud.contacts.platform

import android.content.Context
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.prefs.CalendarEventDescriptionMode
import com.nextcloud.contacts.util.ParsedContactDate

object ContactCalendarEventFormatting {

    fun eventTitle(context: Context, contact: ContactEntity, kind: ContactCalendarEventKind): String {
        val name = contactTitle(context, contact)
        return when (kind) {
            ContactCalendarEventKind.BIRTHDAY -> context.getString(R.string.event_birthday, name)
            ContactCalendarEventKind.ANNIVERSARY -> context.getString(R.string.event_anniversary, name)
            ContactCalendarEventKind.DEATH -> context.getString(R.string.event_death, name)
        }
    }

    fun defaultDescriptionBlock(
        context: Context,
        contact: ContactEntity,
        parsed: ParsedContactDate,
        kind: ContactCalendarEventKind,
    ): String {
        val lines = mutableListOf<String>()
        when (kind) {
            ContactCalendarEventKind.BIRTHDAY -> {
                parsed.year?.let { lines.add(context.getString(R.string.event_born_in, it)) }
                contact.birthplace.trim().takeIf { it.isNotEmpty() }?.let {
                    lines.add(context.getString(R.string.event_place_of_birth, it))
                }
            }
            ContactCalendarEventKind.ANNIVERSARY -> {
                parsed.year?.let { lines.add(context.getString(R.string.event_anniversary_in, it)) }
            }
            ContactCalendarEventKind.DEATH -> {
                parsed.year?.let { lines.add(context.getString(R.string.event_died_in, it)) }
                contact.deathPlace.trim().takeIf { it.isNotEmpty() }?.let {
                    lines.add(context.getString(R.string.event_place_of_death, it))
                }
            }
        }
        return lines.joinToString("\n")
    }

    fun eventDescription(
        context: Context,
        parsed: ParsedContactDate,
        kind: ContactCalendarEventKind,
        details: ContactWithDetails,
        mode: CalendarEventDescriptionMode,
    ): String {
        val defaultBlock = defaultDescriptionBlock(context, details.contact, parsed, kind)
        return when (mode) {
            CalendarEventDescriptionMode.DEFAULT -> defaultBlock
            CalendarEventDescriptionMode.NOTES -> {
                val note = details.contact.notes.trim()
                joinDescriptionParts(defaultBlock, note.takeIf { it.isNotEmpty() })
            }
            CalendarEventDescriptionMode.DETAILS -> {
                val block = ContactPlainTextFormatter.formatForCalendar(context, details, kind)
                joinDescriptionParts(defaultBlock, block.takeIf { it.isNotBlank() })
            }
        }
    }

    fun eventFingerprint(
        context: Context,
        contact: ContactEntity,
        parsed: ParsedContactDate,
        kind: ContactCalendarEventKind,
        details: ContactWithDetails,
        mode: CalendarEventDescriptionMode,
    ): String {
        val title = eventTitle(context, contact, kind)
        val rawDate = rawDateForKind(contact, kind).trim()
        val description = eventDescription(context, parsed, kind, details, mode)
        return "$title|$rawDate|${description.hashCode()}"
    }

    fun rawDateForKind(contact: ContactEntity, kind: ContactCalendarEventKind): String =
        when (kind) {
            ContactCalendarEventKind.BIRTHDAY -> contact.birthday
            ContactCalendarEventKind.ANNIVERSARY -> contact.anniversary
            ContactCalendarEventKind.DEATH -> contact.deathDate
        }

    fun stableEventUid(appContactId: Long, kind: ContactCalendarEventKind): String =
        "nextcloud-contacts-$appContactId-${kind.storageKey}@nextcloud.contacts"

    fun contactTitle(context: Context, contact: ContactEntity): String {
        contact.displayName.trim().takeIf { it.isNotEmpty() }?.let { return it }
        return listOf(contact.givenName, contact.familyName)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifBlank { context.getString(R.string.fallback_contact) }
    }

    private fun joinDescriptionParts(vararg parts: String?): String =
        parts.mapNotNull { part -> part?.trim()?.takeIf { it.isNotEmpty() } }
            .joinToString("\n\n")
}
