package com.nextcloud.contacts.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.local.AddressInput
import com.nextcloud.contacts.data.local.ContactCategories
import com.nextcloud.contacts.data.local.ContactInput
import com.nextcloud.contacts.data.local.CustomFieldInput
import com.nextcloud.contacts.data.local.VCardSyntaxExtras
import com.nextcloud.contacts.data.local.VCardTypedFieldInput
import com.nextcloud.contacts.data.local.RESERVED_CUSTOM_FIELD_PROPERTY_NAMES
import com.nextcloud.contacts.data.local.LanguageOptions
import com.nextcloud.contacts.data.prefs.ContactEditFieldMode
import com.nextcloud.contacts.data.prefs.UserPreferences
import com.nextcloud.contacts.data.remote.CloudUser
import com.nextcloud.contacts.data.remote.NextcloudUserDirectory
import com.nextcloud.contacts.data.remote.PhotonGeocodingService
import com.nextcloud.contacts.data.remote.PlaceSuggestion
import com.nextcloud.contacts.data.remote.VCardMapper
import com.nextcloud.contacts.data.remote.VCardPhotoHelper
import com.nextcloud.contacts.ui.screens.ScalarVCardProperty
import com.nextcloud.contacts.sync.SyncWorker
import com.nextcloud.contacts.util.HttpStatuses
import com.nextcloud.contacts.platform.MessengerImMapping
import com.nextcloud.contacts.ui.components.NextcloudAvatarSupport
import com.nextcloud.contacts.ui.navigation.ContactEditPrefill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PlaceAutocompleteTarget {
    data object None : PlaceAutocompleteTarget()
    data class AddressStreet(val index: Int) : PlaceAutocompleteTarget()
    data object Birthplace : PlaceAutocompleteTarget()
    data object DeathPlace : PlaceAutocompleteTarget()
}

data class PlaceAutocompleteState(
    val enabled: Boolean = true,
    val photonBaseUrl: String = UserPreferences.DEFAULT_PHOTON_BASE_URL,
    val target: PlaceAutocompleteTarget = PlaceAutocompleteTarget.None,
    val query: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hideSuggestions: Boolean = false,
)

data class CloudUserSearchState(
    val query: String = "",
    val users: List<CloudUser> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val canSearch: Boolean = false,
    val exampleCloudId: String = NextcloudUserDirectory.exampleCloudId(null),
    val avatarAuthHeader: String? = null,
)

/** Missing Nextcloud fields for one federated user, offered together. */
data class NextcloudCrossLinkPrompt(
    val cloudId: String,
    val profileUrl: String,
    val addSocialProfile: Boolean,
    val addInstantMessage: Boolean,
    val addFederatedCloudId: Boolean,
)

data class LineManagerCandidate(
    val id: Long,
    val uid: String,
    val displayName: String,
    val subtitle: String,
)

data class ContactEditState(
    val prefix: String = "",
    val givenName: String = "",
    val middleName: String = "",
    val familyName: String = "",
    val suffix: String = "",
    val nickname: String = "",
    val phoneticGiven: String = "",
    val phoneticFamily: String = "",
    val organization: String = "",
    val title: String = "",
    val role: String = "",
    val gender: String = "",
    val birthday: String = "",
    val birthplace: String = "",
    val anniversary: String = "",
    val deathDate: String = "",
    val deathPlace: String = "",
    val categories: String = "",
    val notes: String = "",
    val notesMarkdown: Boolean = false,
    /** Local only. Not written into the vCard. */
    val editFieldSyntaxEnabled: Boolean = false,
    val timeZone: String = "",
    val managersName: String = "",
    val managersUid: String = "",
    val relationshipToYou: String = "",
    val phones: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.phone()),
    val emails: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.email()),
    val addresses: List<AddressInput> = listOf(AddressInput()),
    val urls: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.url()),
    val imAccounts: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.im()),
    val socialProfiles: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.social()),
    val cloudIds: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.cloud()),
    val languages: List<VCardTypedFieldInput> = listOf(
        VCardTypedFieldInput(type = "", value = "", propertyName = "LANG"),
    ),
    val relatedNames: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.related()),
    val customFields: List<CustomFieldInput> = emptyList(),
    val scalarVcardExtras: Map<String, VCardSyntaxExtras> = emptyMap(),
    val photoBytes: ByteArray? = null,
    /** PHOTO text kept for a file-backed photo until that line is edited. */
    val photoVcardBaselineRaw: String? = null,
    val hasExistingPhoto: Boolean = false,
    val removePhoto: Boolean = false,
    val isLoading: Boolean = false,
    /** False while an existing contact is still being loaded into the form. */
    val isReady: Boolean = true,
    val isNew: Boolean = true,
    val showMoreNameFields: Boolean = false,
    val showMoreOrganizationFields: Boolean = false,
    val expandedAddressIndices: Set<Int> = emptySet(),
    /** IM rows that stay visible in Simple/Work after adding a Nextcloud account. */
    val pinnedVisibleImIndices: Set<Int> = emptySet(),
    val pinnedVisibleSocialIndices: Set<Int> = emptySet(),
    /** Revealed for this edit session. Not persisted. */
    val sessionRevealedHiddenFields: Set<ContactEditHiddenField> = emptySet(),
) {
    val displayName: String
        get() = listOf(prefix, givenName, middleName, familyName, suffix)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { nickname.ifBlank { organization } }

    val showPhoto: Boolean
        get() = (photoBytes != null || hasExistingPhoto) && !removePhoto
}

class ContactEditViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    val contactId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
    private val container = (application as ContactsApp).container
    private val repository = container.contactRepository

    private val _state = MutableStateFlow(
        ContactEditState(
            isNew = contactId == 0L,
            isReady = contactId == 0L,
            // Known before the first frame, so the avatar does not flash the empty placeholder.
            hasExistingPhoto = contactId != 0L && repository.hasPhoto(contactId),
        ),
    )
    val state: StateFlow<ContactEditState> = _state.asStateFlow()

    private val _fieldMode = MutableStateFlow(ContactEditFieldMode.SIMPLE)
    val fieldMode: StateFlow<ContactEditFieldMode> = _fieldMode.asStateFlow()

    private val _settingsFieldSyntaxEnabled = MutableStateFlow(false)
    val settingsFieldSyntaxEnabled: StateFlow<Boolean> = _settingsFieldSyntaxEnabled.asStateFlow()
    private var editFieldSyntaxTouched = false

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _existingGroups = MutableStateFlow<List<String>>(emptyList())
    val existingGroups: StateFlow<List<String>> = _existingGroups.asStateFlow()

    private val credentials = container.credentialStore.getCredentials()
    private val userDirectory = credentials?.let { NextcloudUserDirectory(it) }

    private val _cloudUserSearch = MutableStateFlow(
        CloudUserSearchState(
            canSearch = userDirectory != null,
            exampleCloudId = NextcloudUserDirectory.exampleCloudId(credentials),
            avatarAuthHeader = userDirectory?.avatarAuthorizationHeader(),
        ),
    )
    val cloudUserSearch: StateFlow<CloudUserSearchState> = _cloudUserSearch.asStateFlow()
    private var cloudUserSearchJob: Job? = null

    private val photonService = container.photonGeocodingService
    private val _placeAutocomplete = MutableStateFlow(PlaceAutocompleteState())
    val placeAutocomplete: StateFlow<PlaceAutocompleteState> = _placeAutocomplete.asStateFlow()
    private var placeAutocompleteJob: Job? = null

    private val _crossLinkPrompt = MutableStateFlow<NextcloudCrossLinkPrompt?>(null)
    val crossLinkPrompt: StateFlow<NextcloudCrossLinkPrompt?> = _crossLinkPrompt.asStateFlow()
    private var lastOfferedCrossLinkKey: String? = null

    private val _lineManagerCandidates = MutableStateFlow<List<LineManagerCandidate>>(emptyList())
    val lineManagerCandidates: StateFlow<List<LineManagerCandidate>> = _lineManagerCandidates.asStateFlow()

    val cloudIdExample: String
        get() = _cloudUserSearch.value.exampleCloudId

    val socialNextcloudExample: String
        get() = NextcloudAvatarSupport.exampleProfileUrl(credentials)

    init {
        viewModelScope.launch {
            container.userPreferences.contactEditFieldMode.collect { mode ->
                _fieldMode.value = mode
            }
        }
        viewModelScope.launch {
            container.userPreferences.fieldSyntaxEnabled.collect { enabled ->
                _settingsFieldSyntaxEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            combine(
                container.userPreferences.locationRecommendationsEnabled,
                container.userPreferences.photonBaseUrl,
            ) { enabled, baseUrl ->
                enabled to PhotonGeocodingService.normalizeBaseUrl(baseUrl)
            }.collect { (enabled, baseUrl) ->
                _placeAutocomplete.value = _placeAutocomplete.value.copy(
                    enabled = enabled,
                    photonBaseUrl = baseUrl,
                )
                if (!enabled) {
                    clearPlaceAutocomplete()
                }
            }
        }
        viewModelScope.launch {
            _existingGroups.value = withContext(Dispatchers.IO) {
                repository.allCategories()
            }
        }
        viewModelScope.launch {
            repository.observeLineManagerCandidates().collect { rows ->
                _lineManagerCandidates.value = rows
                    .asSequence()
                    .filter { it.id != contactId }
                    .map { row ->
                        val subtitle = listOf(row.organization, row.email)
                            .firstOrNull { !it.isNullOrBlank() }
                            .orEmpty()
                        LineManagerCandidate(
                            id = row.id,
                            uid = row.uid,
                            displayName = row.displayName,
                            subtitle = subtitle,
                        )
                    }
                    .sortedBy { it.displayName.lowercase() }
                    .toList()
            }
        }
        if (contactId != 0L) {
            viewModelScope.launch {
                val contact = repository.getContact(contactId) ?: return@launch
                val input = VCardMapper.inputFromContact(contact)
                val storedPhotoBytes = withContext(Dispatchers.IO) {
                    repository.getPhotoBytes(contactId)
                }
                val photoExtra = input.scalarVcardExtras["PHOTO"]
                val photoBaseline = if (
                    photoExtra?.rawValue.isNullOrBlank() &&
                    storedPhotoBytes != null
                ) {
                    com.nextcloud.contacts.data.remote.VCardPhotoHelper.baselineRawValue(
                        storedPhotoBytes,
                        photoExtra,
                    )
                } else {
                    null
                }
                val loaded = input.toEditState(
                    hasExistingPhoto = repository.hasPhoto(contactId),
                    isNew = false,
                    showMoreNameFields = input.hasExtendedNameFields(),
                    showMoreOrganizationFields = input.title.isNotBlank() || input.role.isNotBlank(),
                ).copy(
                    photoBytes = storedPhotoBytes,
                    photoVcardBaselineRaw = photoBaseline,
                )
                _state.value = if (editFieldSyntaxTouched) {
                    loaded.copy(editFieldSyntaxEnabled = _state.value.editFieldSyntaxEnabled)
                } else {
                    loaded.copy(editFieldSyntaxEnabled = contact.contact.editFieldSyntaxEnabled)
                }
            }
        } else {
            ContactEditPrefill.input?.let { prefill ->
                _state.value = prefill.toEditState(
                    hasExistingPhoto = prefill.photoBytes != null,
                    isNew = true,
                    showMoreNameFields = prefill.hasExtendedNameFields(),
                    showMoreOrganizationFields = prefill.title.isNotBlank() || prefill.role.isNotBlank(),
                )
                ContactEditPrefill.input = null
            }
        }
    }

    fun addableHiddenFields(): List<ContactEditHiddenField> =
        _state.value.addableHiddenFields(_fieldMode.value)

    fun revealHiddenField(field: ContactEditHiddenField) = update {
        applyRevealHiddenField(field)
    }

    fun toggleMoreNameFields() = update { copy(showMoreNameFields = !showMoreNameFields) }
    fun toggleMoreOrganizationFields() = update { copy(showMoreOrganizationFields = !showMoreOrganizationFields) }
    fun updatePrefix(value: String) = update { copy(prefix = value) }
    fun updateGivenName(value: String) = update { copy(givenName = value) }
    fun updateMiddleName(value: String) = update { copy(middleName = value) }
    fun updateFamilyName(value: String) = update { copy(familyName = value) }
    fun updateSuffix(value: String) = update { copy(suffix = value) }
    fun updateNickname(value: String) = update { copy(nickname = value) }
    fun updatePhoneticGiven(value: String) = update { copy(phoneticGiven = value) }
    fun updatePhoneticFamily(value: String) = update { copy(phoneticFamily = value) }
    fun updateOrganization(value: String) = update { copy(organization = value) }
    fun updateTitle(value: String) = update { copy(title = value) }
    fun updateRole(value: String) = update { copy(role = value) }
    fun updateGender(value: String) = update { copy(gender = value) }
    fun updateBirthday(value: String) = update { copy(birthday = value) }
    fun updateBirthplace(value: String) = update { copy(birthplace = value) }
    fun updateAnniversary(value: String) = update { copy(anniversary = value) }
    fun updateDeathDate(value: String) = update { copy(deathDate = value) }
    fun updateDeathPlace(value: String) = update { copy(deathPlace = value) }
    fun updateCategories(value: String) = update { copy(categories = value) }

    fun addCategory(group: String) = update {
        copy(categories = ContactCategories.add(categories, group))
    }

    fun removeCategory(group: String) = update {
        copy(categories = ContactCategories.remove(categories, group))
    }

    fun updateNotes(value: String) = update { copy(notes = value) }
    fun updateNotesMarkdown(value: Boolean) = update { copy(notesMarkdown = value) }

    fun toggleEditFieldSyntax() {
        val next = !_state.value.editFieldSyntaxEnabled
        editFieldSyntaxTouched = true
        update { copy(editFieldSyntaxEnabled = next) }
        if (contactId == 0L) return
        viewModelScope.launch {
            repository.setEditFieldSyntaxEnabled(contactId, next)
        }
    }
    fun updateTimeZone(value: String) = update { copy(timeZone = value) }
    fun setLineManager(candidate: LineManagerCandidate) = update {
        copy(managersName = candidate.displayName, managersUid = candidate.uid)
    }
    fun clearLineManager() = update { copy(managersName = "", managersUid = "") }
    fun updateManagersName(value: String) = update { copy(managersName = value) }
    fun updateManagersUid(value: String) = update { copy(managersUid = value) }
    fun updateRelationshipToYou(value: String) = update { copy(relationshipToYou = value) }

    fun updatePhone(index: Int, value: String) = update {
        copy(phones = updateTypedValue(phones, index, value))
    }

    fun updatePhoneField(index: Int, field: VCardTypedFieldInput) = update {
        copy(phones = replaceTypedField(phones, index, field))
    }
    fun updatePhoneType(index: Int, type: String) = update {
        copy(phones = updateTypedType(phones, index, type))
    }
    fun updatePhoneSyntax(index: Int, syntax: VCardSyntaxExtras) = update {
        copy(phones = updateTypedSyntax(phones, index, syntax))
    }
    fun addPhone() = update { copy(phones = phones + VCardTypedFieldInput.phone()) }
    fun removePhone(index: Int) = update { copy(phones = removeIfMultiple(phones, index)) }

    fun updateEmail(index: Int, value: String) = update {
        copy(emails = updateTypedValue(emails, index, value))
    }
    fun updateEmailType(index: Int, type: String) = update {
        copy(emails = updateTypedType(emails, index, type))
    }
    fun updateEmailSyntax(index: Int, syntax: VCardSyntaxExtras) = update {
        copy(emails = updateTypedSyntax(emails, index, syntax))
    }
    fun updateEmailField(index: Int, field: VCardTypedFieldInput) = update {
        copy(emails = replaceTypedField(emails, index, field))
    }
    fun addEmail() = update { copy(emails = emails + VCardTypedFieldInput.email()) }
    fun removeEmail(index: Int) = update { copy(emails = removeIfMultiple(emails, index)) }

    fun updateAddress(index: Int, block: AddressInput.() -> AddressInput) = update {
        val updated = addresses.toMutableList()
        if (index in updated.indices) updated[index] = updated[index].block()
        copy(addresses = updated)
    }

    fun addAddress() = update { copy(addresses = addresses + AddressInput()) }

    fun removeAddress(index: Int) = update {
        copy(
            addresses = removeIfMultiple(addresses, index),
            expandedAddressIndices = remappedExpandedIndices(expandedAddressIndices, index, addresses.size),
        )
    }

    fun toggleAddressExpanded(index: Int) = update {
        copy(
            expandedAddressIndices = if (index in expandedAddressIndices) {
                expandedAddressIndices - index
            } else {
                expandedAddressIndices + index
            },
        )
    }

    fun updateUrl(index: Int, value: String) = update {
        copy(urls = updateTypedValue(urls, index, value))
    }
    fun updateUrlType(index: Int, type: String) = update {
        copy(urls = updateTypedType(urls, index, type))
    }
    fun updateUrlSyntax(index: Int, syntax: VCardSyntaxExtras) = update {
        copy(urls = updateTypedSyntax(urls, index, syntax))
    }
    fun updateUrlField(index: Int, field: VCardTypedFieldInput) = update {
        copy(urls = replaceTypedField(urls, index, field))
    }
    fun addUrl() = update { copy(urls = urls + VCardTypedFieldInput.url()) }
    fun removeUrl(index: Int) = update { copy(urls = removeIfMultiple(urls, index)) }

    fun updateIm(index: Int, value: String) = update {
        copy(imAccounts = updateTypedValue(imAccounts, index, value))
    }
    fun updateImType(index: Int, type: String) = update {
        copy(imAccounts = updateTypedType(imAccounts, index, type))
    }
    fun updateImSyntax(index: Int, syntax: VCardSyntaxExtras) = update {
        copy(imAccounts = updateTypedSyntax(imAccounts, index, syntax))
    }
    fun updateImField(index: Int, field: VCardTypedFieldInput) = update {
        copy(imAccounts = replaceTypedField(imAccounts, index, field))
    }
    fun addIm() = update { copy(imAccounts = imAccounts + VCardTypedFieldInput.im()) }
    fun removeIm(index: Int) = update {
        val previousSize = imAccounts.size
        copy(
            imAccounts = removeIfMultiple(imAccounts, index),
            pinnedVisibleImIndices = remappedExpandedIndices(pinnedVisibleImIndices, index, previousSize),
        )
    }

    fun updateSocialProfile(index: Int, value: String) = update {
        copy(socialProfiles = updateTypedValue(socialProfiles, index, value))
    }

    fun updateSocialProfileType(index: Int, type: String) = update {
        copy(socialProfiles = updateTypedType(socialProfiles, index, type))
    }

    fun updateSocialProfileSyntax(index: Int, syntax: VCardSyntaxExtras) = update {
        copy(socialProfiles = updateTypedSyntax(socialProfiles, index, syntax))
    }
    fun updateSocialProfileField(index: Int, field: VCardTypedFieldInput) = update {
        copy(socialProfiles = replaceTypedField(socialProfiles, index, field))
    }

    fun addSocialProfile() = update { copy(socialProfiles = socialProfiles + VCardTypedFieldInput.social()) }
    fun removeSocialProfile(index: Int) = update {
        val previousSize = socialProfiles.size
        copy(
            socialProfiles = removeIfMultiple(socialProfiles, index),
            pinnedVisibleSocialIndices = remappedExpandedIndices(pinnedVisibleSocialIndices, index, previousSize),
        )
    }

    /** Writes social, IM, and cloud id for one user. Does not open the cross-link dialog. */
    fun applyNextcloudAccountFromPicker(user: CloudUser) {
        val cloudId = user.cloudId.trim()
        if (cloudId.isBlank()) return
        val profileUrl = NextcloudAvatarSupport.profileUrlForLocalUser(user.id, credentials)
            ?: NextcloudAvatarSupport.profileUrl(cloudId, credentials)
            ?: return

        update {
            val social = socialProfiles.toMutableList()
            val im = imAccounts.toMutableList()
            val clouds = cloudIds.toMutableList()
            val pinnedSocial = pinnedVisibleSocialIndices.toMutableSet()
            val pinnedIm = pinnedVisibleImIndices.toMutableSet()

            if (!matchesNextcloudSocial(social, cloudId, profileUrl)) {
                val blankNc = social.indexOfFirst {
                    it.type.equals("nextcloud", ignoreCase = true) && it.value.isBlank()
                }
                val blankAny = social.indexOfFirst { it.value.isBlank() }
                val idx = when {
                    blankNc >= 0 -> blankNc.also {
                        social[it] = VCardTypedFieldInput.social(type = "nextcloud", value = profileUrl)
                    }
                    blankAny >= 0 -> blankAny.also {
                        social[it] = VCardTypedFieldInput.social(type = "nextcloud", value = profileUrl)
                    }
                    else -> social.size.also {
                        social.add(VCardTypedFieldInput.social(type = "nextcloud", value = profileUrl))
                    }
                }
                pinnedSocial.add(idx)
            }

            if (!matchesNextcloudIm(im, cloudId)) {
                val blankNc = im.indexOfFirst {
                    MessengerImMapping.isNextcloudImType(it.type) && it.value.isBlank()
                }
                val blankAny = im.indexOfFirst { it.value.isBlank() }
                val idx = when {
                    blankNc >= 0 -> blankNc.also {
                        im[it] = VCardTypedFieldInput.im(type = MessengerImMapping.NEXTCLOUD_IM_TYPE, value = cloudId)
                    }
                    blankAny >= 0 -> blankAny.also {
                        im[it] = VCardTypedFieldInput.im(type = MessengerImMapping.NEXTCLOUD_IM_TYPE, value = cloudId)
                    }
                    else -> im.size.also {
                        im.add(VCardTypedFieldInput.im(type = MessengerImMapping.NEXTCLOUD_IM_TYPE, value = cloudId))
                    }
                }
                pinnedIm.add(idx)
            }

            if (!matchesFederatedCloudId(clouds, cloudId)) {
                val blankHome = clouds.indexOfFirst {
                    it.type.equals("home", ignoreCase = true) && it.value.isBlank()
                }
                val blankAny = clouds.indexOfFirst { it.value.isBlank() }
                when {
                    blankHome >= 0 -> clouds[blankHome] = VCardTypedFieldInput.cloud(type = "home", value = cloudId)
                    blankAny >= 0 -> clouds[blankAny] = clouds[blankAny].copy(value = cloudId)
                    else -> clouds += VCardTypedFieldInput.cloud(type = "home", value = cloudId)
                }
            }

            copy(
                socialProfiles = social,
                imAccounts = im,
                cloudIds = clouds,
                pinnedVisibleSocialIndices = pinnedSocial,
                pinnedVisibleImIndices = pinnedIm,
            )
        }
    }

    fun setSocialNextcloudFromPicker(index: Int, user: CloudUser) {
        val profileUrl = NextcloudAvatarSupport.profileUrlForLocalUser(user.id, credentials)
            ?: NextcloudAvatarSupport.profileUrl(user.cloudId, credentials)
            ?: return
        update {
            val list = socialProfiles.toMutableList()
            if (index in list.indices) {
                list[index] = VCardTypedFieldInput.social(type = "nextcloud", value = profileUrl)
            }
            copy(socialProfiles = list)
        }
        offerAlsoAddMissingNextcloudFields(profileUrl)
    }

    fun setImNextcloudFromPicker(index: Int, user: CloudUser) {
        val cloudId = user.cloudId.trim()
        if (cloudId.isBlank()) return
        update {
            val list = imAccounts.toMutableList()
            if (index in list.indices) {
                list[index] = VCardTypedFieldInput.im(
                    type = MessengerImMapping.NEXTCLOUD_IM_TYPE,
                    value = cloudId,
                )
            }
            copy(imAccounts = list)
        }
        offerAlsoAddMissingNextcloudFields(cloudId)
    }

    fun updateCloudId(index: Int, value: String) = update {
        copy(cloudIds = updateTypedValue(cloudIds, index, value))
    }

    fun updateCloudIdType(index: Int, type: String) = update {
        copy(cloudIds = updateTypedType(cloudIds, index, type))
    }

    fun updateCloudIdSyntax(index: Int, syntax: VCardSyntaxExtras) = update {
        copy(cloudIds = updateTypedSyntax(cloudIds, index, syntax))
    }
    fun updateCloudIdField(index: Int, field: VCardTypedFieldInput) = update {
        copy(cloudIds = replaceTypedField(cloudIds, index, field))
    }

    fun addCloudId() = update { copy(cloudIds = cloudIds + VCardTypedFieldInput.cloud()) }
    fun removeCloudId(index: Int) = update { copy(cloudIds = removeIfMultiple(cloudIds, index)) }

    fun setCloudIdFromPicker(index: Int, cloudId: String) {
        update { copy(cloudIds = updateTypedValue(cloudIds, index, cloudId)) }
        offerAlsoAddMissingNextcloudFields(cloudId)
    }

    fun confirmCrossLink() {
        val prompt = _crossLinkPrompt.value ?: return
        _crossLinkPrompt.value = null
        if (prompt.addSocialProfile) addOrFillNextcloudSocial(prompt.profileUrl)
        if (prompt.addInstantMessage) addOrFillNextcloudIm(prompt.cloudId)
        if (prompt.addFederatedCloudId) addOrFillFederatedCloudId(prompt.cloudId)
    }

    fun dismissCrossLink() {
        _crossLinkPrompt.value = null
    }

    fun resetCloudUserSearch() {
        cloudUserSearchJob?.cancel()
        _cloudUserSearch.value = CloudUserSearchState(
            canSearch = userDirectory != null,
            exampleCloudId = NextcloudUserDirectory.exampleCloudId(credentials),
            avatarAuthHeader = userDirectory?.avatarAuthorizationHeader(),
        )
        if (userDirectory != null) {
            searchCloudUsers("")
        }
    }

    fun onAddressStreetQueryChange(index: Int, query: String) {
        searchPlaces(PlaceAutocompleteTarget.AddressStreet(index), query)
    }

    fun onPlaceFieldQueryChange(target: PlaceAutocompleteTarget, query: String) {
        searchPlaces(target, query)
    }

    fun dismissPlaceAutocompleteSuggestions() {
        placeAutocompleteJob?.cancel()
        _placeAutocomplete.value = _placeAutocomplete.value.copy(
            target = PlaceAutocompleteTarget.None,
            query = "",
            suggestions = emptyList(),
            isLoading = false,
            error = null,
            hideSuggestions = true,
        )
    }

    fun applyAddressSuggestion(index: Int, suggestion: PlaceSuggestion) {
        val current = _state.value.addresses.getOrNull(index) ?: return
        updateAddress(index) {
            suggestion.toAddressInput(type = type, poBox = poBox, existingGeo = geo)
        }
        _placeAutocomplete.value = _placeAutocomplete.value.copy(
            target = PlaceAutocompleteTarget.None,
            query = "",
            suggestions = emptyList(),
            isLoading = false,
            error = null,
            hideSuggestions = true,
        )
        if (!current.hasExtraFields() && suggestion.hasDetailFields()) {
            toggleAddressExpanded(index)
        }
    }

    fun applyPlaceSuggestion(target: PlaceAutocompleteTarget, suggestion: PlaceSuggestion) {
        val value = suggestion.toSingleLinePlace()
        when (target) {
            PlaceAutocompleteTarget.Birthplace -> updateBirthplace(value)
            PlaceAutocompleteTarget.DeathPlace -> updateDeathPlace(value)
            else -> Unit
        }
        _placeAutocomplete.value = _placeAutocomplete.value.copy(
            target = PlaceAutocompleteTarget.None,
            query = "",
            suggestions = emptyList(),
            isLoading = false,
            error = null,
            hideSuggestions = true,
        )
    }

    private fun searchPlaces(target: PlaceAutocompleteTarget, query: String) {
        val prefs = _placeAutocomplete.value
        if (!prefs.enabled) return

        placeAutocompleteJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _placeAutocomplete.value = prefs.copy(
                target = target,
                query = query,
                suggestions = emptyList(),
                isLoading = false,
                error = null,
                hideSuggestions = false,
            )
            return
        }

        _placeAutocomplete.value = prefs.copy(
            target = target,
            query = query,
            isLoading = true,
            error = null,
            hideSuggestions = false,
            suggestions = emptyList(),
        )
        placeAutocompleteJob = viewModelScope.launch {
            delay(300)
            try {
                val baseUrl = _placeAutocomplete.value.photonBaseUrl
                val results = withContext(Dispatchers.IO) {
                    photonService.search(baseUrl, trimmed)
                }
                if (_placeAutocomplete.value.target != target ||
                    _placeAutocomplete.value.query != query
                ) {
                    return@launch
                }
                _placeAutocomplete.value = _placeAutocomplete.value.copy(
                    suggestions = results,
                    isLoading = false,
                    error = null,
                )
            } catch (e: Exception) {
                if (_placeAutocomplete.value.target != target ||
                    _placeAutocomplete.value.query != query
                ) {
                    return@launch
                }
                _placeAutocomplete.value = _placeAutocomplete.value.copy(
                    suggestions = emptyList(),
                    isLoading = false,
                    error = placeSearchError(e),
                )
            }
        }
    }

    private fun clearPlaceAutocomplete() {
        placeAutocompleteJob?.cancel()
        _placeAutocomplete.value = _placeAutocomplete.value.copy(
            target = PlaceAutocompleteTarget.None,
            query = "",
            suggestions = emptyList(),
            isLoading = false,
            error = null,
            hideSuggestions = false,
        )
    }

    fun searchCloudUsers(query: String) {
        val directory = userDirectory
        if (directory == null) {
            _cloudUserSearch.value = _cloudUserSearch.value.copy(
                query = query,
                users = emptyList(),
                isLoading = false,
                canSearch = false,
                avatarAuthHeader = null,
                error = getApplication<Application>().getString(R.string.sign_in_to_pick_nextcloud_user),
            )
            return
        }
        cloudUserSearchJob?.cancel()
        cloudUserSearchJob = viewModelScope.launch {
            _cloudUserSearch.value = _cloudUserSearch.value.copy(
                query = query,
                isLoading = true,
                error = null,
                canSearch = true,
                avatarAuthHeader = directory.avatarAuthorizationHeader(),
            )
            delay(250)
            try {
                val login = container.credentialStore.getCredentials()?.loginName.orEmpty()
                val selfName = getApplication<Application>().getString(R.string.you_named, login)
                val users = withContext(Dispatchers.IO) { directory.searchUsers(query, selfName) }
                _cloudUserSearch.value = _cloudUserSearch.value.copy(
                    users = users,
                    isLoading = false,
                    error = null,
                )
            } catch (e: Exception) {
                _cloudUserSearch.value = _cloudUserSearch.value.copy(
                    users = emptyList(),
                    isLoading = false,
                    error = userSearchError(e),
                )
            }
        }
    }

    private fun placeSearchError(error: Exception): String {
        val message = error.message.orEmpty()
        val app = getApplication<Application>()
        return when {
            message.isBlank() || message.startsWith("Photon returned") ->
                app.getString(R.string.could_not_load_places)
            else -> message
        }
    }

    private fun userSearchError(error: Exception): String {
        val message = error.message.orEmpty()
        val http = HttpStatuses.codeString(message)
        val app = getApplication<Application>()
        return when {
            message.contains("Invalid server URL") -> app.getString(R.string.invalid_server_url)
            http != null -> app.getString(R.string.user_search_failed, http)
            message.isBlank() -> app.getString(R.string.could_not_load_users)
            else -> message
        }
    }

    private fun offerAlsoAddMissingNextcloudFields(raw: String) {
        val pair = resolveCloudAndProfile(raw) ?: return
        offerAlsoAddMissingNextcloudFields(pair.first, pair.second)
    }

    private fun offerAlsoAddMissingNextcloudFields(cloudId: String, profileUrl: String) {
        val addSocial = !hasMatchingNextcloudSocial(cloudId, profileUrl)
        val addIm = !hasMatchingNextcloudIm(cloudId)
        val addCloudId = !hasMatchingFederatedCloudId(cloudId)
        if (!addSocial && !addIm && !addCloudId) return
        showCrossLink(
            NextcloudCrossLinkPrompt(
                cloudId = cloudId,
                profileUrl = profileUrl,
                addSocialProfile = addSocial,
                addInstantMessage = addIm,
                addFederatedCloudId = addCloudId,
            ),
        )
    }

    private fun resolveCloudAndProfile(raw: String): Pair<String, String>? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        NextcloudAvatarSupport.cloudIdFromProfileUrl(trimmed)?.let { cloudId ->
            val url = NextcloudAvatarSupport.profileUrl(cloudId, credentials) ?: trimmed
            return cloudId to url
        }
        if (NextcloudAvatarSupport.looksLikeCloudId(trimmed)) {
            val url = NextcloudAvatarSupport.profileUrl(trimmed, credentials) ?: return null
            return trimmed to url
        }
        return null
    }

    private fun hasMatchingNextcloudSocial(cloudId: String, profileUrl: String): Boolean =
        matchesNextcloudSocial(_state.value.socialProfiles, cloudId, profileUrl)

    private fun hasMatchingNextcloudIm(cloudId: String): Boolean =
        matchesNextcloudIm(_state.value.imAccounts, cloudId)

    private fun hasMatchingFederatedCloudId(cloudId: String): Boolean =
        matchesFederatedCloudId(_state.value.cloudIds, cloudId)

    private fun matchesNextcloudSocial(
        socialProfiles: List<VCardTypedFieldInput>,
        cloudId: String,
        profileUrl: String,
    ): Boolean = socialProfiles.any { field ->
        val type = field.type
        val value = field.value
        if (!type.equals("nextcloud", ignoreCase = true)) return@any false
        if (value.isBlank()) return@any false
        val trimmed = value.trim()
        trimmed.equals(profileUrl, ignoreCase = true) ||
            resolveCloudAndProfile(trimmed)?.first?.equals(cloudId, ignoreCase = true) == true
    }

    private fun matchesNextcloudIm(
        imAccounts: List<VCardTypedFieldInput>,
        cloudId: String,
    ): Boolean = imAccounts.any { field ->
        MessengerImMapping.isMatchingNextcloudIm(
            imType = field.type,
            imValue = field.value,
            cloudId = cloudId,
            cloudIdFromProfileUrl = NextcloudAvatarSupport::cloudIdFromProfileUrl,
        )
    }

    private fun matchesFederatedCloudId(
        cloudIds: List<VCardTypedFieldInput>,
        cloudId: String,
    ): Boolean = cloudIds.any { field ->
        val value = field.value
        if (value.isBlank()) return@any false
        value.trim().equals(cloudId.trim(), ignoreCase = true) ||
            resolveCloudAndProfile(value)?.first?.equals(cloudId.trim(), ignoreCase = true) == true
    }

    private fun showCrossLink(prompt: NextcloudCrossLinkPrompt) {
        val key = buildString {
            append(prompt.cloudId.lowercase())
            if (prompt.addSocialProfile) append(":s")
            if (prompt.addInstantMessage) append(":i")
            if (prompt.addFederatedCloudId) append(":c")
        }
        if (lastOfferedCrossLinkKey == key && _crossLinkPrompt.value != null) return
        lastOfferedCrossLinkKey = key
        _crossLinkPrompt.value = prompt
    }

    private fun addOrFillNextcloudSocial(profileUrl: String) {
        update {
            val list = socialProfiles.toMutableList()
            val blankNc = list.indexOfFirst {
                it.type.equals("nextcloud", ignoreCase = true) && it.value.isBlank()
            }
            val blankAny = list.indexOfFirst { it.value.isBlank() }
            when {
                blankNc >= 0 -> list[blankNc] = VCardTypedFieldInput.social(type = "nextcloud", value = profileUrl)
                blankAny >= 0 -> list[blankAny] = VCardTypedFieldInput.social(type = "nextcloud", value = profileUrl)
                else -> list += VCardTypedFieldInput.social(type = "nextcloud", value = profileUrl)
            }
            copy(socialProfiles = list)
        }
    }

    private fun addOrFillNextcloudIm(cloudId: String) {
        update {
            val list = imAccounts.toMutableList()
            val blankNc = list.indexOfFirst {
                MessengerImMapping.isNextcloudImType(it.type) && it.value.isBlank()
            }
            val blankAny = list.indexOfFirst { it.value.isBlank() }
            when {
                blankNc >= 0 -> list[blankNc] = VCardTypedFieldInput.im(
                    type = MessengerImMapping.NEXTCLOUD_IM_TYPE,
                    value = cloudId,
                )
                blankAny >= 0 -> list[blankAny] = VCardTypedFieldInput.im(
                    type = MessengerImMapping.NEXTCLOUD_IM_TYPE,
                    value = cloudId,
                )
                else -> list += VCardTypedFieldInput.im(
                    type = MessengerImMapping.NEXTCLOUD_IM_TYPE,
                    value = cloudId,
                )
            }
            copy(imAccounts = list)
        }
    }

    private fun addOrFillFederatedCloudId(cloudId: String) {
        update {
            val list = cloudIds.toMutableList()
            val blankHome = list.indexOfFirst { it.type.equals("home", ignoreCase = true) && it.value.isBlank() }
            val blankAny = list.indexOfFirst { it.value.isBlank() }
            when {
                blankHome >= 0 -> list[blankHome] = VCardTypedFieldInput.cloud(type = "home", value = cloudId)
                blankAny >= 0 -> list[blankAny] = list[blankAny].copy(value = cloudId)
                else -> list += VCardTypedFieldInput.cloud(type = "home", value = cloudId)
            }
            copy(cloudIds = list)
        }
    }

    fun updateLanguage(index: Int, value: String) = update {
        copy(languages = updateTypedValue(languages, index, LanguageOptions.normalize(value)))
    }

    fun updateLanguageSyntax(index: Int, syntax: VCardSyntaxExtras) = update {
        copy(languages = updateTypedSyntax(languages, index, syntax))
    }
    fun updateLanguageField(index: Int, field: VCardTypedFieldInput) = update {
        copy(languages = replaceTypedField(languages, index, field))
    }

    fun addLanguage() = update {
        copy(
            languages = languages + VCardTypedFieldInput(type = "", value = "", propertyName = "LANG"),
        )
    }
    fun removeLanguage(index: Int) = update { copy(languages = removeIfMultiple(languages, index)) }

    fun updateRelated(index: Int, value: String) = update {
        copy(relatedNames = updateTypedValue(relatedNames, index, value))
    }
    fun updateRelatedType(index: Int, type: String) = update {
        copy(relatedNames = updateTypedType(relatedNames, index, type))
    }
    fun updateRelatedSyntax(index: Int, syntax: VCardSyntaxExtras) = update {
        copy(relatedNames = updateTypedSyntax(relatedNames, index, syntax))
    }
    fun updateRelatedField(index: Int, field: VCardTypedFieldInput) = update {
        copy(relatedNames = replaceTypedField(relatedNames, index, field))
    }
    fun addRelated() = update { copy(relatedNames = relatedNames + VCardTypedFieldInput.related()) }
    fun removeRelated(index: Int) = update { copy(relatedNames = removeIfMultiple(relatedNames, index)) }

    fun updateScalarVcardExtras(property: String, extras: VCardSyntaxExtras) =
        updateScalarVcardProperty(property, extras)

    fun updateScalarVcardProperty(property: String, extras: VCardSyntaxExtras) = update {
        val key = property.trim().uppercase()
        val next = scalarVcardExtras.toMutableMap()
        if (!extras.hasPersistableMetadata() && extras.rawValue.isBlank()) {
            next.remove(key)
        } else {
            next[key] = extras
        }
        var updated = copy(scalarVcardExtras = next)
        updated = when (key) {
            "FN" -> updated
            "N" -> updated.withStructuredNameFrom(extras.rawValue)
            "NICKNAME" -> updated.copy(nickname = extras.rawValue)
            "X-PHONETIC-FIRST-NAME" -> updated.copy(phoneticGiven = extras.rawValue)
            "X-PHONETIC-LAST-NAME" -> updated.copy(phoneticFamily = extras.rawValue)
            "ORG" -> updated.copy(organization = extras.rawValue)
            "TITLE" -> updated.copy(title = extras.rawValue)
            "ROLE" -> updated.copy(role = extras.rawValue)
            "GENDER" -> updated.copy(gender = extras.rawValue)
            "BDAY" -> updated.copy(birthday = extras.rawValue)
            "BIRTHPLACE" -> updated.copy(birthplace = extras.rawValue)
            "ANNIVERSARY" -> updated.copy(anniversary = extras.rawValue)
            "DEATHDATE" -> updated.copy(deathDate = extras.rawValue)
            "DEATHPLACE" -> updated.copy(deathPlace = extras.rawValue)
            "CATEGORIES" -> updated.copy(categories = extras.rawValue)
            "NOTE" -> updated.copy(notes = extras.rawValue)
            "TZ" -> updated.copy(timeZone = extras.rawValue)
            "X-MANAGERSNAME" -> updated.withLineManagerFrom(extras)
            "RELATIONSHIP" -> updated.copy(relationshipToYou = extras.rawValue)
            "PHOTO" -> {
                if (
                    extras.rawValue.isBlank() &&
                    !extras.hasPersistableMetadata()
                ) {
                    updated.copy(
                        removePhoto = true,
                        photoBytes = null,
                        hasExistingPhoto = false,
                        photoVcardBaselineRaw = null,
                    )
                } else {
                    val bytes = com.nextcloud.contacts.data.remote.VCardPhotoHelper.decodeBinary(extras.rawValue)
                    updated.copy(
                        removePhoto = false,
                        photoBytes = bytes ?: updated.photoBytes,
                        hasExistingPhoto = bytes != null ||
                            updated.hasExistingPhoto ||
                            extras.rawValue.isNotBlank(),
                    )
                }
            }
            else -> updated
        }
        updated
    }

    fun updateAddressSyntax(index: Int, syntax: VCardSyntaxExtras) = updateAddress(index) {
        copy(syntax = syntax)
    }

    fun addCustomField() = update { copy(customFields = customFields + CustomFieldInput()) }

    fun updateCustomFieldLabel(index: Int, label: String) = updateCustomField(index) { copy(label = label) }

    fun updateCustomFieldValue(index: Int, value: String) = updateCustomField(index) { copy(value = value) }

    fun updateCustomFieldPropertyName(index: Int, propertyName: String) = updateCustomField(index) {
        val upper = propertyName.trim().uppercase()
        if (upper in RESERVED_CUSTOM_FIELD_PROPERTY_NAMES) this else copy(propertyName = upper)
    }

    fun updateCustomFieldGroup(index: Int, group: String) = updateCustomField(index) { copy(propertyGroup = group) }

    fun updateCustomFieldSyntax(index: Int, syntax: VCardSyntaxExtras) = updateCustomField(index) {
        copy(
            propertyGroup = syntax.propertyGroup,
            typeParam = syntax.typeParam,
            extraParameters = syntax.extraParameters,
            label = syntax.abLabel.ifBlank { label },
        )
    }

    fun updateCustomFieldFromVCard(index: Int, field: CustomFieldInput) = updateCustomField(index) {
        copy(
            label = field.label,
            value = field.value,
            propertyName = field.propertyName,
            propertyGroup = field.propertyGroup,
            typeParam = field.typeParam,
            extraParameters = field.extraParameters,
        )
    }

    fun updateCustomFieldType(index: Int, type: String) = updateCustomField(index) { copy(typeParam = type) }

    fun addCustomFieldExtraParameter(index: Int) = updateCustomField(index) {
        copy(extraParameters = extraParameters + ("" to ""))
    }

    fun updateCustomFieldExtraParameterName(index: Int, paramIndex: Int, name: String) =
        updateCustomFieldExtraParameter(index, paramIndex, name, updateName = true)

    fun updateCustomFieldExtraParameterValue(index: Int, paramIndex: Int, value: String) =
        updateCustomFieldExtraParameter(index, paramIndex, value, updateName = false)

    fun removeCustomFieldExtraParameter(index: Int, paramIndex: Int) = updateCustomField(index) {
        if (paramIndex !in extraParameters.indices) return@updateCustomField this
        copy(extraParameters = extraParameters.toMutableList().also { it.removeAt(paramIndex) })
    }

    fun removeCustomField(index: Int) = update {
        copy(customFields = customFields.toMutableList().also { it.removeAt(index) })
    }

    private fun updateCustomField(index: Int, block: CustomFieldInput.() -> CustomFieldInput) = update {
        if (index !in customFields.indices) return@update this
        val updated = customFields.toMutableList()
        updated[index] = updated[index].block()
        copy(customFields = updated)
    }

    private fun updateCustomFieldExtraParameter(
        index: Int,
        paramIndex: Int,
        text: String,
        updateName: Boolean,
    ) = updateCustomField(index) {
        if (paramIndex !in extraParameters.indices) return@updateCustomField this
        val params = extraParameters.toMutableList()
        val (name, value) = params[paramIndex]
        params[paramIndex] = if (updateName) text to value else name to text
        copy(extraParameters = params)
    }

    fun setPhotoFromUri(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: return@launch
            update {
                val nextExtras = scalarVcardExtras.toMutableMap()
                nextExtras.remove("PHOTO")
                copy(
                    photoBytes = bytes,
                    removePhoto = false,
                    hasExistingPhoto = true,
                    photoVcardBaselineRaw = com.nextcloud.contacts.data.remote.VCardPhotoHelper.baselineRawValue(
                        bytes,
                        scalarVcardExtras["PHOTO"],
                    ),
                    scalarVcardExtras = nextExtras,
                )
            }
        }
    }

    fun removePhoto() = update {
        val nextExtras = scalarVcardExtras.toMutableMap()
        nextExtras.remove("PHOTO")
        copy(
            photoBytes = null,
            removePhoto = true,
            hasExistingPhoto = false,
            photoVcardBaselineRaw = null,
            scalarVcardExtras = nextExtras,
        )
    }

    fun save() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val editFieldSyntaxEnabled = _state.value.editFieldSyntaxEnabled
                val useVcardPhoto = _settingsFieldSyntaxEnabled.value && editFieldSyntaxEnabled
                val stateForSave = if (useVcardPhoto) {
                    val storedBytes = if (contactId != 0L) {
                        withContext(Dispatchers.IO) { repository.getPhotoBytes(contactId) }
                    } else {
                        null
                    }
                    _state.value.resolvePhotoForSave(storedBytes)
                } else {
                    _state.value
                }
                if (useVcardPhoto && stateForSave != _state.value) {
                    _state.value = stateForSave
                }
                val input = stateForSave.toContactInput()
                if (contactId == 0L) {
                    repository.createContact(input, editFieldSyntaxEnabled = editFieldSyntaxEnabled)
                } else {
                    repository.updateContact(
                        contactId,
                        input,
                        editFieldSyntaxEnabled = editFieldSyntaxEnabled,
                    )
                }
                SyncWorker.syncNow(getApplication(), localSave = true)
                _saved.value = true
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun delete() {
        if (contactId == 0L) return
        viewModelScope.launch {
            repository.deleteContact(contactId)
            SyncWorker.syncNow(getApplication(), localSave = true)
            _deleted.value = true
        }
    }

    private fun update(block: ContactEditState.() -> ContactEditState) {
        _state.value = _state.value.block()
    }
}

