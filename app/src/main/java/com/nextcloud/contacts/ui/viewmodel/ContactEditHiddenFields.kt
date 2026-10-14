package com.nextcloud.contacts.ui.viewmodel

import androidx.annotation.StringRes
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.prefs.ContactEditFieldMode
import com.nextcloud.contacts.data.prefs.includesFullFields
import com.nextcloud.contacts.data.prefs.includesWorkFields

/** Optional contact fields that Simple / Work modes may hide from the edit form. */
enum class ContactEditHiddenField(@StringRes val labelRes: Int, private val sortOrder: Int) {
    JOB_TITLE(R.string.field_job_title, 100),
    ROLE(R.string.field_role, 110),
    PHONETIC_GIVEN(R.string.field_phonetic_first, 200),
    PHONETIC_FAMILY(R.string.field_phonetic_last, 210),
    ADDRESS_PO_BOX(R.string.field_po_box, 300),
    PLACE_OF_BIRTH(R.string.field_place_of_birth, 400),
    ANNIVERSARY(R.string.field_anniversary, 410),
    INSTANT_MESSAGE(R.string.field_instant_message, 500),
    SOCIAL_PROFILE(R.string.field_social_profile, 510),
    WEBSITE(R.string.field_website, 600),
    LINE_MANAGER(R.string.field_line_manager, 610),
    FEDERATED_CLOUD_ID(R.string.field_federated_cloud_id, 620),
    TIME_ZONE(R.string.field_time_zone, 630),
    LANGUAGE(R.string.field_language, 640),
    GENDER(R.string.field_gender, 700),
    RELATIONSHIP_TO_YOU(R.string.field_relationship, 710),
    RELATED_PERSON(R.string.field_related_person, 720),
    DATE_OF_DEATH(R.string.field_date_of_death, 800),
    PLACE_OF_DEATH(R.string.field_place_of_death, 810),
    ;

    companion object {
        val pickerOrder: List<ContactEditHiddenField> =
            entries.sortedBy { it.sortOrder }
    }
}

fun ContactEditState.isHiddenFieldVisible(
    field: ContactEditHiddenField,
    mode: ContactEditFieldMode,
): Boolean {
    val showWork = mode.includesWorkFields()
    val showFull = mode.includesFullFields()
    val revealed = field in sessionRevealedHiddenFields
    return when (field) {
        ContactEditHiddenField.JOB_TITLE -> when {
            showWork -> showMoreOrganizationFields || title.isNotBlank()
            else -> revealed || title.isNotBlank()
        }
        ContactEditHiddenField.ROLE -> when {
            showWork -> showMoreOrganizationFields || role.isNotBlank()
            else -> revealed || role.isNotBlank()
        }
        ContactEditHiddenField.PHONETIC_GIVEN ->
            showFull || revealed || phoneticGiven.isNotBlank()
        ContactEditHiddenField.PHONETIC_FAMILY ->
            showFull || revealed || phoneticFamily.isNotBlank()
        ContactEditHiddenField.ADDRESS_PO_BOX ->
            showWork || revealed || addresses.any { it.poBox.isNotBlank() }
        ContactEditHiddenField.PLACE_OF_BIRTH ->
            showFull || revealed || birthplace.isNotBlank()
        ContactEditHiddenField.ANNIVERSARY ->
            showFull || revealed || anniversary.isNotBlank()
        ContactEditHiddenField.INSTANT_MESSAGE -> instantMessageBlockVisible(mode)
        ContactEditHiddenField.SOCIAL_PROFILE -> socialProfileBlockVisible(mode)
        ContactEditHiddenField.WEBSITE ->
            showWork || revealed || urls.any { it.value.isNotBlank() }
        ContactEditHiddenField.LINE_MANAGER ->
            showWork || revealed || managersUid.isNotBlank() || managersName.isNotBlank()
        ContactEditHiddenField.FEDERATED_CLOUD_ID ->
            showWork || revealed || cloudIds.any { it.value.isNotBlank() }
        ContactEditHiddenField.TIME_ZONE ->
            showWork || revealed || timeZone.isNotBlank()
        ContactEditHiddenField.LANGUAGE ->
            showWork || revealed || languages.any { it.value.isNotBlank() }
        ContactEditHiddenField.GENDER ->
            showFull || revealed || gender.isNotBlank()
        ContactEditHiddenField.RELATIONSHIP_TO_YOU ->
            showFull || revealed || relationshipToYou.isNotBlank()
        ContactEditHiddenField.RELATED_PERSON ->
            showFull || revealed || relatedNames.any { it.value.isNotBlank() }
        ContactEditHiddenField.DATE_OF_DEATH ->
            showFull || revealed || deathDate.isNotBlank()
        ContactEditHiddenField.PLACE_OF_DEATH ->
            showFull || revealed || deathPlace.isNotBlank()
    }
}

