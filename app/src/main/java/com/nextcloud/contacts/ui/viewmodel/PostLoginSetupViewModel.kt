package com.nextcloud.contacts.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.prefs.ContactDatesDestination
import com.nextcloud.contacts.data.remote.NextcloudCapabilitiesService
import com.nextcloud.contacts.ui.setup.PostLoginSetup
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PostLoginSetupUiState(
    val exportToDeviceContacts: Boolean = false,
    val storedBirthdaysDestination: ContactDatesDestination? = null,
    val storedAnniversariesDestination: ContactDatesDestination? = null,
    val storedDatesOfDeathDestination: ContactDatesDestination? = null,
    val enableWebsiteLogos: Boolean = true,
    val locationRecommendationsEnabled: Boolean = true,
    val nextcloudCalendarAvailable: Boolean? = null,
    val message: String? = null,
)

class PostLoginSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as ContactsApp).container
    private val seeded = container.userPreferences.snapshot()
    private val _message = MutableStateFlow<String?>(null)
    private val _nextcloudCalendarAvailable = MutableStateFlow(
        container.userPreferences.getCachedNextcloudCalendarAvailable(),
    )

    val uiState: StateFlow<PostLoginSetupUiState> = combine(
        combine(
            container.userPreferences.exportToDeviceContacts,
            container.userPreferences.storedBirthdaysDestination,
            container.userPreferences.storedAnniversariesDestination,
            container.userPreferences.storedDatesOfDeathDestination,
        ) { export, birthdays, anniversaries, deaths ->
            PostLoginSetupUiState(
                exportToDeviceContacts = export,
                storedBirthdaysDestination = birthdays,
                storedAnniversariesDestination = anniversaries,
                storedDatesOfDeathDestination = deaths,
            )
        },
        combine(
            container.userPreferences.enableWebsiteLogos,
            container.userPreferences.locationRecommendationsEnabled,
        ) { logos, locations -> logos to locations },
        _nextcloudCalendarAvailable,
        _message,
    ) { dates, extras, nextcloudAvailable, message ->
        dates.copy(
            enableWebsiteLogos = extras.first,
            locationRecommendationsEnabled = extras.second,
            nextcloudCalendarAvailable = nextcloudAvailable,
            message = message,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PostLoginSetupUiState(
            exportToDeviceContacts = seeded.exportToDeviceContacts,
            storedBirthdaysDestination = seeded.storedBirthdaysDestination,
            storedAnniversariesDestination = seeded.storedAnniversariesDestination,
            storedDatesOfDeathDestination = seeded.storedDatesOfDeathDestination,
            enableWebsiteLogos = seeded.enableWebsiteLogos,
            locationRecommendationsEnabled = seeded.locationRecommendationsEnabled,
            nextcloudCalendarAvailable = container.userPreferences.getCachedNextcloudCalendarAvailable(),
        ),
    )

    init {
        refreshNextcloudCalendarAvailability()
    }

    fun refreshNextcloudCalendarAvailability() {
        viewModelScope.launch {
            val credentials = container.credentialStore.getCredentials()
            if (credentials == null) {
                _nextcloudCalendarAvailable.value = false
                container.userPreferences.setCachedNextcloudCalendarAvailable(false)
                return@launch
            }
            var alreadyRetried = false
            while (true) {
                val available = runCatching {
                    withContext(Dispatchers.IO) {
                        NextcloudCapabilitiesService(credentials).isCalendarAppEnabled()
                    }
                }.getOrNull()
                if (available != null) {
                    _nextcloudCalendarAvailable.value = available
                    container.userPreferences.setCachedNextcloudCalendarAvailable(available)
                    return@launch
                }
                val cached = container.userPreferences.getCachedNextcloudCalendarAvailable()
                when (
                    PostLoginSetup.nextStepAfterCalendarProbe(
                        hasCachedValue = cached != null,
                        alreadyRetried = alreadyRetried,
                    )
                ) {
                    PostLoginSetup.CalendarProbeStep.KeepCached -> {
                        cached?.let { _nextcloudCalendarAvailable.value = it }
                        return@launch
                    }
                    PostLoginSetup.CalendarProbeStep.RetryOnce -> alreadyRetried = true
                    PostLoginSetup.CalendarProbeStep.LeaveDefault -> return@launch
                }
            }
        }
    }

    /** Same preference and export path as the Settings switch. */
    fun setExportToDeviceContacts(enabled: Boolean) {
        viewModelScope.launch {
            try {
                container.systemContactsExporter.setEnabled(enabled)
                _message.value = if (enabled) {
                    str(R.string.msg_contacts_will_appear)
                } else {
                    str(R.string.msg_contacts_removed_device)
                }
            } catch (e: CancellationException) {
                throw e
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

    /**
     * Writes one destination to birthdays, anniversaries, and dates of death.
     * Call this only after a required calendar grant. A denial must not call it.
     */
    fun setDatesDestination(destination: ContactDatesDestination) {
        viewModelScope.launch {
            var nextcloudFailed = false
            try {
                for ((kind, value) in PostLoginSetup.destinationsForAllKinds(destination)) {
                    val failed = container.contactDatesCalendarCoordinator.setKindDestination(kind, value)
                    nextcloudFailed = nextcloudFailed || failed
                }
                if (nextcloudFailed) {
                    _message.value = str(R.string.msg_nextcloud_calendar_failed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                // A denied calendar grant must leave the three stored keys unchanged.
            } catch (e: Exception) {
                _message.value = str(
                    R.string.msg_update_failed,
                    str(R.string.section_calendar),
                    e.message ?: str(R.string.msg_unknown_error),
                )
            }
        }
    }

    /** Same preference as the Settings website-logos switch. */
    fun setEnableWebsiteLogos(enabled: Boolean) {
        viewModelScope.launch {
            container.userPreferences.setEnableWebsiteLogos(enabled)
        }
    }

    /** Same preference as the Settings location-recommendations switch. */
    fun setLocationRecommendationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.userPreferences.setLocationRecommendationsEnabled(enabled)
        }
    }

    /** Marks setup complete. Only Done calls this, including when contacts access was denied. */
    fun finish(onFinished: () -> Unit) {
        viewModelScope.launch {
            container.userPreferences.setPostLoginSetupComplete(true)
            onFinished()
        }
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }
}
