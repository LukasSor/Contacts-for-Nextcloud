package com.nextcloud.contacts.platform

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import com.nextcloud.contacts.data.local.ContactDao
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.prefs.CalendarEventDescriptionMode
import com.nextcloud.contacts.data.prefs.ContactDatesDestination
import com.nextcloud.contacts.data.prefs.UserPreferences
import com.nextcloud.contacts.util.ParsedContactDate
import com.nextcloud.contacts.util.contactDateInYear
import com.nextcloud.contacts.util.parseContactDateForCountdown
import kotlinx.coroutines.flow.first
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class BirthdayCalendarSync(
    private val context: Context,
    private val contactDao: ContactDao,
    private val links: BirthdayCalendarEventLinks,
    private val userPreferences: UserPreferences,
) {
    private val resolver = context.contentResolver

    suspend fun enableKind(kind: ContactCalendarEventKind) {
        requireCalendarPermissions()
        val calendarId = ensureCalendar() ?: return
        syncAllOfKind(kind, calendarId, userPreferences.calendarEventDescriptionMode.first())
    }

    suspend fun disableKind(kind: ContactCalendarEventKind) {
        clearKindEvents(kind)
        deleteCalendarIfNoKindsEnabled()
    }

    suspend fun refreshCalendarPresence() {
        deleteCalendarIfNoKindsEnabled()
    }

    suspend fun syncContact(appContactId: Long) {
        if (!shouldUseSystemCalendar()) return
        if (!hasAnyKindEnabled() || !hasCalendarPermissions()) return
        val details = contactDao.getById(appContactId) ?: run {
            removeContactEvent(appContactId)
            return
        }
        if (details.contact.deleted) {
            removeContactEvent(appContactId)
            return
        }
        ensureCalendar()
        val descriptionMode = userPreferences.calendarEventDescriptionMode.first()
        ContactCalendarEventKind.entries.forEach { kind ->
            if (isKindEnabled(kind)) {
                upsertContactEvent(details, kind, descriptionMode = descriptionMode)
            } else {
                removeContactEventKind(appContactId, kind)
            }
        }
    }

    suspend fun removeContactEvent(appContactId: Long) {
        ContactCalendarEventKind.entries.forEach { kind ->
            removeContactEventKind(appContactId, kind)
        }
    }

    suspend fun syncAll() {
        if (!shouldUseSystemCalendar()) {
            removeSystemCalendarAndLinks()
            return
        }
        if (!hasAnyKindEnabled()) {
            removeSystemCalendarAndLinks()
            return
        }
        if (!hasCalendarPermissions()) return
        val calendarId = ensureCalendar() ?: return
        val descriptionMode = userPreferences.calendarEventDescriptionMode.first()
        ContactCalendarEventKind.entries.forEach { kind ->
            if (isKindEnabled(kind)) {
                syncAllOfKind(kind, calendarId, descriptionMode)
            } else {
                clearKindEvents(kind)
            }
        }
    }

    private suspend fun syncAllOfKind(
        kind: ContactCalendarEventKind,
        calendarId: Long,
        descriptionMode: CalendarEventDescriptionMode,
    ) {
        val activeIds = mutableSetOf<Long>()
        contactDao.getAllWithDetails().forEach { details ->
            val contact = details.contact
            if (contact.deleted) return@forEach
            val raw = ContactCalendarEventFormatting.rawDateForKind(contact, kind)
            if (raw.isBlank()) return@forEach
            if (parseContactDateForCountdown(raw) == null) return@forEach
            activeIds.add(contact.id)
            upsertContactEvent(details, kind, calendarId, descriptionMode)
        }
        links.allAppContactIds(kind).forEach { appId ->
            if (appId !in activeIds) {
                removeContactEventKind(appId, kind)
            }
        }
    }

    private suspend fun upsertContactEvent(
        details: ContactWithDetails,
        kind: ContactCalendarEventKind,
        calendarId: Long? = null,
        descriptionMode: CalendarEventDescriptionMode,
    ) {
        val contact = details.contact
        val resolvedCalendarId = calendarId ?: ensureCalendar() ?: return
        val rawDate = ContactCalendarEventFormatting.rawDateForKind(contact, kind)
        val parsed = parseContactDateForCountdown(rawDate) ?: run {
            removeContactEventKind(contact.id, kind)
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
        val existingId = links.getEventId(contact.id, kind)
        if (existingId != null &&
            links.getFingerprint(contact.id, kind) == fingerprint &&
            eventExists(existingId, resolvedCalendarId)
        ) {
            return
        }

        val values = eventValues(
            details,
            parsed,
            resolvedCalendarId,
            title,
            kind,
            descriptionMode,
        )
        if (existingId != null && eventExists(existingId, resolvedCalendarId)) {
            val updated = resolver.update(
                ContentUris.withAppendedId(Events.CONTENT_URI, existingId),
                values,
                null,
                null,
            )
            if (updated > 0) {
                links.setLink(contact.id, kind, existingId, fingerprint)
                return
            }
        }

        val uri = resolver.insert(Events.CONTENT_URI, values) ?: return
        val eventId = ContentUris.parseId(uri)
        links.setLink(contact.id, kind, eventId, fingerprint)
    }

    private fun eventValues(
        details: ContactWithDetails,
        parsed: ParsedContactDate,
        calendarId: Long,
        title: String,
        kind: ContactCalendarEventKind,
        descriptionMode: CalendarEventDescriptionMode,
    ): ContentValues {
        val contact = details.contact
        val anchorYear = parsed.year ?: 2000
        val date = contactDateInYear(anchorYear, parsed.month, parsed.day)
        val startMillis = date.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
        val endMillis = startMillis + TimeUnit.DAYS.toMillis(1)
        val description = ContactCalendarEventFormatting.eventDescription(
            context,
            parsed,
            kind,
            details,
            descriptionMode,
        )

        return ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, title)
            put(Events.DESCRIPTION, description)
            put(Events.DTSTART, startMillis)
            put(Events.DTEND, endMillis)
            put(Events.ALL_DAY, 1)
            put(Events.EVENT_TIMEZONE, TimeZone.getTimeZone("UTC").id)
            put(Events.RRULE, "FREQ=YEARLY")
            put(Events.HAS_ALARM, 0)
            put(Events.CUSTOM_APP_PACKAGE, context.packageName)
            put(Events.CUSTOM_APP_URI, "contact:${contact.id}:${kind.storageKey}")
        }
    }

    private fun eventExists(eventId: Long, calendarId: Long): Boolean {
        resolver.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID, Events.CALENDAR_ID),
            "${Events._ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return false
            val eventCalendarId = cursor.getLong(1)
            return eventCalendarId == calendarId
        }
        return false
    }

    private suspend fun ensureCalendar(): Long? {
        if (!hasCalendarPermissions()) return null
        findExistingCalendarId()?.let { return it }
        return createCalendar()
    }

    private fun findExistingCalendarId(): Long? =
        findCalendarIdByAccount(ACCOUNT_TYPE, ACCOUNT_NAME)

    private fun findCalendarIdByAccount(accountType: String, accountName: String): Long? {
        resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.ACCOUNT_NAME} = ?",
            arrayOf(accountType, accountName),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }

    private fun createCalendar(): Long? {
        val uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .build()

        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(Calendars.NAME, CALENDAR_DISPLAY_NAME)
            put(Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
        }

        val inserted = resolver.insert(uri, values) ?: return null
        return ContentUris.parseId(inserted)
    }

    private suspend fun clearKindEvents(kind: ContactCalendarEventKind) {
        if (!hasCalendarPermissions()) {
            links.clearKind(kind)
            return
        }
        links.allAppContactIds(kind).forEach { appId ->
            removeContactEventKind(appId, kind)
        }
        links.clearKind(kind)
    }

    private suspend fun removeContactEventKind(appContactId: Long, kind: ContactCalendarEventKind) {
        val eventId = links.getEventId(appContactId, kind) ?: return
        if (hasCalendarPermissions()) {
            runCatching {
                resolver.delete(
                    ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                    null,
                    null,
                )
            }
        }
        links.removeAppContact(appContactId, kind)
    }

    private suspend fun deleteCalendarIfNoKindsEnabled() {
        if (!shouldUseSystemCalendar()) {
            removeSystemCalendarAndLinks()
            return
        }
        if (hasAnyKindEnabled()) return
        removeSystemCalendarAndLinks()
    }

    private suspend fun removeSystemCalendarAndLinks() {
        deleteContactsCalendar()
        links.clear()
    }

    private suspend fun shouldUseSystemCalendar(): Boolean {
        val nextcloudAvailable = userPreferences.getCachedNextcloudCalendarAvailable()
        return ContactCalendarEventKind.entries.any { kind ->
            val stored = userPreferences.storedDestinationForKind(kind).first()
            ContactDatesDestination.includesSystemForSync(stored, nextcloudAvailable)
        }
    }

    private fun deleteContactsCalendar() {
        if (!hasCalendarPermissions()) return
        val calendarId = findExistingCalendarId() ?: return
        val deleteUri = ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId)
            .buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()
        runCatching { resolver.delete(deleteUri, null, null) }
    }

    private suspend fun hasAnyKindEnabled(): Boolean =
        isKindEnabled(ContactCalendarEventKind.BIRTHDAY) ||
            isKindEnabled(ContactCalendarEventKind.ANNIVERSARY) ||
            isKindEnabled(ContactCalendarEventKind.DEATH)

    private suspend fun isKindEnabled(kind: ContactCalendarEventKind): Boolean {
        val stored = userPreferences.storedDestinationForKind(kind).first()
        val nextcloudAvailable = userPreferences.getCachedNextcloudCalendarAvailable()
        return ContactDatesDestination.includesSystemForSync(stored, nextcloudAvailable)
    }

    private fun hasCalendarPermissions(): Boolean =
        CalendarPermissionRequester.hasCalendarPermissions(context)

    private fun requireCalendarPermissions() {
        if (!hasCalendarPermissions()) {
            throw SecurityException("Calendar read and write permissions required")
        }
    }

    companion object {
        const val ACCOUNT_TYPE = "com.nextcloud.contacts"
        const val ACCOUNT_NAME = "Nextcloud Contacts"
        const val CALENDAR_DISPLAY_NAME = "Nextcloud Contacts"
        private const val CALENDAR_COLOR = 0xFF0082C9.toInt()
    }
}
