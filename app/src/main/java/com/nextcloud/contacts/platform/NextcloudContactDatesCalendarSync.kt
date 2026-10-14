package com.nextcloud.contacts.platform

import android.content.Context
import com.nextcloud.contacts.auth.AccountCredentials
import com.nextcloud.contacts.data.local.ContactDao
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.prefs.CalendarEventDescriptionMode
import com.nextcloud.contacts.data.prefs.ContactDatesDestination
import com.nextcloud.contacts.data.prefs.UserPreferences
import com.nextcloud.contacts.data.remote.CalDavCalendarClient
import com.nextcloud.contacts.data.remote.RemoteUrlNormalizer
import com.nextcloud.contacts.util.ParsedContactDate
import com.nextcloud.contacts.util.contactDateInYear
import com.nextcloud.contacts.util.parseContactDateForCountdown
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class NextcloudContactDatesCalendarSync(
    private val context: Context,
    private val contactDao: ContactDao,
    private val links: NextcloudCalendarEventLinks,
    private val userPreferences: UserPreferences,
    private val credentials: () -> AccountCredentials?,
) {

    suspend fun syncContact(appContactId: Long) {
        if (!includesNextcloudDestination()) return
        val details = contactDao.getById(appContactId) ?: run {
            removeContactEvent(appContactId)
            return
        }
        if (details.contact.deleted) {
            removeContactEvent(appContactId)
            return
        }
        val client = createClient() ?: return
        val collectionUrl = ensureCollection(client) ?: return
        val descriptionMode = userPreferences.calendarEventDescriptionMode.first()
        ContactCalendarEventKind.entries.forEach { kind ->
            if (isKindEnabled(kind)) {
                upsertContactEvent(client, collectionUrl, details, kind, descriptionMode)
            } else {
                removeContactEventKind(client, appContactId, kind)
            }
        }
    }

    suspend fun removeContactEvent(appContactId: Long) {
        val client = createClient()
        ContactCalendarEventKind.entries.forEach { kind ->
            removeContactEventKind(client, appContactId, kind)
        }
    }

    suspend fun syncAll() {
        if (!includesNextcloudDestination()) {
            clearNextcloudEventsIfExcluded()
            return
        }
        if (!hasAnyKindEnabled()) {
            clearAllManagedEvents(createClient())
            return
        }
        val client = createClient() ?: return
        val collectionUrl = ensureCollection(client) ?: return
        val descriptionMode = userPreferences.calendarEventDescriptionMode.first()
        ContactCalendarEventKind.entries.forEach { kind ->
            if (isKindEnabled(kind)) {
                syncAllOfKind(client, collectionUrl, kind, descriptionMode)
            } else {
                clearKindEvents(client, kind)
            }
        }
    }

    suspend fun enableKind(kind: ContactCalendarEventKind) {
        if (!includesNextcloudDestination()) return
        val client = createClient() ?: return
        val collectionUrl = ensureCollection(client) ?: return
        syncAllOfKind(
            client,
            collectionUrl,
            kind,
            userPreferences.calendarEventDescriptionMode.first(),
        )
    }

    suspend fun disableKind(kind: ContactCalendarEventKind) {
        clearKindEvents(createClient(), kind)
    }

    private suspend fun clearNextcloudEventsIfExcluded() {
        clearAllManagedEvents(createClient())
    }

    private suspend fun includesNextcloudDestination(): Boolean {
        val nextcloudAvailable = userPreferences.getCachedNextcloudCalendarAvailable()
        return ContactCalendarEventKind.entries.any { kind ->
            val stored = userPreferences.storedDestinationForKind(kind).first()
            ContactDatesDestination.includesNextcloudForSync(stored, nextcloudAvailable)
        }
    }

    private suspend fun syncAllOfKind(
        client: CalDavCalendarClient,
        collectionUrl: String,
        kind: ContactCalendarEventKind,
        descriptionMode: CalendarEventDescriptionMode,
    ) {
        val activeIds = mutableSetOf<Long>()
        contactDao.getAllWithDetails().forEach { details ->
            val contact = details.contact
            if (contact.deleted) return@forEach
            val rawDate = ContactCalendarEventFormatting.rawDateForKind(contact, kind)
            if (rawDate.isBlank()) return@forEach
            if (parseContactDateForCountdown(rawDate) == null) return@forEach
            activeIds.add(contact.id)
            upsertContactEvent(client, collectionUrl, details, kind, descriptionMode)
        }
        links.allAppContactIds(kind).forEach { appId ->
            if (appId !in activeIds) {
                removeContactEventKind(client, appId, kind)
            }
        }
    }

    private suspend fun upsertContactEvent(
        client: CalDavCalendarClient,
        collectionUrl: String,
        details: ContactWithDetails,
        kind: ContactCalendarEventKind,
        descriptionMode: CalendarEventDescriptionMode,
    ) {
        val contact = details.contact
        val rawDate = ContactCalendarEventFormatting.rawDateForKind(contact, kind)
        val parsed = parseContactDateForCountdown(rawDate) ?: run {
            removeContactEventKind(client, contact.id, kind)
            return
        }
        val title = ContactCalendarEventFormatting.eventTitle(context, contact, kind)
        val fingerprint = ContactCalendarEventFormatting.eventFingerprint(
            context,
            contact,
            parsed,
            kind,
            details,
            descriptionMode,
        )
        val uid = ContactCalendarEventFormatting.stableEventUid(contact.id, kind)
        val existingHref = links.getEventHref(contact.id, kind)
        if (existingHref != null && links.getFingerprint(contact.id, kind) == fingerprint) {
            return
        }

        val ical = buildVEvent(uid, title, parsed, kind, details, descriptionMode)
        val eventUrl = existingHref ?: client.eventUrl(collectionUrl, uid)
        runCatching {
            client.putEvent(eventUrl, ical, etag = null)
            links.setLink(contact.id, kind, eventUrl, fingerprint)
        }
    }

    private suspend fun removeContactEventKind(
        client: CalDavCalendarClient?,
        appContactId: Long,
        kind: ContactCalendarEventKind,
    ) {
        val href = links.getEventHref(appContactId, kind) ?: return
        if (client != null) {
            runCatching { client.deleteResource(href, etag = null) }
        }
        links.removeAppContact(appContactId, kind)
    }

    private suspend fun clearKindEvents(client: CalDavCalendarClient?, kind: ContactCalendarEventKind) {
        links.allAppContactIds(kind).forEach { appId ->
            removeContactEventKind(client, appId, kind)
        }
        links.clearKind(kind)
    }

    private suspend fun clearAllManagedEvents(client: CalDavCalendarClient?) {
        ContactCalendarEventKind.entries.forEach { kind ->
            clearKindEvents(client, kind)
        }
        val collectionHref = links.getCollectionHref()
        if (client != null && collectionHref != null) {
            purgeRemainingManagedEvents(client, collectionHref)
            maybeDeleteEmptyCollection(client, collectionHref)
        }
        links.clearEventLinks()
        links.clearCollectionMetadata()
    }

    private fun purgeRemainingManagedEvents(client: CalDavCalendarClient, collectionUrl: String) {
        runCatching {
            client.listEventResources(collectionUrl).forEach { resource ->
                val uid = resource.calendarData?.let(::extractUid) ?: return@forEach
                if (isManagedUid(uid)) {
                    runCatching { client.deleteResource(resource.href, resource.etag) }
                }
            }
        }
    }

    private suspend fun maybeDeleteEmptyCollection(client: CalDavCalendarClient, collectionUrl: String) {
        if (!links.getWeCreatedCollection()) return
        val remaining = runCatching { client.listEventResources(collectionUrl) }.getOrDefault(emptyList())
        if (remaining.isNotEmpty()) return
        runCatching { client.deleteCollection(collectionUrl) }
        links.clearCollectionMetadata()
    }

    private suspend fun ensureCollection(client: CalDavCalendarClient): String? {
        links.getCollectionHref()?.takeIf { it.isNotBlank() }?.let { cached ->
            return cached
        }
        val homeUrl = client.calendarHomeUrl()
        val existing = client.findCalendarCollection(homeUrl, CALENDAR_SLUG)
        if (existing != null) {
            links.setCollectionHref(existing.href)
            return existing.href
        }
        return runCatching {
            val created = client.createCalendarCollection(homeUrl, CALENDAR_SLUG, CALENDAR_DISPLAY_NAME)
            links.setCollectionHref(created)
            links.setWeCreatedCollection(true)
            created
        }.getOrNull()
    }

    private fun createClient(): CalDavCalendarClient? {
        val account = credentials() ?: return null
        return CalDavCalendarClient(account)
    }

    private suspend fun hasAnyKindEnabled(): Boolean =
        isKindEnabled(ContactCalendarEventKind.BIRTHDAY) ||
            isKindEnabled(ContactCalendarEventKind.ANNIVERSARY) ||
            isKindEnabled(ContactCalendarEventKind.DEATH)

    private suspend fun isKindEnabled(kind: ContactCalendarEventKind): Boolean {
        val stored = userPreferences.storedDestinationForKind(kind).first()
        val nextcloudAvailable = userPreferences.getCachedNextcloudCalendarAvailable()
        return ContactDatesDestination.includesNextcloudForSync(stored, nextcloudAvailable)
    }

    private fun buildVEvent(
        uid: String,
        summary: String,
        parsed: ParsedContactDate,
        kind: ContactCalendarEventKind,
        details: ContactWithDetails,
        descriptionMode: CalendarEventDescriptionMode,
    ): String {
        val anchorYear = parsed.year ?: 2000
        val startDate = contactDateInYear(anchorYear, parsed.month, parsed.day)
        val endDate = startDate.plusDays(1)
        val description = ContactCalendarEventFormatting.eventDescription(
            context,
            parsed,
            kind,
            details,
            descriptionMode,
        )
        val dtStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .format(Instant.now().atZone(ZoneOffset.UTC))
        val start = formatIcalDate(startDate)
        val end = formatIcalDate(endDate)

        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//Nextcloud Contacts//EN")
            appendLine("CALSCALE:GREGORIAN")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:$uid")
            appendLine("DTSTAMP:$dtStamp")
            appendLine("SUMMARY:${escapeIcalText(summary)}")
            if (description.isNotBlank()) {
                appendLine("DESCRIPTION:${escapeIcalText(description)}")
            }
            appendLine("DTSTART;VALUE=DATE:$start")
            appendLine("DTEND;VALUE=DATE:$end")
            appendLine("RRULE:FREQ=YEARLY")
            appendLine("TRANSP:TRANSPARENT")
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }.trimEnd() + "\r\n"
    }

    private fun formatIcalDate(date: LocalDate): String =
        DateTimeFormatter.BASIC_ISO_DATE.format(date)

    private fun escapeIcalText(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace(",", "\\,")
            .replace(";", "\\;")

    private fun extractUid(calendarData: String): String? {
        val line = calendarData.lineSequence()
            .firstOrNull { it.startsWith("UID:", ignoreCase = true) }
            ?: return null
        return line.substringAfter(':').trim()
    }

    private fun isManagedUid(uid: String): Boolean =
        uid.startsWith("nextcloud-contacts-") && uid.endsWith("@nextcloud.contacts")

    companion object {
        const val CALENDAR_SLUG = "contacts-dates"
        const val CALENDAR_DISPLAY_NAME = "Nextcloud Contacts"
    }
}