private fun updateTypedValue(
    list: List<VCardTypedFieldInput>,
    index: Int,
    value: String,
): List<VCardTypedFieldInput> {
    val updated = list.toMutableList()
    if (index in updated.indices) updated[index] = updated[index].copy(value = value)
    return updated
}

private fun updateTypedType(
    list: List<VCardTypedFieldInput>,
    index: Int,
    type: String,
): List<VCardTypedFieldInput> {
    val updated = list.toMutableList()
    if (index in updated.indices) {
        val field = updated[index]
        updated[index] = field.copy(
            type = type,
            syntax = field.syntax.copy(typeParam = ""),
        )
    }
    return updated
}

private fun updateTypedSyntax(
    list: List<VCardTypedFieldInput>,
    index: Int,
    syntax: VCardSyntaxExtras,
): List<VCardTypedFieldInput> {
    val updated = list.toMutableList()
    if (index in updated.indices) updated[index] = updated[index].copy(syntax = syntax)
    return updated
}

private fun replaceTypedField(
    list: List<VCardTypedFieldInput>,
    index: Int,
    field: VCardTypedFieldInput,
): List<VCardTypedFieldInput> {
    if (index !in list.indices) return list
    return list.toMutableList().also { it[index] = field }
}

private fun <T> removeIfMultiple(list: List<T>, index: Int): List<T> =
    if (list.size <= 1 || index !in list.indices) list else list.toMutableList().also { it.removeAt(index) }