fun ContactEditState.addableHiddenFields(mode: ContactEditFieldMode): List<ContactEditHiddenField> {
    if (mode.includesFullFields()) return emptyList()
    return ContactEditHiddenField.pickerOrder.filter { field ->
        !isHiddenFieldVisible(field, mode) && isHiddenByMode(field, mode)
    }
}

private fun ContactEditState.isHiddenByMode(
    field: ContactEditHiddenField,
    mode: ContactEditFieldMode,
): Boolean {
    val showWork = mode.includesWorkFields()
    val showFull = mode.includesFullFields()
    return when (field) {
        ContactEditHiddenField.JOB_TITLE,
        ContactEditHiddenField.ROLE,
        ContactEditHiddenField.ADDRESS_PO_BOX,
        ContactEditHiddenField.WEBSITE,
        ContactEditHiddenField.LINE_MANAGER,
        ContactEditHiddenField.FEDERATED_CLOUD_ID,
        ContactEditHiddenField.TIME_ZONE,
        ContactEditHiddenField.LANGUAGE,
        -> !showWork
        ContactEditHiddenField.PHONETIC_GIVEN,
        ContactEditHiddenField.PHONETIC_FAMILY,
        ContactEditHiddenField.PLACE_OF_BIRTH,
        ContactEditHiddenField.ANNIVERSARY,
        ContactEditHiddenField.INSTANT_MESSAGE,
        ContactEditHiddenField.SOCIAL_PROFILE,
        ContactEditHiddenField.GENDER,
        ContactEditHiddenField.RELATIONSHIP_TO_YOU,
        ContactEditHiddenField.RELATED_PERSON,
        ContactEditHiddenField.DATE_OF_DEATH,
        ContactEditHiddenField.PLACE_OF_DEATH,
        -> !showFull
    }
}

fun ContactEditState.instantMessageBlockVisible(mode: ContactEditFieldMode): Boolean {
    if (mode.includesFullFields()) return true
    if (ContactEditHiddenField.INSTANT_MESSAGE in sessionRevealedHiddenFields) return true
    if (pinnedVisibleImIndices.isNotEmpty()) return true
    return imAccounts.any { it.value.isNotBlank() }
}

fun ContactEditState.showAllInstantMessageRows(mode: ContactEditFieldMode): Boolean =
    mode.includesFullFields() ||
        ContactEditHiddenField.INSTANT_MESSAGE in sessionRevealedHiddenFields

fun ContactEditState.visibleInstantMessageIndices(mode: ContactEditFieldMode): List<Int> =
    if (showAllInstantMessageRows(mode)) {
        imAccounts.indices.toList()
    } else {
        imAccounts.indices.filter { index ->
            index in pinnedVisibleImIndices || imAccounts[index].value.isNotBlank()
        }
    }

fun ContactEditState.socialProfileBlockVisible(mode: ContactEditFieldMode): Boolean {
    if (mode.includesFullFields()) return true
    if (ContactEditHiddenField.SOCIAL_PROFILE in sessionRevealedHiddenFields) return true
    if (pinnedVisibleSocialIndices.isNotEmpty()) return true
    return socialProfiles.any { it.value.isNotBlank() }
}

fun ContactEditState.showAllSocialProfileRows(mode: ContactEditFieldMode): Boolean =
    mode.includesFullFields() ||
        ContactEditHiddenField.SOCIAL_PROFILE in sessionRevealedHiddenFields

fun ContactEditState.visibleSocialProfileIndices(mode: ContactEditFieldMode): List<Int> =
    if (showAllSocialProfileRows(mode)) {
        socialProfiles.indices.toList()
    } else {
        socialProfiles.indices.filter { index ->
            index in pinnedVisibleSocialIndices || socialProfiles[index].value.isNotBlank()
        }
    }

