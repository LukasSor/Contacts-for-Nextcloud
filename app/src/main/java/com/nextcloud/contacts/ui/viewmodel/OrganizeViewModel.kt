package com.nextcloud.contacts.ui.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.ContactDuplicates
import com.nextcloud.contacts.data.ContactMerge
import com.nextcloud.contacts.data.MergeScalarSnapshot
import com.nextcloud.contacts.data.MergeSingletonField
import com.nextcloud.contacts.data.local.ContactCategories
import com.nextcloud.contacts.data.local.ContactFieldOptions
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.platform.EmergencyContacts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeletedContactItem(
    val id: Long,
    val displayName: String,
    /** [com.nextcloud.contacts.data.local.ContactEntity.lastModified] at delete time. */
    val deletedAt: Long,
)

data class DuplicateFieldLine(
    @StringRes val labelRes: Int = 0,
    val literalLabel: String = "",
    @StringRes val valueRes: Int = 0,
    val value: String = "",
)

data class DuplicateContactSide(
    val id: Long,
    val displayName: String,
    val scalars: List<MergeScalarSnapshot>,
    val lines: List<DuplicateFieldLine>,
)

data class DuplicateMatchItem(
    val key: String,
    val match: ContactDuplicates.Match,
    val sharedValue: String,
    val contacts: List<DuplicateContactSide>,
)

data class OrganizeUiState(
    val deleted: List<DeletedContactItem>? = null,
    val duplicateMatches: List<DuplicateMatchItem>? = null,
    val emergencyNames: List<String>? = null,
    val emergencyCanWrite: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

class OrganizeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ContactsApp).container
    private val app = application

    private val deleted = MutableStateFlow<List<DeletedContactItem>?>(null)
    private val duplicateMatches = MutableStateFlow<List<DuplicateMatchItem>?>(null)
    private val emergencyNames = MutableStateFlow<List<String>?>(null)
    private val emergencyCanWrite = MutableStateFlow(false)
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OrganizeUiState> = combine(
        combine(deleted, duplicateMatches, emergencyNames, emergencyCanWrite) { deletedRows, groups, names, canWrite ->
            EmergencyAndLists(deletedRows, groups, names, canWrite)
        },
        busy,
        message,
    ) { lists, isBusy, text ->
        OrganizeUiState(
            deleted = lists.deleted,
            duplicateMatches = lists.groups,
            emergencyNames = lists.emergencyNames,
            emergencyCanWrite = lists.canWrite,
            busy = isBusy,
            message = text,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrganizeUiState())

    init {
        viewModelScope.launch {
            container.contactRepository.observeDeletedContacts().collect { rows ->
                deleted.value = rows.map { contact ->
                    DeletedContactItem(
                        id = contact.id,
                        displayName = contact.displayName,
                        deletedAt = contact.lastModified,
                    )
                }
            }
        }
        refreshEmergency()
        refreshDuplicates()
    }

    fun refreshDuplicates() {
        viewModelScope.launch {
            duplicateMatches.value = withContext(Dispatchers.IO) {
                val groups = container.contactRepository.loadDuplicateGroups()
                val ids = groups.flatMap { it.contactIds }.distinct()
                val byId = ids.mapNotNull { id ->
                    container.contactRepository.getContact(id)?.takeIf { !it.contact.deleted }
                }.associateBy { it.contact.id }
                groups.mapNotNull { group ->
                    val ordered = group.contactIds.mapNotNull { byId[it] }.sortedWith(
                        compareByDescending<ContactWithDetails> { !it.contact.remoteUrl.isNullOrBlank() }
                            .thenByDescending { it.contact.lastModified }
                            .thenBy { it.contact.id },
                    )
                    if (ordered.size < 2) return@mapNotNull null
                    val contacts = ordered.map { details ->
                        DuplicateContactSide(
                            id = details.contact.id,
                            displayName = details.contact.displayName,
                            scalars = scalarSnapshots(
                                details,
                                container.contactPhotoStore.photoDigest(details.contact.id),
                            ),
                            lines = duplicateFieldLines(details),
                        )
                    }
                    DuplicateMatchItem(
                        key = "${group.match.name}|${group.value}|${contacts.map { it.id }.sorted().joinToString(",")}",
                        match = group.match,
                        sharedValue = group.value,
                        contacts = contacts,
                    )
                }
            }
        }
    }

    fun refreshEmergency() {
        viewModelScope.launch {
            val names = withContext(Dispatchers.IO) {
                EmergencyContacts.readNames(app)
            }
            emergencyNames.value = names
            emergencyCanWrite.value = names != null && EmergencyContacts.canWrite()
        }
    }

    fun restore(id: Long) {
        viewModelScope.launch {
            busy.value = true
            try {
                withContext(Dispatchers.IO) {
                    container.contactRepository.restoreContact(id)
                }
                message.value = app.getString(R.string.msg_contact_restored)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message.value = app.getString(R.string.msg_restore_failed)
            } finally {
                busy.value = false
            }
        }
    }

    fun merge(
        keeperId: Long,
        duplicateId: Long,
        fieldsFromDuplicate: Set<MergeSingletonField> = emptySet(),
    ) {
        if (keeperId == duplicateId) return
        viewModelScope.launch {
            busy.value = true
            try {
                withContext(Dispatchers.IO) {
                    container.contactRepository.mergeContacts(
                        contactIds = listOf(keeperId, duplicateId),
                        keeperId = keeperId,
                        fieldsFromDuplicate = fieldsFromDuplicate,
                    )
                }
                message.value = app.getString(R.string.msg_contacts_merged)
                refreshDuplicates()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message.value = app.getString(R.string.msg_merge_failed)
            } finally {
                busy.value = false
            }
        }
    }

    fun showMessage(text: String) {
        message.value = text
    }

    fun clearMessage() {
        message.value = null
    }

    private data class EmergencyAndLists(
        val deleted: List<DeletedContactItem>?,
        val groups: List<DuplicateMatchItem>?,
        val emergencyNames: List<String>?,
        val canWrite: Boolean,
    )
}

