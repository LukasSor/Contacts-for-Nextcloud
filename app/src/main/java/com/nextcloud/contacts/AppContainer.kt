package com.nextcloud.contacts

import android.content.Context
import androidx.room.Room
import com.nextcloud.contacts.auth.CredentialStore
import com.nextcloud.contacts.auth.LoginFlowCoordinator
import com.nextcloud.contacts.auth.LoginFlowService
import com.nextcloud.contacts.auth.LoginSessionStore
import com.nextcloud.contacts.data.ContactRepository
import com.nextcloud.contacts.data.device.DeviceContactsImporter
import com.nextcloud.contacts.data.device.DeviceContactsReader
import com.nextcloud.contacts.data.local.ContactPhotoStore
import com.nextcloud.contacts.data.local.ContactsDatabase
import com.nextcloud.contacts.data.local.WebsiteLogoStore
import com.nextcloud.contacts.data.remote.WebsiteFaviconFetcher
import com.nextcloud.contacts.data.prefs.UserPreferences
import com.nextcloud.contacts.data.remote.PhotonGeocodingService
import com.nextcloud.contacts.platform.BirthdayCalendarEventLinks
import com.nextcloud.contacts.platform.BirthdayCalendarSync
import com.nextcloud.contacts.platform.ContactDatesCalendarCoordinator
import com.nextcloud.contacts.platform.NextcloudCalendarEventLinks
import com.nextcloud.contacts.platform.NextcloudContactDatesCalendarSync
import com.nextcloud.contacts.platform.CalendarPermissionRequester
import com.nextcloud.contacts.platform.ContactsPermissionRequester
import com.nextcloud.contacts.platform.SystemContactLinks
import com.nextcloud.contacts.platform.SystemContactsExporter
import com.nextcloud.contacts.sync.SyncManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val userPreferences = UserPreferences(appContext)
    val credentialStore = CredentialStore(appContext)
    val loginFlowService by lazy { LoginFlowService(LoginFlowService.createHttpClient()) }
    val photonGeocodingService by lazy { PhotonGeocodingService() }
    private val loginSessionStore = LoginSessionStore(appContext)

    private val database = Room.databaseBuilder(
        appContext,
        ContactsDatabase::class.java,
        "contacts.db",
    ).build()

    val contactPhotoStore = ContactPhotoStore(appContext)
    val websiteLogoStore = WebsiteLogoStore(appContext)
    val websiteFaviconFetcher by lazy { WebsiteFaviconFetcher() }
    val systemContactLinks = SystemContactLinks(appContext)
    val birthdayCalendarEventLinks = BirthdayCalendarEventLinks(appContext)
    val contactsPermissionRequester = ContactsPermissionRequester()
    val calendarPermissionRequester = CalendarPermissionRequester()
    val systemContactsExporter = SystemContactsExporter(
        appContext,
        database.contactDao(),
        contactPhotoStore,
        systemContactLinks,
        userPreferences,
    )
    val birthdayCalendarSync = BirthdayCalendarSync(
        appContext,
        database.contactDao(),
        birthdayCalendarEventLinks,
        userPreferences,
    )
    val nextcloudCalendarEventLinks = NextcloudCalendarEventLinks(appContext)
    val nextcloudContactDatesCalendarSync = NextcloudContactDatesCalendarSync(
        appContext,
        database.contactDao(),
        nextcloudCalendarEventLinks,
        userPreferences,
        credentialStore::getCredentials,
    )
    val contactDatesCalendarCoordinator = ContactDatesCalendarCoordinator(
        birthdayCalendarSync,
        nextcloudContactDatesCalendarSync,
        userPreferences,
    )
    val uiNotices = UiNotices()
    val contactRepository = ContactRepository(
        database,
        database.contactDao(),
        contactPhotoStore,
        websiteLogoStore,
        websiteFaviconFetcher,
        systemContactsExporter,
        contactDatesCalendarCoordinator,
    )
    val deviceContactsImporter = DeviceContactsImporter(
        DeviceContactsReader(appContext.contentResolver),
        contactRepository,
    )
    val syncManager = SyncManager(
        appContext,
        credentialStore,
        contactRepository,
        userPreferences,
        systemContactsExporter,
        contactDatesCalendarCoordinator,
    )

    val loginFlowCoordinator by lazy {
        LoginFlowCoordinator(
            application = appContext as android.app.Application,
            loginFlowService = loginFlowService,
            sessionStore = loginSessionStore,
            credentialStore = credentialStore,
            userPreferences = userPreferences,
            syncManager = syncManager,
            uiNotices = uiNotices,
        )
    }
}
