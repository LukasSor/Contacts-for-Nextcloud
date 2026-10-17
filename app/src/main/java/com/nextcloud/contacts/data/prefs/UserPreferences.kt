package com.nextcloud.contacts.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.emptyPreferences
import com.nextcloud.contacts.data.remote.PhotonGeocodingService
import com.nextcloud.contacts.platform.ContactCalendarEventKind
import com.nextcloud.contacts.platform.DeviceContactAccount
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/** Synchronous read of the last persisted preference values (for first-frame UI seeding). */
data class UserPreferencesSnapshot(
    val themeMode: ThemeMode,
    val useDynamicColor: Boolean,
    val enableWebsiteLogos: Boolean,
    val storedBirthdaysDestination: ContactDatesDestination?,
    val storedAnniversariesDestination: ContactDatesDestination?,
    val storedDatesOfDeathDestination: ContactDatesDestination?,
    val calendarEventDescriptionMode: CalendarEventDescriptionMode,
    val showScrollLetters: Boolean,
    val avatarFrameStyle: AvatarFrameStyle,
    val exportToDeviceContacts: Boolean,
    val autoLinkMessengers: Boolean,
    val hideLinkSuggestions: Boolean,
    val locationRecommendationsEnabled: Boolean,
    val photonBaseUrl: String,
    val disabledAccountKeys: Set<String>,
    val lastSyncTime: Long?,
    val contactEditFieldMode: ContactEditFieldMode,
    val fieldSyntaxEnabled: Boolean,
    val largeFavorites: Boolean = false,
)

class UserPreferences(private val context: Context) {

