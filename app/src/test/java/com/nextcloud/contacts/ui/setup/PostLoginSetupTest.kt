package com.nextcloud.contacts.ui.setup

import com.nextcloud.contacts.data.prefs.ConnectedAppsAccess
import com.nextcloud.contacts.data.prefs.ContactDatesDestination
import com.nextcloud.contacts.data.prefs.UserPreferences
import com.nextcloud.contacts.platform.ContactCalendarEventKind
import com.nextcloud.contacts.platform.MessagingContactsIntents
import com.nextcloud.contacts.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostLoginSetupTest {

    @Test
    fun startDestination_loggedInSetupComplete_opensContacts() {
        assertEquals(
            Routes.CONTACTS,
            PostLoginSetup.startDestination(
                loggedIn = true,
                loginSkipped = false,
                setupCompleted = true,
            ),
        )
        assertEquals(
            Routes.CONTACTS,
            PostLoginSetup.startDestination(
                loggedIn = true,
                loginSkipped = true,
                setupCompleted = true,
            ),
        )
    }

    @Test
    fun startDestination_loggedInSetupIncomplete_opensSetup() {
        assertEquals(
            Routes.POST_LOGIN_SETUP,
            PostLoginSetup.startDestination(
                loggedIn = true,
                loginSkipped = false,
                setupCompleted = false,
            ),
        )
        assertEquals(
            Routes.POST_LOGIN_SETUP,
            PostLoginSetup.startDestination(
                loggedIn = true,
                loginSkipped = true,
                setupCompleted = false,
            ),
        )
    }

    @Test
    fun startDestination_skippedLogin_opensContacts() {
        assertEquals(
            Routes.CONTACTS,
            PostLoginSetup.startDestination(
                loggedIn = false,
                loginSkipped = true,
                setupCompleted = false,
            ),
        )
        assertEquals(
            Routes.CONTACTS,
            PostLoginSetup.startDestination(
                loggedIn = false,
                loginSkipped = true,
                setupCompleted = true,
            ),
        )
    }

    @Test
    fun startDestination_signedOut_opensLogin() {
        assertEquals(
            Routes.SETUP,
            PostLoginSetup.startDestination(
                loggedIn = false,
                loginSkipped = false,
                setupCompleted = false,
            ),
        )
        assertEquals(
            Routes.SETUP,
            PostLoginSetup.startDestination(
                loggedIn = false,
                loginSkipped = false,
                setupCompleted = true,
            ),
        )
    }

    @Test
    fun afterLogin_opensSetup() {
        assertEquals(Routes.POST_LOGIN_SETUP, PostLoginSetup.afterLoginDestination())
    }

    @Test
    fun destinationsForAllKinds_writesOneChoiceToEveryKind() {
        ContactDatesDestination.entries.forEach { choice ->
            assertEquals(
                mapOf(
                    ContactCalendarEventKind.BIRTHDAY to choice,
                    ContactCalendarEventKind.ANNIVERSARY to choice,
                    ContactCalendarEventKind.DEATH to choice,
                ),
                PostLoginSetup.destinationsForAllKinds(choice),
            )
        }
    }

    @Test
    fun sharedDateDestination_nullWhenKindsDisagree() {
        assertNull(
            PostLoginSetup.sharedDateDestination(
                birthdays = ContactDatesDestination.SYSTEM,
                anniversaries = ContactDatesDestination.NONE,
                deaths = ContactDatesDestination.SYSTEM,
                nextcloudCalendarAvailable = false,
            ),
        )
    }

    @Test
    fun sharedDateDestination_unsetWithNextcloud_showsNextcloud() {
        assertEquals(
            ContactDatesDestination.NEXTCLOUD,
            PostLoginSetup.sharedDateDestination(
                birthdays = null,
                anniversaries = null,
                deaths = null,
                nextcloudCalendarAvailable = true,
            ),
        )
    }

    @Test
    fun sharedDateDestination_unsetWithoutNextcloud_showsNone() {
        assertEquals(
            ContactDatesDestination.NONE,
            PostLoginSetup.sharedDateDestination(
                birthdays = null,
                anniversaries = null,
                deaths = null,
                nextcloudCalendarAvailable = false,
            ),
        )
        assertEquals(
            ContactDatesDestination.NONE,
            PostLoginSetup.sharedDateDestination(
                birthdays = null,
                anniversaries = null,
                deaths = null,
                nextcloudCalendarAvailable = null,
            ),
        )
    }

    @Test
    fun sharedDateDestination_unavailableCalendar_mapsNextcloudAndBoth() {
        assertEquals(
            ContactDatesDestination.NONE,
            PostLoginSetup.sharedDateDestination(
                birthdays = ContactDatesDestination.NEXTCLOUD,
                anniversaries = ContactDatesDestination.NEXTCLOUD,
                deaths = ContactDatesDestination.NEXTCLOUD,
                nextcloudCalendarAvailable = false,
            ),
        )
        assertEquals(
            ContactDatesDestination.SYSTEM,
            PostLoginSetup.sharedDateDestination(
                birthdays = ContactDatesDestination.BOTH,
                anniversaries = ContactDatesDestination.BOTH,
                deaths = ContactDatesDestination.BOTH,
                nextcloudCalendarAvailable = false,
            ),
        )
    }

    @Test
    fun calendarProbe_keepsCacheAndRetriesOnceWhenUnset() {
        assertEquals(
            PostLoginSetup.CalendarProbeStep.KeepCached,
            PostLoginSetup.nextStepAfterCalendarProbe(hasCachedValue = true, alreadyRetried = false),
        )
        assertEquals(
            PostLoginSetup.CalendarProbeStep.KeepCached,
            PostLoginSetup.nextStepAfterCalendarProbe(hasCachedValue = true, alreadyRetried = true),
        )
        assertEquals(
            PostLoginSetup.CalendarProbeStep.RetryOnce,
            PostLoginSetup.nextStepAfterCalendarProbe(hasCachedValue = false, alreadyRetried = false),
        )
        assertEquals(
            PostLoginSetup.CalendarProbeStep.LeaveDefault,
            PostLoginSetup.nextStepAfterCalendarProbe(hasCachedValue = false, alreadyRetried = true),
        )
    }

    @Test
    fun websiteLogos_unsetIsOn_storedFalseStaysOff() {
        assertTrue(UserPreferences.websiteLogosEnabled(null))
        assertFalse(UserPreferences.websiteLogosEnabled(false))
        assertTrue(UserPreferences.websiteLogosEnabled(true))
    }

    @Test
    fun connectedApps_shownOnlyWhenReadIsGranted() {
        assertFalse(ConnectedAppsAccess.discoveryEnabled(readContactsGranted = false))
        assertTrue(ConnectedAppsAccess.discoveryEnabled(readContactsGranted = true))
    }

    @Test
    fun phoneCalls_directWhenCallPermissionIsGranted() {
        assertFalse(MessagingContactsIntents.startsCallDirectly(callPermissionGranted = false))
        assertTrue(MessagingContactsIntents.startsCallDirectly(callPermissionGranted = true))
    }
}