private fun ContactInput.toEditState(
    hasExistingPhoto: Boolean,
    isNew: Boolean,
    showMoreNameFields: Boolean,
    showMoreOrganizationFields: Boolean,
): ContactEditState {
    val input = migrateLegacyContactGeo()
    return ContactEditState(
        prefix = input.prefix,
        givenName = input.givenName,
        middleName = input.middleName,
        familyName = input.familyName,
        suffix = input.suffix,
        nickname = input.nickname,
        phoneticGiven = input.phoneticGiven,
        phoneticFamily = input.phoneticFamily,
        organization = input.organization,
        title = input.title,
        role = input.role,
        gender = input.gender,
        birthday = input.birthday,
        birthplace = input.birthplace,
        anniversary = input.anniversary,
        deathDate = input.deathDate,
        deathPlace = input.deathPlace,
        categories = input.categories,
        notes = input.notes,
        notesMarkdown = input.notesMarkdown,
        timeZone = input.timeZone,
        managersName = input.managersName,
        managersUid = input.managersUid,
        relationshipToYou = input.relationshipToYou,
        phones = input.phones,
        emails = input.emails,
        addresses = input.addresses,
        urls = input.urls,
        imAccounts = input.imAccounts,
        socialProfiles = input.socialProfiles,
        cloudIds = input.cloudIds,
        languages = input.languages,
        relatedNames = input.relatedNames,
        customFields = input.customFields,
        scalarVcardExtras = input.scalarVcardExtras,
        photoBytes = input.photoBytes,
        hasExistingPhoto = hasExistingPhoto,
        isReady = true,
        isNew = isNew,
        showMoreNameFields = showMoreNameFields,
        showMoreOrganizationFields = showMoreOrganizationFields,
        expandedAddressIndices = input.addresses.mapIndexedNotNull { index, address ->
            index.takeIf { address.hasExtraFields() }
        }.toSet(),
    )
}