fun ContactEditState.focusRowIndexForHiddenField(field: ContactEditHiddenField): Int =
    when (field) {
        ContactEditHiddenField.INSTANT_MESSAGE -> firstBlankPairValueIndex(imAccounts)
        ContactEditHiddenField.SOCIAL_PROFILE -> firstBlankPairValueIndex(socialProfiles)
        ContactEditHiddenField.WEBSITE -> firstBlankPairValueIndex(urls)
        ContactEditHiddenField.RELATED_PERSON -> firstBlankPairValueIndex(relatedNames)
        ContactEditHiddenField.FEDERATED_CLOUD_ID -> firstBlankPairValueIndex(cloudIds)
        ContactEditHiddenField.LANGUAGE -> {
            languages.indexOfFirst { it.value.isBlank() }.takeIf { it >= 0 }
                ?: languages.lastIndex.coerceAtLeast(0)
        }
        else -> 0
    }

fun ContactEditState.focusAddressIndexForHiddenField(field: ContactEditHiddenField): Int =
    when (field) {
        ContactEditHiddenField.ADDRESS_PO_BOX -> addresses.indices.firstOrNull() ?: 0
        else -> 0
    }

fun ContactEditState.isFocusTargetReady(
    field: ContactEditHiddenField,
    rowIndex: Int,
    addressIndex: Int,
    mode: ContactEditFieldMode,
): Boolean {
    if (!isHiddenFieldVisible(field, mode)) return false
    return when (field) {
        ContactEditHiddenField.JOB_TITLE,
        ContactEditHiddenField.ROLE,
        -> !mode.includesWorkFields() || showMoreOrganizationFields
        ContactEditHiddenField.PHONETIC_GIVEN,
        ContactEditHiddenField.PHONETIC_FAMILY,
        -> showMoreNameFields
        ContactEditHiddenField.ADDRESS_PO_BOX ->
            addressIndex in expandedAddressIndices &&
                addressIndex in addresses.indices
        ContactEditHiddenField.INSTANT_MESSAGE ->
            rowIndex in visibleInstantMessageIndices(mode)
        ContactEditHiddenField.SOCIAL_PROFILE ->
            rowIndex in visibleSocialProfileIndices(mode)
        ContactEditHiddenField.WEBSITE ->
            rowIndex in urls.indices
        ContactEditHiddenField.FEDERATED_CLOUD_ID ->
            rowIndex in cloudIds.indices
        ContactEditHiddenField.LANGUAGE ->
            rowIndex in languages.indices
        ContactEditHiddenField.RELATED_PERSON ->
            rowIndex in relatedNames.indices
        else -> true
    }
}

private fun firstBlankPairValueIndex(list: List<com.nextcloud.contacts.data.local.VCardTypedFieldInput>): Int =
    list.indexOfFirst { it.value.isBlank() }.takeIf { it >= 0 }
        ?: list.lastIndex.coerceAtLeast(0)

fun ContactEditState.applyRevealHiddenField(field: ContactEditHiddenField): ContactEditState {
    var next = copy(sessionRevealedHiddenFields = sessionRevealedHiddenFields + field)
    when (field) {
        ContactEditHiddenField.JOB_TITLE,
        ContactEditHiddenField.ROLE,
        -> next = next.copy(showMoreOrganizationFields = true)
        ContactEditHiddenField.PHONETIC_GIVEN,
        ContactEditHiddenField.PHONETIC_FAMILY,
        -> next = next.copy(showMoreNameFields = true)
        ContactEditHiddenField.ADDRESS_PO_BOX -> {
            next = next.copy(
                expandedAddressIndices = addresses.indices.toSet(),
            )
        }
        ContactEditHiddenField.INSTANT_MESSAGE -> {
            next = next.copy(
                pinnedVisibleImIndices = imAccounts.indices.toSet(),
            )
        }
        ContactEditHiddenField.SOCIAL_PROFILE -> {
            next = next.copy(
                pinnedVisibleSocialIndices = socialProfiles.indices.toSet(),
            )
        }
        else -> Unit
    }
    return next
}