private fun duplicateFieldLines(details: ContactWithDetails): List<DuplicateFieldLine> {
    val lines = mutableListOf<DuplicateFieldLine>()
    fun add(
        @StringRes labelRes: Int = 0,
        literalLabel: String = "",
        @StringRes valueRes: Int = 0,
        value: String = "",
    ) {
        if (valueRes == 0 && value.isBlank()) return
        lines += DuplicateFieldLine(
            labelRes = labelRes,
            literalLabel = literalLabel,
            valueRes = valueRes,
            value = value.trim(),
        )
    }
    for (phone in details.phones) {
        add(R.string.field_phone, value = phone.value)
    }
    for (email in details.emails) {
        add(R.string.field_email, value = email.value)
    }
    for (address in details.addresses) {
        val summary = listOf(
            address.street,
            address.extendedAddress,
            address.city,
            address.region,
            address.postalCode,
            address.country,
            address.poBox,
        ).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ").ifBlank { address.geo.trim() }
        add(R.string.field_address, value = summary)
    }
    for (url in details.urls) {
        add(R.string.field_website, value = url.value)
    }
    for (im in details.imAccounts) {
        add(R.string.field_instant_messaging, value = im.value)
    }
    for (social in details.socialProfiles) {
        add(R.string.field_social_network, value = social.value)
    }
    for (cloud in details.cloudIds) {
        add(R.string.field_federated_cloud_id, value = cloud.value)
    }
    for (language in details.languages) {
        add(R.string.field_spoken_languages, value = language.value)
    }
    for (related in details.relatedNames) {
        add(R.string.field_related_person, value = related.value)
    }
    for (group in ContactCategories.parse(details.contact.categories)) {
        add(R.string.field_groups, value = group)
    }
    for (custom in details.customFields) {
        val label = custom.label.trim()
        if (label.isEmpty()) add(R.string.field_custom, value = custom.value)
        else add(literalLabel = label, value = custom.value)
    }
    return lines
}