private fun ContactInput.migrateLegacyContactGeo(): ContactInput {
    if (geo.isBlank()) return this
    val list = addresses.toMutableList()
    if (list.isEmpty()) return this
    if (list[0].geo.isBlank()) {
        list[0] = list[0].copy(geo = geo)
    }
    return copy(addresses = list, geo = "")
}

private fun ContactEditState.toContactInput(): ContactInput = ContactInput(
    prefix = prefix,
    givenName = givenName,
    middleName = middleName,
    familyName = familyName,
    suffix = suffix,
    nickname = nickname,
    phoneticGiven = phoneticGiven,
    phoneticFamily = phoneticFamily,
    organization = organization,
    title = title,
    role = role,
    gender = gender,
    birthday = birthday,
    birthplace = birthplace,
    anniversary = anniversary,
    deathDate = deathDate,
    deathPlace = deathPlace,
    categories = categories,
    notes = notes,
    notesMarkdown = notesMarkdown,
    geo = "",
    timeZone = timeZone,
    managersName = managersName,
    managersUid = managersUid,
    relationshipToYou = relationshipToYou,
    phones = phones,
    emails = emails,
    addresses = addresses,
    urls = urls,
    imAccounts = imAccounts,
    socialProfiles = socialProfiles,
    cloudIds = cloudIds,
    languages = languages,
    relatedNames = relatedNames,
    customFields = customFields.filter { it.hasPersistableContent() },
    scalarVcardExtras = scalarVcardExtras,
    photoBytes = photoBytes,
    removePhoto = removePhoto,
)

