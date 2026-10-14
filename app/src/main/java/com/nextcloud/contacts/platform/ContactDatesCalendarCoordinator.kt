package com.nextcloud.contacts.platform

import android.util.Log
import com.nextcloud.contacts.data.prefs.CalendarEventDescriptionMode
import com.nextcloud.contacts.data.prefs.ContactDatesDestination
import com.nextcloud.contacts.data.prefs.UserPreferences
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

class ContactDatesCalendarCoordinator(
    private val systemSync: BirthdayCalendarSync,
    private val nextcloudSync: NextcloudContactDatesCalendarSync,
    private val userPreferences: UserPreferences,
) {
    private val _nextcloudCalendarFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fired when the Nextcloud calendar leg fails and the system calendar was still updated. */
    val nextcloudCalendarFailures: SharedFlow<Unit> = _nextcloudCalendarFailures.asSharedFlow()

    suspend fun syncAll(): Boolean {
        systemSync.syncAll()
        return nextcloudLegFailed { nextcloudSync.syncAll() }
    }

    suspend fun syncContact(appContactId: Long): Boolean {
        systemSync.syncContact(appContactId)
        return nextcloudLegFailed { nextcloudSync.syncContact(appContactId) }
    }

    suspend fun removeContactEvent(appContactId: Long): Boolean {
        systemSync.removeContactEvent(appContactId)
        return nextcloudLegFailed { nextcloudSync.removeContactEvent(appContactId) }
    }

    suspend fun setEventDescriptionMode(mode: CalendarEventDescriptionMode): Boolean {
        val previous = userPreferences.calendarEventDescriptionMode.first()
        if (previous == mode) return false
        userPreferences.setCalendarEventDescriptionMode(mode)
        return syncAll()
    }

    suspend fun setKindDestination(kind: ContactCalendarEventKind, destination: ContactDatesDestination): Boolean {
        val nextcloudAvailable = userPreferences.getCachedNextcloudCalendarAvailable()
        val previousStored = userPreferences.storedDestinationForKind(kind).first()
        val previousEffective = ContactDatesDestination.resolveForBehavior(previousStored, nextcloudAvailable)
        val changed = previousStored != destination
        if (changed) {
            userPreferences.setDestinationForKind(kind, destination)
        }
        val stored = if (changed) destination else previousStored
        val failed = applyKindDestination(kind, stored, previousStored, previousEffective, nextcloudAvailable)
        systemSync.refreshCalendarPresence()
        return failed
    }

    private suspend fun applyKindDestination(
        kind: ContactCalendarEventKind,
        stored: ContactDatesDestination?,
        previousStored: ContactDatesDestination?,
        previousEffective: ContactDatesDestination,
        nextcloudAvailable: Boolean?,
    ): Boolean {
        val wantsSystem = ContactDatesDestination.includesSystemForSync(stored, nextcloudAvailable)
        if (wantsSystem) {
            systemSync.enableKind(kind)
        } else if (previousEffective.includesSystem()) {
            systemSync.disableKind(kind)
        }

        val hadNextcloud = ContactDatesDestination.includesNextcloudForSync(previousStored, nextcloudAvailable)
        val wantsNextcloud = ContactDatesDestination.includesNextcloudForSync(stored, nextcloudAvailable)
        return if (wantsNextcloud) {
            nextcloudLegFailed { nextcloudSync.enableKind(kind) }
        } else if (hadNextcloud) {
            nextcloudLegFailed { nextcloudSync.disableKind(kind) }
        } else {
            false
        }
    }

    private suspend fun nextcloudLegFailed(block: suspend () -> Unit): Boolean {
        try {
            block()
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Nextcloud calendar update failed", e)
            _nextcloudCalendarFailures.tryEmit(Unit)
            return true
        }
    }

    private companion object {
        const val TAG = "ContactDates"
    }
}