private fun scalarSnapshots(details: ContactWithDetails, photoDigest: String?): List<MergeScalarSnapshot> {
    val contact = details.contact
    val snapshots = mutableListOf<MergeScalarSnapshot>()
    fun add(field: MergeSingletonField, raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        snapshots += MergeScalarSnapshot(field = field, value = value, compareKey = value)
    }
    fun addChoice(
        field: MergeSingletonField,
        options: List<ContactFieldOptions.Option>,
        raw: String,
    ) {
        val token = raw.trim()
        if (token.isEmpty()) return
        val option = ContactFieldOptions.matchOption(options, token)
        when {
            option == null -> snapshots += MergeScalarSnapshot(
                field = field,
                value = token,
                compareKey = token.lowercase(),
            )
            option.id.isBlank() -> Unit
            option.labelRes != 0 -> snapshots += MergeScalarSnapshot(
                field = field,
                valueRes = option.labelRes,
                compareKey = option.id.lowercase(),
            )
            option.label.isNotBlank() -> snapshots += MergeScalarSnapshot(
                field = field,
                value = option.label,
                compareKey = option.id.lowercase(),
            )
            else -> snapshots += MergeScalarSnapshot(
                field = field,
                value = token,
                compareKey = token.lowercase(),
            )
        }
    }
    add(MergeSingletonField.DISPLAY_NAME, contact.displayName)
    add(MergeSingletonField.PREFIX, contact.prefix)
    add(MergeSingletonField.GIVEN_NAME, contact.givenName)
    add(MergeSingletonField.MIDDLE_NAME, contact.middleName)
    add(MergeSingletonField.FAMILY_NAME, contact.familyName)
    add(MergeSingletonField.SUFFIX, contact.suffix)
    add(MergeSingletonField.NICKNAME, contact.nickname)
    add(MergeSingletonField.PHONETIC_GIVEN, contact.phoneticGiven)
    add(MergeSingletonField.PHONETIC_FAMILY, contact.phoneticFamily)
    add(MergeSingletonField.ORGANIZATION, contact.organization)
    add(MergeSingletonField.TITLE, contact.title)
    add(MergeSingletonField.ROLE, contact.role)
    addChoice(MergeSingletonField.GENDER, ContactFieldOptions.gender, contact.gender)
    add(MergeSingletonField.BIRTHDAY, contact.birthday)
    add(MergeSingletonField.BIRTHPLACE, contact.birthplace)
    add(MergeSingletonField.ANNIVERSARY, contact.anniversary)
    add(MergeSingletonField.DEATH_DATE, contact.deathDate)
    add(MergeSingletonField.DEATH_PLACE, contact.deathPlace)
    add(MergeSingletonField.NOTES, contact.notes)
    add(MergeSingletonField.GEO, contact.geo)
    add(MergeSingletonField.TIME_ZONE, contact.timeZone)
    val managerKey = ContactMerge.lineManagerKey(contact.managersName, contact.managersUid)
    if (managerKey.isNotEmpty()) {
        snapshots += MergeScalarSnapshot(
            field = MergeSingletonField.LINE_MANAGER,
            value = contact.managersName.trim().ifBlank { contact.managersUid.trim() },
            compareKey = managerKey,
        )
    }
    addChoice(
        MergeSingletonField.RELATIONSHIP,
        ContactFieldOptions.relationshipToYou,
        contact.relationshipToYou,
    )
    if (!photoDigest.isNullOrBlank()) {
        snapshots += MergeScalarSnapshot(
            field = MergeSingletonField.PHOTO,
            compareKey = photoDigest,
        )
    }
    return snapshots
}