private fun ContactInput.hasExtendedNameFields(): Boolean =
    prefix.isNotBlank() || middleName.isNotBlank() || suffix.isNotBlank() ||
        nickname.isNotBlank() || phoneticGiven.isNotBlank() || phoneticFamily.isNotBlank()

private fun AddressInput.hasExtraFields(): Boolean =
    poBox.isNotBlank() || extendedAddress.isNotBlank() || city.isNotBlank() ||
        region.isNotBlank() || postalCode.isNotBlank() || country.isNotBlank() ||
        geo.isNotBlank()

private fun ContactEditState.withStructuredNameFrom(raw: String): ContactEditState {
    val parts = raw.split(';')
    return copy(
        familyName = parts.getOrElse(0) { "" },
        givenName = parts.getOrElse(1) { "" },
        middleName = parts.getOrElse(2) { "" },
        prefix = parts.getOrElse(3) { "" },
        suffix = parts.getOrElse(4) { "" },
    )
}

private fun ContactEditState.withLineManagerFrom(extras: VCardSyntaxExtras): ContactEditState {
    val uid = extras.extraParameters
        .firstOrNull { it.first.equals("UID", ignoreCase = true) }
        ?.second
        ?.trim()
        .orEmpty()
    return copy(
        managersName = extras.rawValue,
        managersUid = uid,
    )
}

