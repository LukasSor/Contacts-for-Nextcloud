package com.nextcloud.contacts.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.annotation.PluralsRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.R
import com.nextcloud.contacts.auth.AccountCredentials
import com.nextcloud.contacts.data.prefs.AvatarFrameStyle
import com.nextcloud.contacts.data.prefs.CalendarEventDescriptionMode
import com.nextcloud.contacts.data.prefs.ContactDatesDestination
import com.nextcloud.contacts.data.prefs.ContactEditFieldMode
import com.nextcloud.contacts.data.prefs.ThemeMode
import com.nextcloud.contacts.data.prefs.UserPreferences
import com.nextcloud.contacts.data.remote.NextcloudCapabilitiesService
import com.nextcloud.contacts.data.remote.PhotonGeocodingService
import com.nextcloud.contacts.data.device.importDeviceContacts
import com.nextcloud.contacts.data.vcf.formatVcfImportMessage
import com.nextcloud.contacts.platform.ContactCalendarEventKind
import com.nextcloud.contacts.platform.ContactsProviderAccounts
import com.nextcloud.contacts.platform.DeviceContactAccount
import com.nextcloud.contacts.platform.KnownContactAccountTypes
import com.nextcloud.contacts.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

data class AccountToggleState(
    val key: String,
    val displayName: String,
    val accountType: String,
    val accountName: String,
    val enabled: Boolean,
)

private data class ThemeAppearancePrefs(
    val themeMode: ThemeMode,
    val useDynamicColor: Boolean,
    val enableWebsiteLogos: Boolean,
    val storedBirthdaysDestination: ContactDatesDestination?,
    val storedAnniversariesDestination: ContactDatesDestination?,
    val storedDatesOfDeathDestination: ContactDatesDestination?,
    val calendarEventDescriptionMode: CalendarEventDescriptionMode,
    val showScrollLetters: Boolean,
    val avatarFrameStyle: AvatarFrameStyle,
)

private data class PreferencesSnapshot(
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
)