    private val syncPrefs = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
    private val snapshotCache = AtomicReference<UserPreferencesSnapshot?>(null)
    private val snapshotScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // DataStore has no synchronous read. Fill the cache off the main thread.
        snapshotScope.launch {
            context.dataStore.data.collect { prefs ->
                snapshotCache.set(snapshotFromPreferences(prefs))
            }
        }
    }

    /** Last known prefs, or defaults until the first DataStore emission. */
    fun snapshot(): UserPreferencesSnapshot =
        snapshotCache.get() ?: snapshotFromPreferences(emptyPreferences())

    fun getCachedProviderAccounts(): List<DeviceContactAccount> {
        val raw = syncPrefs.getString(KEY_CACHED_PROVIDER_ACCOUNTS, null)?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val entry = array.optJSONObject(i) ?: continue
                    val accountType = entry.optString("accountType").trim()
                    if (accountType.isBlank()) continue
                    add(
                        DeviceContactAccount(
                            accountType = accountType,
                            accountName = entry.optString("accountName").trim(),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun getCachedNextcloudCalendarAvailable(): Boolean? {
        if (!syncPrefs.contains(KEY_CACHED_NEXTCLOUD_CALENDAR_AVAILABLE)) return null
        return syncPrefs.getBoolean(KEY_CACHED_NEXTCLOUD_CALENDAR_AVAILABLE, false)
    }

    fun setCachedNextcloudCalendarAvailable(available: Boolean) {
        syncPrefs.edit().putBoolean(KEY_CACHED_NEXTCLOUD_CALENDAR_AVAILABLE, available).apply()
    }

    fun setCachedProviderAccounts(accounts: List<DeviceContactAccount>) {
        if (accounts.isEmpty()) {
            syncPrefs.edit().remove(KEY_CACHED_PROVIDER_ACCOUNTS).apply()
            return
        }
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject()
                    .put("accountType", account.accountType)
                    .put("accountName", account.accountName),
            )
        }
        syncPrefs.edit().putString(KEY_CACHED_PROVIDER_ACCOUNTS, array.toString()).apply()
    }

    private fun snapshotFromPreferences(prefs: Preferences): UserPreferencesSnapshot {
        val themeMode = when (prefs[KEY_THEME_MODE]) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            "amoled" -> ThemeMode.AMOLED
            else -> ThemeMode.SYSTEM
        }
        val disabledAccountKeys = prefs[KEY_DISABLED_ACCOUNTS] ?: emptySet()
        return UserPreferencesSnapshot(
            themeMode = themeMode,
            useDynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            enableWebsiteLogos = websiteLogosEnabled(prefs[KEY_ENABLE_WEBSITE_LOGOS]),
            storedBirthdaysDestination = readStoredDestinationForKind(prefs, ContactCalendarEventKind.BIRTHDAY),
            storedAnniversariesDestination = readStoredDestinationForKind(prefs, ContactCalendarEventKind.ANNIVERSARY),
            storedDatesOfDeathDestination = readStoredDestinationForKind(prefs, ContactCalendarEventKind.DEATH),
            calendarEventDescriptionMode = CalendarEventDescriptionMode.fromStorage(
                prefs[KEY_CALENDAR_EVENT_DESCRIPTION_MODE],
            ),
            showScrollLetters = prefs[KEY_SHOW_SCROLL_LETTERS] ?: true,
            avatarFrameStyle = when (prefs[KEY_AVATAR_FRAME]) {
                "circle" -> AvatarFrameStyle.CIRCLE
                else -> AvatarFrameStyle.ORGANIC
            },
            exportToDeviceContacts = prefs[KEY_EXPORT_TO_DEVICE] ?: false,
            autoLinkMessengers = prefs[KEY_AUTO_LINK_MESSENGERS] ?: false,
            hideLinkSuggestions = prefs[KEY_HIDE_LINK_SUGGESTIONS] ?: false,
            locationRecommendationsEnabled = prefs[KEY_LOCATION_RECOMMENDATIONS] ?: true,
            photonBaseUrl = PhotonGeocodingService.normalizeBaseUrl(
                prefs[KEY_PHOTON_BASE_URL] ?: DEFAULT_PHOTON_BASE_URL,
            ),
            disabledAccountKeys = disabledAccountKeys,
            lastSyncTime = prefs[KEY_LAST_SYNC],
            contactEditFieldMode = when (prefs[KEY_CONTACT_EDIT_FIELD_MODE]) {
                "work" -> ContactEditFieldMode.WORK
                "full" -> ContactEditFieldMode.FULL
                else -> ContactEditFieldMode.SIMPLE
            },
            fieldSyntaxEnabled = prefs[KEY_FIELD_SYNTAX] ?: false,
            largeFavorites = prefs[KEY_LARGE_FAVORITES] ?: false,
        )
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_THEME_MODE]) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            "amoled" -> ThemeMode.AMOLED
            else -> ThemeMode.SYSTEM
        }
    }

    val useDynamicColor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: true
    }

    val enableWebsiteLogos: Flow<Boolean> = context.dataStore.data.map { prefs ->
        websiteLogosEnabled(prefs[KEY_ENABLE_WEBSITE_LOGOS])
    }

    fun storedDestinationForKind(kind: ContactCalendarEventKind): Flow<ContactDatesDestination?> =
        context.dataStore.data.map { prefs -> readStoredDestinationForKind(prefs, kind) }

    val storedBirthdaysDestination: Flow<ContactDatesDestination?> =
        storedDestinationForKind(ContactCalendarEventKind.BIRTHDAY)

    val storedAnniversariesDestination: Flow<ContactDatesDestination?> =
        storedDestinationForKind(ContactCalendarEventKind.ANNIVERSARY)

    val storedDatesOfDeathDestination: Flow<ContactDatesDestination?> =
        storedDestinationForKind(ContactCalendarEventKind.DEATH)

    val calendarEventDescriptionMode: Flow<CalendarEventDescriptionMode> = context.dataStore.data.map { prefs ->
        CalendarEventDescriptionMode.fromStorage(prefs[KEY_CALENDAR_EVENT_DESCRIPTION_MODE])
    }

    private fun readStoredDestinationForKind(
        prefs: Preferences,
        kind: ContactCalendarEventKind,
    ): ContactDatesDestination? {
        val kindKey = destinationKeyForKind(kind)
        if (!prefs.contains(kindKey)) return null
        return ContactDatesDestination.fromStorage(prefs[kindKey])
    }

    val showScrollLetters: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_SCROLL_LETTERS] ?: true
    }

    val largeFavorites: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LARGE_FAVORITES] ?: false
    }

    val avatarFrameStyle: Flow<AvatarFrameStyle> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_AVATAR_FRAME]) {
            "circle" -> AvatarFrameStyle.CIRCLE
            else -> AvatarFrameStyle.ORGANIC
        }
    }

    val autoLinkMessengers: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_LINK_MESSENGERS] ?: false
    }

    val hideLinkSuggestions: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HIDE_LINK_SUGGESTIONS] ?: false
    }

    val dismissedImLinkKeys: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISMISSED_IM_LINKS] ?: emptySet()
    }

    val lastSyncTime: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNC]
    }

    val syncToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYNC_TOKEN]
    }

    val exportToDeviceContacts: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_EXPORT_TO_DEVICE] ?: false
    }

    val locationRecommendationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LOCATION_RECOMMENDATIONS] ?: true
    }

    val photonBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PHOTON_BASE_URL] ?: DEFAULT_PHOTON_BASE_URL
    }

    val contactEditFieldMode: Flow<ContactEditFieldMode> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_CONTACT_EDIT_FIELD_MODE]) {
            "work" -> ContactEditFieldMode.WORK
            "full" -> ContactEditFieldMode.FULL
            else -> ContactEditFieldMode.SIMPLE
        }
    }

    val fieldSyntaxEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FIELD_SYNTAX] ?: false
    }

    /** Global comparison clocks shown on every contact time-zone screen. */
    val comparisonTimeZoneIds: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_COMPARISON_TIME_ZONES]?.toList()?.sorted() ?: emptyList()
    }

    val disabledAccountKeys: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISABLED_ACCOUNTS] ?: emptySet()
    }

    suspend fun setAccountEnabled(accountKey: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_DISABLED_ACCOUNTS]?.toMutableSet() ?: mutableSetOf()
            if (enabled) {
                current.remove(accountKey)
            } else {
                current.add(accountKey)
            }
            if (current.isEmpty()) {
                prefs.remove(KEY_DISABLED_ACCOUNTS)
            } else {
                prefs[KEY_DISABLED_ACCOUNTS] = current
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = when (mode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.AMOLED -> "amoled"
                ThemeMode.SYSTEM -> "system"
            }
        }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setEnableWebsiteLogos(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLE_WEBSITE_LOGOS] = enabled
        }
    }

    suspend fun setDestinationForKind(kind: ContactCalendarEventKind, destination: ContactDatesDestination) {
        context.dataStore.edit { prefs ->
            prefs[destinationKeyForKind(kind)] = destination.storageValue()
        }
    }

    suspend fun setCalendarEventDescriptionMode(mode: CalendarEventDescriptionMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CALENDAR_EVENT_DESCRIPTION_MODE] = mode.toStorageValue()
        }
    }

    private fun destinationKeyForKind(kind: ContactCalendarEventKind) = when (kind) {
        ContactCalendarEventKind.BIRTHDAY -> KEY_BIRTHDAYS_DESTINATION
        ContactCalendarEventKind.ANNIVERSARY -> KEY_ANNIVERSARIES_DESTINATION
        ContactCalendarEventKind.DEATH -> KEY_DATES_OF_DEATH_DESTINATION
    }

    suspend fun setShowScrollLetters(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOW_SCROLL_LETTERS] = enabled
        }
    }

    suspend fun setLargeFavorites(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LARGE_FAVORITES] = enabled
        }
    }

    suspend fun setAvatarFrameStyle(style: AvatarFrameStyle) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AVATAR_FRAME] = when (style) {
                AvatarFrameStyle.CIRCLE -> "circle"
                AvatarFrameStyle.ORGANIC -> "organic"
            }
        }
    }

    suspend fun setAutoLinkMessengers(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_LINK_MESSENGERS] = enabled
        }
    }

    suspend fun setHideLinkSuggestions(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HIDE_LINK_SUGGESTIONS] = enabled
        }
    }

    suspend fun dismissImLinkSuggestion(key: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_DISMISSED_IM_LINKS]?.toMutableSet() ?: mutableSetOf()
            current.add(key)
            prefs[KEY_DISMISSED_IM_LINKS] = current
        }
    }

    suspend fun setLastSyncTime(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC] = timestamp
        }
    }

    suspend fun setSyncToken(token: String?) {
        context.dataStore.edit { prefs ->
            if (token == null) {
                prefs.remove(KEY_SYNC_TOKEN)
            } else {
                prefs[KEY_SYNC_TOKEN] = token
            }
        }
    }

    suspend fun setExportToDeviceContacts(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EXPORT_TO_DEVICE] = enabled
        }
    }

    suspend fun setLocationRecommendationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCATION_RECOMMENDATIONS] = enabled
        }
    }

    suspend fun setPhotonBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            val trimmed = url.trim()
            if (trimmed.isEmpty() || trimmed == DEFAULT_PHOTON_BASE_URL) {
                prefs.remove(KEY_PHOTON_BASE_URL)
            } else {
                prefs[KEY_PHOTON_BASE_URL] = trimmed
            }
        }
    }

    suspend fun setContactEditFieldMode(mode: ContactEditFieldMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CONTACT_EDIT_FIELD_MODE] = when (mode) {
                ContactEditFieldMode.SIMPLE -> "simple"
                ContactEditFieldMode.WORK -> "work"
                ContactEditFieldMode.FULL -> "full"
            }
        }
    }

    suspend fun setFieldSyntaxEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FIELD_SYNTAX] = enabled
        }
    }

    suspend fun addComparisonTimeZone(zoneId: String) {
        val trimmed = zoneId.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_COMPARISON_TIME_ZONES]?.toMutableSet() ?: mutableSetOf()
            current.add(trimmed)
            prefs[KEY_COMPARISON_TIME_ZONES] = current
        }
    }

    suspend fun removeComparisonTimeZone(zoneId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_COMPARISON_TIME_ZONES]?.toMutableSet() ?: return@edit
            current.remove(zoneId)
            if (current.isEmpty()) {
                prefs.remove(KEY_COMPARISON_TIME_ZONES)
            } else {
                prefs[KEY_COMPARISON_TIME_ZONES] = current
            }
        }
    }

    suspend fun clearSyncState() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SYNC_TOKEN)
            prefs.remove(KEY_LAST_SYNC)
        }
    }

    fun isSetupSkipped(): Boolean = syncPrefs.getBoolean(KEY_SETUP_SKIPPED, false)

    fun setSetupSkipped(skipped: Boolean) {
        syncPrefs.edit().putBoolean(KEY_SETUP_SKIPPED, skipped).apply()
    }

    /** False until Done. Login must not set this, and composing the page must not either. */
    fun isPostLoginSetupComplete(): Boolean =
        syncPrefs.getBoolean(KEY_POST_LOGIN_SETUP_COMPLETE, false)

    fun setPostLoginSetupComplete(complete: Boolean) {
        syncPrefs.edit().putBoolean(KEY_POST_LOGIN_SETUP_COMPLETE, complete).commit()
    }

    companion object {
        private const val SYNC_PREFS = "user_preferences_sync"
        private const val KEY_SETUP_SKIPPED = "setup_skipped"
        private const val KEY_POST_LOGIN_SETUP_COMPLETE = "post_login_setup_complete"

        /** Unset stays on. A stored false is left false; there is no migration. */
        const val DEFAULT_ENABLE_WEBSITE_LOGOS = true

        fun websiteLogosEnabled(stored: Boolean?): Boolean = stored ?: DEFAULT_ENABLE_WEBSITE_LOGOS

        private const val KEY_CACHED_PROVIDER_ACCOUNTS = "cached_provider_accounts"
        private const val KEY_CACHED_NEXTCLOUD_CALENDAR_AVAILABLE = "cached_nextcloud_calendar_available"
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_ENABLE_WEBSITE_LOGOS = booleanPreferencesKey("enable_website_logos")
        private val KEY_BIRTHDAYS_DESTINATION =
            stringPreferencesKey(ContactDatesDestination.BIRTHDAYS_PREFERENCE)
        private val KEY_ANNIVERSARIES_DESTINATION =
            stringPreferencesKey(ContactDatesDestination.ANNIVERSARIES_PREFERENCE)
        private val KEY_DATES_OF_DEATH_DESTINATION =
            stringPreferencesKey(ContactDatesDestination.DATES_OF_DEATH_PREFERENCE)
        private val KEY_CALENDAR_EVENT_DESCRIPTION_MODE =
            stringPreferencesKey("calendar_event_description_mode")
        private val KEY_SHOW_SCROLL_LETTERS = booleanPreferencesKey("show_scroll_letters")
        private val KEY_LARGE_FAVORITES = booleanPreferencesKey("large_favorites")
        private val KEY_AVATAR_FRAME = stringPreferencesKey("avatar_frame")
        private val KEY_AUTO_LINK_MESSENGERS = booleanPreferencesKey("auto_link_messengers")
        private val KEY_HIDE_LINK_SUGGESTIONS = booleanPreferencesKey("hide_link_suggestions")
        private val KEY_DISMISSED_IM_LINKS = stringSetPreferencesKey("dismissed_im_links")
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync")
        private val KEY_SYNC_TOKEN = stringPreferencesKey("sync_token")
        private val KEY_EXPORT_TO_DEVICE = booleanPreferencesKey("export_to_device")
        private val KEY_LOCATION_RECOMMENDATIONS = booleanPreferencesKey("location_recommendations")
        private val KEY_PHOTON_BASE_URL = stringPreferencesKey("photon_base_url")
        private val KEY_CONTACT_EDIT_FIELD_MODE = stringPreferencesKey("contact_edit_field_mode")
        private val KEY_FIELD_SYNTAX = booleanPreferencesKey("contact_edit_field_syntax")
        private val KEY_COMPARISON_TIME_ZONES = stringSetPreferencesKey("comparison_time_zones")
        const val DEFAULT_PHOTON_BASE_URL = "https://photon.komoot.io"
        private val KEY_DISABLED_ACCOUNTS = stringSetPreferencesKey("disabled_accounts")
    }
}