internal fun ContactEditState.resolvePhotoForSave(storedPhotoBytes: ByteArray?): ContactEditState {
    if (removePhoto) {
        val extras = scalarVcardExtras.toMutableMap().also { it.remove(ScalarVCardProperty.PHOTO) }
        return copy(scalarVcardExtras = extras, photoBytes = null)
    }
    val photoExtra = scalarVcardExtras[ScalarVCardProperty.PHOTO] ?: VCardSyntaxExtras()
    val storedRaw = photoExtra.rawValue.trim()
    val baseline = photoVcardBaselineRaw?.trim().orEmpty()
    val effectiveRaw = when {
        storedRaw.isNotBlank() -> storedRaw
        (photoBytes != null || hasExistingPhoto || storedPhotoBytes != null) && baseline.isNotBlank() -> baseline
        else -> ""
    }
    val hasPhoto = effectiveRaw.isNotBlank() ||
        photoExtra.hasPersistableMetadata() ||
        photoBytes != null ||
        hasExistingPhoto ||
        storedPhotoBytes != null
    if (!hasPhoto) {
        return copy(removePhoto = true, photoBytes = null)
    }
    val nextExtras = scalarVcardExtras.toMutableMap()
    if (effectiveRaw.isNotBlank() || photoExtra.hasPersistableMetadata()) {
        nextExtras[ScalarVCardProperty.PHOTO] = photoExtra.copy(rawValue = effectiveRaw)
    }
    val bytesFromRaw = VCardPhotoHelper.decodeBinary(effectiveRaw)
    val useStoredFile = effectiveRaw == baseline &&
        bytesFromRaw == null &&
        !VCardPhotoHelper.looksLikeUri(effectiveRaw) &&
        (photoBytes != null || hasExistingPhoto || storedPhotoBytes != null)
    val resolvedBytes = when {
        useStoredFile -> photoBytes ?: storedPhotoBytes
        bytesFromRaw != null -> bytesFromRaw
        else -> null
    }
    return copy(
        scalarVcardExtras = nextExtras,
        photoBytes = resolvedBytes,
        removePhoto = false,
        hasExistingPhoto = resolvedBytes != null ||
            (effectiveRaw.isNotBlank() && VCardPhotoHelper.looksLikeUri(effectiveRaw)),
    )
}

private fun remappedExpandedIndices(expanded: Set<Int>, removedIndex: Int, previousSize: Int): Set<Int> {
    if (previousSize <= 1 || removedIndex !in 0 until previousSize) return expanded
    return expanded.mapNotNull { index ->
        when {
            index == removedIndex -> null
            index > removedIndex -> index - 1
            else -> index
        }
    }.toSet()
}