data class SettingsUiState(
    val account: AccountCredentials? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val enableWebsiteLogos: Boolean = true,
    val birthdaysDestination: ContactDatesDestination = ContactDatesDestination.NONE,
    val anniversariesDestination: ContactDatesDestination = ContactDatesDestination.NONE,
    val datesOfDeathDestination: ContactDatesDestination = ContactDatesDestination.NONE,
    val calendarEventDescriptionMode: CalendarEventDescriptionMode = CalendarEventDescriptionMode.DEFAULT,
    val nextcloudCalendarAvailable: Boolean? = null,
    val showScrollLetters: Boolean = true,
    val avatarFrameStyle: AvatarFrameStyle = AvatarFrameStyle.ORGANIC,
    val exportToDeviceContacts: Boolean = false,
    val autoLinkMessengers: Boolean = false,
    val hideLinkSuggestions: Boolean = false,
    val locationRecommendationsEnabled: Boolean = true,
    val photonBaseUrl: String = UserPreferences.DEFAULT_PHOTON_BASE_URL,
    val accountToggles: List<AccountToggleState> = emptyList(),
    val accountsNeedContactsPermission: Boolean = false,
    val lastSyncLabel: String = "",
    val isSyncing: Boolean = false,
    val isImporting: Boolean = false,
    val isExportingVcf: Boolean = false,
    val message: String? = null,
    val contactEditFieldMode: ContactEditFieldMode = ContactEditFieldMode.SIMPLE,
    val fieldSyntaxEnabled: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as ContactsApp).container
    private val app = application
    private val seededPreferences = container.userPreferences.snapshot()
    private val seededProviderAccounts = container.userPreferences.getCachedProviderAccounts()
        .map { account ->
            AccountToggleState(
                key = account.key,
                displayName = accountLabel(account),
                accountType = account.accountType,
                accountName = account.accountName,
                enabled = true,
            )
        }

    private val _isSyncing = MutableStateFlow(false)
    private val _isImporting = MutableStateFlow(false)
    private val _isExportingVcf = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _providerAccounts = MutableStateFlow(seededProviderAccounts)
    private val _accountsNeedPermission = MutableStateFlow(false)
    private val _nextcloudCalendarAvailable = MutableStateFlow(
        container.userPreferences.getCachedNextcloudCalendarAvailable(),
    )

    private val activityState = combine(
        _isSyncing,
        _isImporting,
        _isExportingVcf,
        _message,
    ) { syncing, importing, exportingVcf, message ->
        ActivitySnapshot(syncing, importing, exportingVcf, message)
    }

    private data class ActivitySnapshot(
        val syncing: Boolean,
        val importing: Boolean,
        val exportingVcf: Boolean,
        val message: String?,
    )

    private data class CalendarPrefs(
        val storedBirthdaysDestination: ContactDatesDestination?,
        val storedAnniversariesDestination: ContactDatesDestination?,
        val storedDatesOfDeathDestination: ContactDatesDestination?,
        val calendarEventDescriptionMode: CalendarEventDescriptionMode,
    )

    private val preferencesState = combine(
        combine(
            combine(
                container.userPreferences.themeMode,
                container.userPreferences.useDynamicColor,
                container.userPreferences.enableWebsiteLogos,
            ) { themeMode, dynamicColor, websiteLogos ->
                Triple(themeMode, dynamicColor, websiteLogos)
            },
            combine(
                container.userPreferences.storedBirthdaysDestination,
                container.userPreferences.storedAnniversariesDestination,
                container.userPreferences.storedDatesOfDeathDestination,
                container.userPreferences.calendarEventDescriptionMode,
            ) { birthdays, anniversaries, datesOfDeath, descriptionMode ->
                CalendarPrefs(birthdays, anniversaries, datesOfDeath, descriptionMode)
            },
            combine(
                container.userPreferences.showScrollLetters,
                container.userPreferences.avatarFrameStyle,
            ) { scrollLetters, avatarFrame ->
                scrollLetters to avatarFrame
            },
        ) { themePrefs, calendarPrefs, scrollAndAvatar ->
            ThemeAppearancePrefs(
                themeMode = themePrefs.first,
                useDynamicColor = themePrefs.second,
                enableWebsiteLogos = themePrefs.third,
                storedBirthdaysDestination = calendarPrefs.storedBirthdaysDestination,
                storedAnniversariesDestination = calendarPrefs.storedAnniversariesDestination,
                storedDatesOfDeathDestination = calendarPrefs.storedDatesOfDeathDestination,
                calendarEventDescriptionMode = calendarPrefs.calendarEventDescriptionMode,
                showScrollLetters = scrollAndAvatar.first,
                avatarFrameStyle = scrollAndAvatar.second,
            )
        },
        combine(
            container.userPreferences.exportToDeviceContacts,
            container.userPreferences.autoLinkMessengers,
            container.userPreferences.hideLinkSuggestions,
        ) { exportToDevice, autoLink, hideLinkSuggestions ->
            Triple(exportToDevice, autoLink, hideLinkSuggestions)
        },
        combine(
            container.userPreferences.locationRecommendationsEnabled,
            container.userPreferences.photonBaseUrl,
        ) { locationEnabled, photonUrl ->
            locationEnabled to PhotonGeocodingService.normalizeBaseUrl(photonUrl)
        },
        container.userPreferences.disabledAccountKeys,
        combine(
            container.userPreferences.lastSyncTime,
            container.userPreferences.contactEditFieldMode,
            container.userPreferences.fieldSyntaxEnabled,
        ) { lastSync, fieldMode, fieldSyntax ->
            Triple(lastSync, fieldMode, fieldSyntax)
        },
    ) { themePrefs, exportAndMessaging, locationPrefs, disabledAccounts, syncFieldAndSyntax ->
        PreferencesSnapshot(
            themeMode = themePrefs.themeMode,
            useDynamicColor = themePrefs.useDynamicColor,
            enableWebsiteLogos = themePrefs.enableWebsiteLogos,
            storedBirthdaysDestination = themePrefs.storedBirthdaysDestination,
            storedAnniversariesDestination = themePrefs.storedAnniversariesDestination,
            storedDatesOfDeathDestination = themePrefs.storedDatesOfDeathDestination,
            calendarEventDescriptionMode = themePrefs.calendarEventDescriptionMode,
            showScrollLetters = themePrefs.showScrollLetters,
            avatarFrameStyle = themePrefs.avatarFrameStyle,
            exportToDeviceContacts = exportAndMessaging.first,
            autoLinkMessengers = exportAndMessaging.second,
            hideLinkSuggestions = exportAndMessaging.third,
            locationRecommendationsEnabled = locationPrefs.first,
            photonBaseUrl = locationPrefs.second,
            disabledAccountKeys = disabledAccounts,
            lastSyncTime = syncFieldAndSyntax.first,
            contactEditFieldMode = syncFieldAndSyntax.second,
            fieldSyntaxEnabled = syncFieldAndSyntax.third,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesState,
        activityState,
        _providerAccounts,
        _accountsNeedPermission,
        _nextcloudCalendarAvailable,
    ) { prefs, activity, providerAccounts, needsPermission, nextcloudCalendarAvailable ->
        SettingsUiState(
            account = container.credentialStore.getCredentials(),
            themeMode = prefs.themeMode,
            useDynamicColor = prefs.useDynamicColor,
            enableWebsiteLogos = prefs.enableWebsiteLogos,
            birthdaysDestination = ContactDatesDestination.displaySelection(
                prefs.storedBirthdaysDestination,
                nextcloudCalendarAvailable,
            ),
            anniversariesDestination = ContactDatesDestination.displaySelection(
                prefs.storedAnniversariesDestination,
                nextcloudCalendarAvailable,
            ),
            datesOfDeathDestination = ContactDatesDestination.displaySelection(
                prefs.storedDatesOfDeathDestination,
                nextcloudCalendarAvailable,
            ),
            calendarEventDescriptionMode = prefs.calendarEventDescriptionMode,
            nextcloudCalendarAvailable = nextcloudCalendarAvailable,
            showScrollLetters = prefs.showScrollLetters,
            avatarFrameStyle = prefs.avatarFrameStyle,
            exportToDeviceContacts = prefs.exportToDeviceContacts,
            autoLinkMessengers = prefs.autoLinkMessengers,
            hideLinkSuggestions = prefs.hideLinkSuggestions,
            locationRecommendationsEnabled = prefs.locationRecommendationsEnabled,
            photonBaseUrl = prefs.photonBaseUrl,
            accountToggles = providerAccounts.map { account ->
                account.copy(
                    enabled = !ContactsProviderAccounts.isDisabled(
                        accountType = account.accountType,
                        accountName = account.accountName,
                        disabledKeys = prefs.disabledAccountKeys,
                    ),
                )
            },
            accountsNeedContactsPermission = needsPermission,
            lastSyncLabel = prefs.lastSyncTime?.let {
                DateFormat.getDateTimeInstance().format(Date(it))
            } ?: str(R.string.label_never),
            isSyncing = activity.syncing,
            isImporting = activity.importing,
            isExportingVcf = activity.exportingVcf,
            message = activity.message,
            contactEditFieldMode = prefs.contactEditFieldMode,
            fieldSyntaxEnabled = prefs.fieldSyntaxEnabled,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState(
            account = container.credentialStore.getCredentials(),
            themeMode = seededPreferences.themeMode,
            useDynamicColor = seededPreferences.useDynamicColor,
            enableWebsiteLogos = seededPreferences.enableWebsiteLogos,
            birthdaysDestination = ContactDatesDestination.displaySelection(
                seededPreferences.storedBirthdaysDestination,
                container.userPreferences.getCachedNextcloudCalendarAvailable(),
            ),
            anniversariesDestination = ContactDatesDestination.displaySelection(
                seededPreferences.storedAnniversariesDestination,
                container.userPreferences.getCachedNextcloudCalendarAvailable(),
            ),
            datesOfDeathDestination = ContactDatesDestination.displaySelection(
                seededPreferences.storedDatesOfDeathDestination,
                container.userPreferences.getCachedNextcloudCalendarAvailable(),
            ),
            calendarEventDescriptionMode = seededPreferences.calendarEventDescriptionMode,
            nextcloudCalendarAvailable = container.userPreferences.getCachedNextcloudCalendarAvailable(),
            showScrollLetters = seededPreferences.showScrollLetters,
            avatarFrameStyle = seededPreferences.avatarFrameStyle,
            exportToDeviceContacts = seededPreferences.exportToDeviceContacts,
            autoLinkMessengers = seededPreferences.autoLinkMessengers,
            hideLinkSuggestions = seededPreferences.hideLinkSuggestions,
            locationRecommendationsEnabled = seededPreferences.locationRecommendationsEnabled,
            photonBaseUrl = PhotonGeocodingService.normalizeBaseUrl(seededPreferences.photonBaseUrl),
            accountToggles = seededProviderAccounts.map { account ->
                account.copy(
                    enabled = !ContactsProviderAccounts.isDisabled(
                        accountType = account.accountType,
                        accountName = account.accountName,
                        disabledKeys = seededPreferences.disabledAccountKeys,
                    ),
                )
            },
            lastSyncLabel = seededPreferences.lastSyncTime?.let {
                DateFormat.getDateTimeInstance().format(Date(it))
            } ?: str(R.string.label_never),
            contactEditFieldMode = seededPreferences.contactEditFieldMode,
        ),
    )

    init {
        refreshProviderAccounts()
        refreshNextcloudCalendarAvailability()
        viewModelScope.launch {
            container.contactDatesCalendarCoordinator.nextcloudCalendarFailures.collect {
                _message.value = str(R.string.msg_nextcloud_calendar_failed)
            }
        }
    }

    fun refreshNextcloudCalendarAvailability() {
        viewModelScope.launch {
            val credentials = container.credentialStore.getCredentials()
            if (credentials == null) {
                _nextcloudCalendarAvailable.value = false
                container.userPreferences.setCachedNextcloudCalendarAvailable(false)
                return@launch
            }
            val available = runCatching {
                withContext(Dispatchers.IO) {
                    NextcloudCapabilitiesService(credentials).isCalendarAppEnabled()
                }
            }.getOrNull() ?: return@launch
            _nextcloudCalendarAvailable.value = available
            container.userPreferences.setCachedNextcloudCalendarAvailable(available)
        }
    }

    fun refreshProviderAccounts() {
        viewModelScope.launch {
            val accounts = withContext(Dispatchers.IO) {
                ContactsProviderAccounts.discover(app)
            }
            _accountsNeedPermission.value = accounts.isEmpty() &&
                app.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED

            if (accounts.isNotEmpty()) {
                container.userPreferences.setCachedProviderAccounts(accounts)
            }
            _providerAccounts.value = accounts.map { account ->
                AccountToggleState(
                    key = account.key,
                    displayName = accountLabel(account),
                    accountType = account.accountType,
                    accountName = account.accountName,
                    enabled = true,
                )
            }.ifEmpty { _providerAccounts.value }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.userPreferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.userPreferences.setUseDynamicColor(enabled) }
    }

    fun setEnableWebsiteLogos(enabled: Boolean) {
        viewModelScope.launch { container.userPreferences.setEnableWebsiteLogos(enabled) }
    }

    fun setKindDestination(kind: ContactCalendarEventKind, destination: ContactDatesDestination) {
        viewModelScope.launch {
            try {
                val nextcloudFailed = container.contactDatesCalendarCoordinator.setKindDestination(kind, destination)
                if (nextcloudFailed) {
                    _message.value = str(R.string.msg_nextcloud_calendar_failed)
                }
            } catch (e: SecurityException) {
                _message.value = calendarPermissionMessage(kind)
            } catch (e: Exception) {
                _message.value = str(
                    R.string.msg_update_failed,
                    calendarKindLabel(kind),
                    e.message ?: str(R.string.msg_unknown_error),
                )
            }
        }
    }

    fun setCalendarEventDescriptionMode(mode: CalendarEventDescriptionMode) {
        viewModelScope.launch {
            try {
                val nextcloudFailed = container.contactDatesCalendarCoordinator.setEventDescriptionMode(mode)
                if (nextcloudFailed) {
                    _message.value = str(R.string.msg_nextcloud_calendar_failed)
                }
            } catch (e: SecurityException) {
                _message.value = str(R.string.msg_calendar_descriptions)
            } catch (e: Exception) {
                _message.value = str(
                    R.string.msg_update_descriptions_failed,
                    e.message ?: str(R.string.msg_unknown_error),
                )
            }
        }
    }

    private fun calendarKindLabel(kind: ContactCalendarEventKind): String = when (kind) {
        ContactCalendarEventKind.BIRTHDAY -> str(R.string.kind_birthdays)
        ContactCalendarEventKind.ANNIVERSARY -> str(R.string.kind_anniversaries)
        ContactCalendarEventKind.DEATH -> str(R.string.kind_deaths)
    }

    private fun calendarPermissionMessage(kind: ContactCalendarEventKind): String = when (kind) {
        ContactCalendarEventKind.BIRTHDAY -> str(R.string.msg_calendar_birthdays)
        ContactCalendarEventKind.ANNIVERSARY -> str(R.string.msg_calendar_anniversaries)
        ContactCalendarEventKind.DEATH -> str(R.string.msg_calendar_deaths)
    }

    private fun accountLabel(account: DeviceContactAccount): String = when (account.accountType) {
        KnownContactAccountTypes.GOOGLE ->
            account.accountName.takeIf { it.isNotBlank() } ?: "Google"
        KnownContactAccountTypes.NEXTCLOUD -> str(R.string.app_name)
        KnownContactAccountTypes.DEVICE -> str(R.string.account_phone)
        KnownContactAccountTypes.SIM -> "SIM"
        else -> account.displayName
    }

    private fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
        app.resources.getQuantityString(id, quantity, *args)

    fun setShowScrollLetters(enabled: Boolean) {
        viewModelScope.launch { container.userPreferences.setShowScrollLetters(enabled) }
    }

    fun setOrganicAvatarFrame(enabled: Boolean) {
        viewModelScope.launch {
            container.userPreferences.setAvatarFrameStyle(
                if (enabled) AvatarFrameStyle.ORGANIC else AvatarFrameStyle.CIRCLE,
            )
        }
    }

    fun setAutoLinkMessengers(enabled: Boolean) {
        viewModelScope.launch {
            container.userPreferences.setAutoLinkMessengers(enabled)
        }
    }

    fun setHideLinkSuggestions(enabled: Boolean) {
        viewModelScope.launch {
            container.userPreferences.setHideLinkSuggestions(enabled)
        }
    }

    fun setLocationRecommendationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.userPreferences.setLocationRecommendationsEnabled(enabled)
        }
    }

    fun setPhotonBaseUrl(url: String) {
        viewModelScope.launch {
            container.userPreferences.setPhotonBaseUrl(url)
        }
    }

    fun setContactEditFieldMode(mode: ContactEditFieldMode) {
        viewModelScope.launch {
            container.userPreferences.setContactEditFieldMode(mode)
        }
    }

    fun setFieldSyntaxEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.userPreferences.setFieldSyntaxEnabled(enabled)
        }
    }

    fun setAccountEnabled(accountKey: String, enabled: Boolean) {
        viewModelScope.launch {
            container.userPreferences.setAccountEnabled(accountKey, enabled)
        }
    }

    fun setExportToDeviceContacts(enabled: Boolean) {
        viewModelScope.launch {
            try {
                container.systemContactsExporter.setEnabled(enabled)
                _message.value = if (enabled) {
                    str(R.string.msg_contacts_will_appear)
                } else {
                    str(R.string.msg_contacts_removed_device)
                }
                refreshProviderAccounts()
            } catch (e: SecurityException) {
                _message.value = str(R.string.msg_contacts_settings_export)
            } catch (e: Exception) {
                _message.value = str(
                    R.string.msg_device_contacts_failed,
                    e.message ?: str(R.string.msg_unknown_error),
                )
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = container.syncManager.refreshFromServer()
            when (result) {
                is SyncManager.SyncResult.Success ->
                    _message.value = result.warning ?: if (result.pulled > 0 || result.pushed > 0) {
                        str(R.string.msg_synced_counts, result.pulled, result.pushed)
                    } else {
                        str(R.string.msg_sync_complete)
                    }
                is SyncManager.SyncResult.Error ->
                    _message.value = result.message
                is SyncManager.SyncResult.NotLoggedIn ->
                    _message.value = str(R.string.msg_not_connected)
            }
            _isSyncing.value = false
        }
    }

    fun exportContactsToVcf(uri: Uri) {
        viewModelScope.launch {
            _isExportingVcf.value = true
            try {
                val export = withContext(Dispatchers.IO) {
                    container.contactRepository.exportAllToVcf()
                }
                if (export.contactCount == 0) {
                    _message.value = str(R.string.msg_no_contacts_export)
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(export.vcardText.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException(str(R.string.msg_could_not_write_file))
                }
                _message.value = plural(R.plurals.exported_contacts, export.contactCount, export.contactCount)
            } catch (e: Exception) {
                _message.value = str(
                    R.string.msg_export_failed,
                    e.message ?: str(R.string.msg_unknown_error),
                )
            } finally {
                _isExportingVcf.value = false
            }
        }
    }

    fun importContactsFromVcf(vcardText: String) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    container.contactRepository.importFromVcf(vcardText)
                }
                _message.value = formatVcfImportMessage(app, result)
                when (val syncResult = container.syncManager.sync()) {
                    is SyncManager.SyncResult.Error ->
                        _message.value = "${_message.value}\n${str(R.string.msg_upload_failed, syncResult.message)}"
                    is SyncManager.SyncResult.Success ->
                        appendSyncFollowUp(syncResult)
                    is SyncManager.SyncResult.NotLoggedIn -> Unit
                }
            } catch (e: Exception) {
                _message.value = str(
                    R.string.msg_import_failed,
                    e.message ?: str(R.string.msg_unknown_error),
                )
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun importFromDevice() {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                _message.value = importDeviceContacts(
                    app,
                    container.deviceContactsImporter,
                    container.syncManager,
                )
                refreshProviderAccounts()
            } catch (e: SecurityException) {
                _message.value = str(R.string.msg_contacts_permission)
            } catch (e: Exception) {
                _message.value = str(
                    R.string.msg_import_failed,
                    e.message ?: str(R.string.msg_unknown_error),
                )
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun disconnect(onDisconnected: () -> Unit) {
        viewModelScope.launch {
            container.syncManager.disconnect()
            onDisconnected()
        }
    }

    private fun appendSyncFollowUp(syncResult: SyncManager.SyncResult.Success) {
        val extra = buildList {
            if (syncResult.pushed > 0) {
                add(plural(R.plurals.uploaded_to_nextcloud, syncResult.pushed, syncResult.pushed))
            }
            syncResult.warning?.let { add(it) }
        }
        if (extra.isNotEmpty()) {
            _message.value = "${_message.value}\n${extra.joinToString("\n")}"
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(text: String) {
        _message.value = text
    }
}
