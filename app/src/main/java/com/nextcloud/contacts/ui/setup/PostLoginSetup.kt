package com.nextcloud.contacts.ui.setup

import com.nextcloud.contacts.data.prefs.ContactDatesDestination
import com.nextcloud.contacts.platform.ContactCalendarEventKind
import com.nextcloud.contacts.ui.navigation.Routes

object PostLoginSetup {
    val dateKinds: List<ContactCalendarEventKind> = listOf(
        ContactCalendarEventKind.BIRTHDAY,
        ContactCalendarEventKind.ANNIVERSARY,
        ContactCalendarEventKind.DEATH,
    )

    /**
     * After login, setup stays up until Done. A cold start with that flag set opens
     * the contact list. Skip on the login screen is the unsigned path to the list.
     */
    fun startDestination(
        loggedIn: Boolean,
        loginSkipped: Boolean,
        setupCompleted: Boolean,
    ): String {
        if (!loggedIn) {
            return if (loginSkipped) Routes.CONTACTS else Routes.SETUP
        }
        return if (setupCompleted) Routes.CONTACTS else Routes.POST_LOGIN_SETUP
    }

    fun afterLoginDestination(): String = Routes.POST_LOGIN_SETUP

    /**
     * A failed capabilities read must not hide Nextcloud. A cached answer stays.
     * With no cache, one retry is enough; still no answer leaves the current default.
     */
    fun nextStepAfterCalendarProbe(hasCachedValue: Boolean, alreadyRetried: Boolean): CalendarProbeStep {
        if (hasCachedValue) return CalendarProbeStep.KeepCached
        if (!alreadyRetried) return CalendarProbeStep.RetryOnce
        return CalendarProbeStep.LeaveDefault
    }

    enum class CalendarProbeStep { KeepCached, RetryOnce, LeaveDefault }

    /** The one setup choice is stored for every date kind. */
    fun destinationsForAllKinds(
        destination: ContactDatesDestination,
    ): Map<ContactCalendarEventKind, ContactDatesDestination> =
        dateKinds.associateWith { destination }

    /**
     * One control for three stored keys. Absent keys use the usual fallback
     * (Nextcloud when that calendar exists, otherwise None). A mix selects nothing.
     */
    fun sharedDateDestination(
        birthdays: ContactDatesDestination?,
        anniversaries: ContactDatesDestination?,
        deaths: ContactDatesDestination?,
        nextcloudCalendarAvailable: Boolean?,
    ): ContactDatesDestination? {
        val displayed = listOf(birthdays, anniversaries, deaths).map { stored ->
            ContactDatesDestination.displaySelection(stored, nextcloudCalendarAvailable)
        }
        return displayed.distinct().singleOrNull()
    }
}
