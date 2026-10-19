package com.nextcloud.contacts.ui.screens

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextcloud.contacts.data.local.ContactFieldOptions
import com.nextcloud.contacts.data.local.VCardSyntaxExtras
import com.nextcloud.contacts.data.local.LanguageOptions
import com.nextcloud.contacts.data.local.TimeZoneOptions
import com.nextcloud.contacts.data.prefs.includesFullFields
import com.nextcloud.contacts.data.prefs.includesWorkFields
import com.nextcloud.contacts.ui.components.ContactEditAddFieldPickerSheet
import com.nextcloud.contacts.ui.components.rememberContactEditFieldFocus
import com.nextcloud.contacts.ui.components.ContactAvatar
import com.nextcloud.contacts.ui.components.FormAddRow
import com.nextcloud.contacts.ui.components.FormCloudIdRow
import com.nextcloud.contacts.ui.components.NextcloudUserPickerDialog
import com.nextcloud.contacts.ui.components.FormDateRow
import com.nextcloud.contacts.ui.components.FormGroupsEditor
import com.nextcloud.contacts.ui.components.FormLineManagerRow
import com.nextcloud.contacts.ui.components.FormSelectRow
import com.nextcloud.contacts.ui.components.FormInstantMessageRow
import com.nextcloud.contacts.ui.components.FormSocialProfileRow
import com.nextcloud.contacts.ui.components.FormAddressFreeFormRow
import com.nextcloud.contacts.ui.components.FormAddressStreetAutocompleteRow
import com.nextcloud.contacts.ui.components.FormPlaceTextAutocompleteRow
import com.nextcloud.contacts.ui.components.ContactNotesGroupedField
import com.nextcloud.contacts.ui.components.FormTextRow
import com.nextcloud.contacts.ui.components.FormTypedTextRow
import com.nextcloud.contacts.ui.util.IdentifierKeyboard
import com.nextcloud.contacts.ui.components.GroupedFormScreenColumn
import com.nextcloud.contacts.ui.components.GroupedFormSection
import com.nextcloud.contacts.ui.components.GroupedFormSpacer
import com.nextcloud.contacts.ui.viewmodel.ContactEditHiddenField
import com.nextcloud.contacts.ui.viewmodel.ContactEditViewModel
import com.nextcloud.contacts.ui.viewmodel.NextcloudCrossLinkPrompt
import com.nextcloud.contacts.ui.viewmodel.PlaceAutocompleteTarget
import com.nextcloud.contacts.ui.viewmodel.applyRevealHiddenField
import com.nextcloud.contacts.ui.viewmodel.addableHiddenFields
import com.nextcloud.contacts.ui.viewmodel.instantMessageBlockVisible
import com.nextcloud.contacts.ui.viewmodel.isHiddenFieldVisible
import com.nextcloud.contacts.ui.viewmodel.showAllInstantMessageRows
import com.nextcloud.contacts.ui.viewmodel.showAllSocialProfileRows
import com.nextcloud.contacts.ui.viewmodel.socialProfileBlockVisible
import com.nextcloud.contacts.ui.viewmodel.visibleInstantMessageIndices
import com.nextcloud.contacts.ui.viewmodel.visibleSocialProfileIndices

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    onBack: () -> Unit,
    onSaved: (createdContactId: Long?) -> Unit,
    onDeleted: () -> Unit,
    viewModel: ContactEditViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val fieldMode by viewModel.fieldMode.collectAsState()
    val settingsFieldSyntaxEnabled by viewModel.settingsFieldSyntaxEnabled.collectAsState()
    val fieldSyntaxEnabled = settingsFieldSyntaxEnabled && state.editFieldSyntaxEnabled
    val showWorkFields = fieldMode.includesWorkFields()
    val showFullFields = fieldMode.includesFullFields()
    val saved by viewModel.saved.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    var navigatedAfterSave by remember { mutableStateOf(false) }
    var navigatedAfterDelete by remember { mutableStateOf(false) }
    val existingGroups by viewModel.existingGroups.collectAsState()
    val cloudUserSearch by viewModel.cloudUserSearch.collectAsState()
    val crossLinkPrompt by viewModel.crossLinkPrompt.collectAsState()
    val lineManagerCandidates by viewModel.lineManagerCandidates.collectAsState()
    val placeAutocomplete by viewModel.placeAutocomplete.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddFieldPicker by remember { mutableStateOf(false) }
    var showNextcloudAccountPicker by remember { mutableStateOf(false) }
    val addableHiddenFields = state.addableHiddenFields(fieldMode)
    val fieldFocus = rememberContactEditFieldFocus()
    fieldFocus.PendingRevealFocusEffect(state, fieldMode)

    LaunchedEffect(saved) {
        if (saved && !navigatedAfterSave) {
            navigatedAfterSave = true
            onSaved(viewModel.createdContactId.value)
        }
    }
    LaunchedEffect(deleted) {
        if (deleted && !navigatedAfterDelete) {
            navigatedAfterDelete = true
            onDeleted()
        }
    }

    crossLinkPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCrossLink,
            title = { Text(crossLinkTitle(prompt)) },
            text = { Text(crossLinkMessage(prompt)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCrossLink) {
                    Text(stringResource(R.string.action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCrossLink) {
                    Text(stringResource(R.string.action_not_now))
                }
            },
        )
    }

    if (showNextcloudAccountPicker && cloudUserSearch.canSearch) {
        NextcloudUserPickerDialog(
            state = cloudUserSearch,
            onQueryChange = viewModel::searchCloudUsers,
            onSelect = { user ->
                viewModel.applyNextcloudAccountFromPicker(user)
                showNextcloudAccountPicker = false
            },
            onDismiss = { showNextcloudAccountPicker = false },
        )
    }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let(viewModel::setPhotoFromUri)
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_delete_contact)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_delete_contact_body,
                        state.displayName.ifBlank { stringResource(R.string.this_contact) },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) stringResource(R.string.cd_add_contact) else stringResource(R.string.screen_edit_contact)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!state.isNew && state.isReady) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.isReady) {
                FloatingActionButton(
                    onClick = viewModel::save,
                ) {
                    Icon(Icons.Default.Save, contentDescription = stringResource(R.string.action_save))
                }
            }
        },
    ) { padding ->
        if (!state.isReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (fieldSyntaxEnabled) {
                    val previewBytes = state.photoPreviewBytes(state.photoBytes)
                    if (previewBytes != null) {
                        ContactAvatar(
                            contactId = viewModel.contactId,
                            displayName = state.displayName,
                            size = 72.dp,
                            photoBytes = previewBytes,
                            loadStoredPhoto = false,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    fun openPhotoPicker() {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }

                    ContactAvatar(
                        contactId = viewModel.contactId,
                        displayName = state.displayName,
                        size = 96.dp,
                        photoBytes = state.photoBytes,
                        loadStoredPhoto = state.hasExistingPhoto && !state.removePhoto,
                        emptyIcon = if (state.showPhoto) null else Icons.Default.AddAPhoto,
                        modifier = Modifier.clickable(onClick = ::openPhotoPicker),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.showPhoto) {
                        Row {
                            FilledTonalButton(
                                onClick = ::openPhotoPicker,
                                shape = RoundedCornerShape(
                                    topStart = 24.dp,
                                    bottomStart = 24.dp,
                                    topEnd = 4.dp,
                                    bottomEnd = 4.dp,
                                ),
                            ) {
                                Text(stringResource(R.string.action_change))
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            FilledTonalButton(
                                onClick = viewModel::removePhoto,
                                shape = RoundedCornerShape(
                                    topStart = 4.dp,
                                    bottomStart = 4.dp,
                                    topEnd = 24.dp,
                                    bottomEnd = 24.dp,
                                ),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text(stringResource(R.string.action_remove))
                            }
                        }
                    } else {
                        FilledTonalButton(onClick = ::openPhotoPicker) {
                            Text(stringResource(R.string.action_add_photo))
                        }
                    }
                }
            }

            GroupedFormScreenColumn {
                if (fieldSyntaxEnabled) {
                    GroupedFormSection(
                        rows = buildList {
                            addScalarVCardBlock(
                                fieldSyntaxEnabled = true,
                                propertyName = ScalarVCardProperty.PHOTO,
                                syntax = state.effectivePhotoSyntax(state.photoBytes),
                                onSyntaxChange = {
                                    viewModel.updateScalarVcardProperty(ScalarVCardProperty.PHOTO, it)
                                },
                                showTypeField = true,
                                showAbLabel = false,
                                multilineValue = true,
                                valueLabel = stringResource(R.string.photo_value_raw),
                            )
                        },
                    )
                    GroupedFormSpacer()
                }
                GroupedFormSection(
                    rows = buildList {
                        if (fieldSyntaxEnabled) {
                            addScalarVCardBlock(
                                fieldSyntaxEnabled = true,
                                propertyName = ScalarVCardProperty.FN,
                                syntax = state.effectiveScalarSyntax(
                                    ScalarVCardProperty.FN,
                                    state.formattedNameRawValue(),
                                ),
                                onSyntaxChange = {
                                    viewModel.updateScalarVcardProperty(ScalarVCardProperty.FN, it)
                                },
                                showTypeField = false,
                                showAbLabel = false,
                            )
                            addScalarVCardBlock(
                                fieldSyntaxEnabled = true,
                                propertyName = ScalarVCardProperty.N,
                                syntax = state.effectiveScalarSyntax(
                                    ScalarVCardProperty.N,
                                    state.structuredNameRawValue(),
                                ),
                                onSyntaxChange = {
                                    viewModel.updateScalarVcardProperty(ScalarVCardProperty.N, it)
                                },
                                showTypeField = false,
                                showAbLabel = false,
                            )
                        } else {
                            add {
                                FormTextRow(
                                    value = state.givenName,
                                    onValueChange = viewModel::updateGivenName,
                                    placeholder = stringResource(R.string.field_first_name),
                                    icon = Icons.Default.Person,
                                )
                            }
                            add {
                                FormTextRow(
                                    value = state.familyName,
                                    onValueChange = viewModel::updateFamilyName,
                                    placeholder = stringResource(R.string.field_last_name),
                                    icon = Icons.Default.Person,
                                )
                            }
                        }
                    },
                )

                GroupedFormSpacer()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = viewModel::toggleMoreNameFields)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.showMoreNameFields) stringResource(R.string.action_show_fewer_fields) else stringResource(R.string.action_show_more_fields),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (state.showMoreNameFields) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                if (state.showMoreNameFields) {
                    Spacer(modifier = Modifier.height(8.dp))
                    GroupedFormSection(
                        rows = buildList {
                            if (fieldSyntaxEnabled) {
                                addScalarVCardBlock(
                                    fieldSyntaxEnabled = true,
                                    propertyName = ScalarVCardProperty.NICKNAME,
                                    syntax = state.effectiveScalarSyntax(
                                        ScalarVCardProperty.NICKNAME,
                                        state.nickname,
                                    ),
                                    onSyntaxChange = {
                                        viewModel.updateScalarVcardProperty(ScalarVCardProperty.NICKNAME, it)
                                    },
                                    showTypeField = false,
                                    showAbLabel = false,
                                )
                                if (state.isHiddenFieldVisible(ContactEditHiddenField.PHONETIC_GIVEN, fieldMode)) {
                                    addScalarVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        propertyName = ScalarVCardProperty.PHONETIC_GIVEN,
                                        syntax = state.effectiveScalarSyntax(
                                            ScalarVCardProperty.PHONETIC_GIVEN,
                                            state.phoneticGiven,
                                        ),
                                        onSyntaxChange = {
                                            viewModel.updateScalarVcardProperty(
                                                ScalarVCardProperty.PHONETIC_GIVEN,
                                                it,
                                            )
                                        },
                                        showTypeField = false,
                                        showAbLabel = false,
                                    )
                                }
                                if (state.isHiddenFieldVisible(ContactEditHiddenField.PHONETIC_FAMILY, fieldMode)) {
                                    addScalarVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        propertyName = ScalarVCardProperty.PHONETIC_FAMILY,
                                        syntax = state.effectiveScalarSyntax(
                                            ScalarVCardProperty.PHONETIC_FAMILY,
                                            state.phoneticFamily,
                                        ),
                                        onSyntaxChange = {
                                            viewModel.updateScalarVcardProperty(
                                                ScalarVCardProperty.PHONETIC_FAMILY,
                                                it,
                                            )
                                        },
                                        showTypeField = false,
                                        showAbLabel = false,
                                    )
                                }
                            } else {
                                add {
                                    FormTextRow(
                                        value = state.prefix,
                                        onValueChange = viewModel::updatePrefix,
                                        placeholder = stringResource(R.string.field_prefix),
                                        icon = Icons.Default.Person,
                                    )
                                }
                                add {
                                    FormTextRow(
                                        value = state.middleName,
                                        onValueChange = viewModel::updateMiddleName,
                                        placeholder = stringResource(R.string.field_middle_name),
                                        icon = Icons.Default.Person,
                                    )
                                }
                                add {
                                    FormTextRow(
                                        value = state.suffix,
                                        onValueChange = viewModel::updateSuffix,
                                        placeholder = stringResource(R.string.field_suffix),
                                        icon = Icons.Default.Person,
                                    )
                                }
                                add {
                                    FormTextRow(
                                        value = state.nickname,
                                        onValueChange = viewModel::updateNickname,
                                        placeholder = stringResource(R.string.field_nickname),
                                        icon = Icons.Default.Badge,
                                    )
                                }
                                if (state.isHiddenFieldVisible(ContactEditHiddenField.PHONETIC_GIVEN, fieldMode)) {
                                    add {
                                        FormTextRow(
                                            value = state.phoneticGiven,
                                            onValueChange = viewModel::updatePhoneticGiven,
                                            placeholder = stringResource(R.string.field_phonetic_first),
                                            icon = Icons.Default.Person,
                                            valueFocus = fieldFocus.valueFocus(ContactEditHiddenField.PHONETIC_GIVEN),
                                        )
                                    }
                                }
                                if (state.isHiddenFieldVisible(ContactEditHiddenField.PHONETIC_FAMILY, fieldMode)) {
                                    add {
                                        FormTextRow(
                                            value = state.phoneticFamily,
                                            onValueChange = viewModel::updatePhoneticFamily,
                                            placeholder = stringResource(R.string.field_phonetic_last),
                                            icon = Icons.Default.Person,
                                            valueFocus = fieldFocus.valueFocus(ContactEditHiddenField.PHONETIC_FAMILY),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                GroupedFormSpacer()

                GroupedFormSection(
                    rows = buildList {
                        state.phones.forEachIndexed { index, field ->
                            if (fieldSyntaxEnabled) {
                                addTypedFieldVCardBlock(
                                    fieldSyntaxEnabled = true,
                                    field = field,
                                    defaultPropertyName = "TEL",
                                    onFieldChange = { viewModel.updatePhoneField(index, it) },
                                    onRemove = if (state.phones.size > 1) {
                                        { viewModel.removePhone(index) }
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                add {
                                    FormTypedTextRow(
                                        type = field.type,
                                        onTypeChange = { viewModel.updatePhoneType(index, it) },
                                        typeOptions = ContactFieldOptions.phones,
                                        value = field.value,
                                        onValueChange = { viewModel.updatePhone(index, it) },
                                        placeholder = stringResource(R.string.field_phone),
                                        icon = Icons.Default.Phone,
                                        typePickerTitle = stringResource(R.string.picker_phone_type),
                                        onRemove = if (state.phones.size > 1) {
                                            { viewModel.removePhone(index) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                        add {
                            FormAddRow(
                                label = stringResource(R.string.add_phone),
                                onClick = viewModel::addPhone,
                            )
                        }
                    },
                )

                GroupedFormSpacer()

                GroupedFormSection(
                    rows = buildList {
                        state.emails.forEachIndexed { index, field ->
                            if (fieldSyntaxEnabled) {
                                addTypedFieldVCardBlock(
                                    fieldSyntaxEnabled = true,
                                    field = field,
                                    defaultPropertyName = "EMAIL",
                                    onFieldChange = { viewModel.updateEmailField(index, it) },
                                    onRemove = if (state.emails.size > 1) {
                                        { viewModel.removeEmail(index) }
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                add {
                                    FormTypedTextRow(
                                        type = field.type,
                                        onTypeChange = { viewModel.updateEmailType(index, it) },
                                        typeOptions = ContactFieldOptions.emails,
                                        value = field.value,
                                        onValueChange = { viewModel.updateEmail(index, it) },
                                        placeholder = stringResource(R.string.field_email),
                                        icon = Icons.Default.Email,
                                        typePickerTitle = stringResource(R.string.picker_email_type),
                                        keyboardOptions = IdentifierKeyboard.email,
                                        onRemove = if (state.emails.size > 1) {
                                            { viewModel.removeEmail(index) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                        add {
                            FormAddRow(
                                label = stringResource(R.string.add_email),
                                onClick = viewModel::addEmail,
                            )
                        }
                    },
                )

                GroupedFormSpacer()

                if (showWorkFields) {
                    val orgExpanded = state.showMoreOrganizationFields
                    GroupedFormSection(
                        rows = buildList {
                            if (fieldSyntaxEnabled) {
                                addScalarVCardBlock(
                                    fieldSyntaxEnabled = true,
                                    propertyName = ScalarVCardProperty.ORG,
                                    syntax = state.effectiveScalarSyntax(
                                        ScalarVCardProperty.ORG,
                                        state.organization,
                                    ),
                                    onSyntaxChange = {
                                        viewModel.updateScalarVcardProperty(ScalarVCardProperty.ORG, it)
                                    },
                                    showTypeField = false,
                                    showAbLabel = false,
                                )
                                if (orgExpanded) {
                                    addScalarVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        propertyName = ScalarVCardProperty.TITLE,
                                        syntax = state.effectiveScalarSyntax(
                                            ScalarVCardProperty.TITLE,
                                            state.title,
                                        ),
                                        onSyntaxChange = {
                                            viewModel.updateScalarVcardProperty(ScalarVCardProperty.TITLE, it)
                                        },
                                        showTypeField = false,
                                        showAbLabel = false,
                                    )
                                    addScalarVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        propertyName = ScalarVCardProperty.ROLE,
                                        syntax = state.effectiveScalarSyntax(
                                            ScalarVCardProperty.ROLE,
                                            state.role,
                                        ),
                                        onSyntaxChange = {
                                            viewModel.updateScalarVcardProperty(ScalarVCardProperty.ROLE, it)
                                        },
                                        showTypeField = false,
                                        showAbLabel = false,
                                    )
                                }
                            } else {
                                add {
                                    FormTextRow(
                                        value = state.organization,
                                        onValueChange = viewModel::updateOrganization,
                                        placeholder = stringResource(R.string.field_organization),
                                        icon = Icons.Default.Business,
                                    )
                                }
                                if (orgExpanded) {
                                    add {
                                        FormTextRow(
                                            value = state.title,
                                            onValueChange = viewModel::updateTitle,
                                            placeholder = stringResource(R.string.field_job_title),
                                            icon = Icons.Default.Work,
                                        )
                                    }
                                    add {
                                        FormTextRow(
                                            value = state.role,
                                            onValueChange = viewModel::updateRole,
                                            placeholder = stringResource(R.string.field_role),
                                            icon = Icons.Default.AssignmentInd,
                                        )
                                    }
                                }
                            }
                            if (!fieldSyntaxEnabled) {
                                add {
                                    FormAddRow(
                                        label = if (orgExpanded) stringResource(R.string.action_show_less) else stringResource(R.string.action_show_more),
                                        onClick = viewModel::toggleMoreOrganizationFields,
                                    )
                                }
                            } else if (orgExpanded) {
                                add {
                                    FormAddRow(
                                        label = stringResource(R.string.action_show_less),
                                        onClick = viewModel::toggleMoreOrganizationFields,
                                    )
                                }
                            } else {
                                add {
                                    FormAddRow(
                                        label = stringResource(R.string.action_show_more),
                                        onClick = viewModel::toggleMoreOrganizationFields,
                                    )
                                }
                            }
                        },
                    )
                } else {
                    GroupedFormSection(
                        rows = buildList {
                            if (fieldSyntaxEnabled) {
                                addScalarVCardBlock(
                                    fieldSyntaxEnabled = true,
                                    propertyName = ScalarVCardProperty.ORG,
                                    syntax = state.effectiveScalarSyntax(
                                        ScalarVCardProperty.ORG,
                                        state.organization,
                                    ),
                                    onSyntaxChange = {
                                        viewModel.updateScalarVcardProperty(ScalarVCardProperty.ORG, it)
                                    },
                                    showTypeField = false,
                                    showAbLabel = false,
                                )
                                if (state.isHiddenFieldVisible(ContactEditHiddenField.JOB_TITLE, fieldMode)) {
                                    addScalarVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        propertyName = ScalarVCardProperty.TITLE,
                                        syntax = state.effectiveScalarSyntax(
                                            ScalarVCardProperty.TITLE,
                                            state.title,
                                        ),
                                        onSyntaxChange = {
                                            viewModel.updateScalarVcardProperty(ScalarVCardProperty.TITLE, it)
                                        },
                                        showTypeField = false,
                                        showAbLabel = false,
                                    )
                                }
                                if (state.isHiddenFieldVisible(ContactEditHiddenField.ROLE, fieldMode)) {
                                    addScalarVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        propertyName = ScalarVCardProperty.ROLE,
                                        syntax = state.effectiveScalarSyntax(
                                            ScalarVCardProperty.ROLE,
                                            state.role,
                                        ),
                                        onSyntaxChange = {
                                            viewModel.updateScalarVcardProperty(ScalarVCardProperty.ROLE, it)
                                        },
                                        showTypeField = false,
                                        showAbLabel = false,
                                    )
                                }
                            } else {
                                add {
                                    FormTextRow(
                                        value = state.organization,
                                        onValueChange = viewModel::updateOrganization,
                                        placeholder = stringResource(R.string.field_organization),
                                        icon = Icons.Default.Business,
                                    )
                                }
                                if (state.isHiddenFieldVisible(ContactEditHiddenField.JOB_TITLE, fieldMode)) {
                                    add {
                                        FormTextRow(
                                            value = state.title,
                                            onValueChange = viewModel::updateTitle,
                                            placeholder = stringResource(R.string.field_job_title),
                                            icon = Icons.Default.Work,
                                            valueFocus = fieldFocus.valueFocus(ContactEditHiddenField.JOB_TITLE),
                                        )
                                    }
                                }
                                if (state.isHiddenFieldVisible(ContactEditHiddenField.ROLE, fieldMode)) {
                                    add {
                                        FormTextRow(
                                            value = state.role,
                                            onValueChange = viewModel::updateRole,
                                            placeholder = stringResource(R.string.field_role),
                                            icon = Icons.Default.AssignmentInd,
                                            valueFocus = fieldFocus.valueFocus(ContactEditHiddenField.ROLE),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                if (state.isHiddenFieldVisible(ContactEditHiddenField.LINE_MANAGER, fieldMode)) {
                    GroupedFormSpacer()
                    GroupedFormSection(
                        rows = buildList {
                            if (fieldSyntaxEnabled) {
                                addScalarVCardBlock(
                                    fieldSyntaxEnabled = true,
                                    propertyName = ScalarVCardProperty.MANAGERS,
                                    syntax = state.lineManagerSyntax(),
                                    onSyntaxChange = {
                                        viewModel.updateScalarVcardProperty(ScalarVCardProperty.MANAGERS, it)
                                    },
                                    showTypeField = false,
                                    showAbLabel = false,
                                )
                            } else {
                                add {
                                    FormLineManagerRow(
                                        displayName = state.managersName,
                                        managersUid = state.managersUid,
                                        candidates = lineManagerCandidates,
                                        onPick = viewModel::setLineManager,
                                        onClear = viewModel::clearLineManager,
                                        valueFocus = fieldFocus.valueFocus(ContactEditHiddenField.LINE_MANAGER),
                                        onDisplayNameChange = viewModel::updateManagersName,
                                        onManagersUidChange = viewModel::updateManagersUid,
                                    )
                                }
                            }
                        },
                    )
                }

                GroupedFormSpacer()

                // Street only until the address row is expanded.
                state.addresses.forEachIndexed { index, address ->
                    val expanded = index in state.expandedAddressIndices
                    GroupedFormSection(
                        rows = buildList {
                            if (fieldSyntaxEnabled) {
                                addAddressVCardBlock(
                                    fieldSyntaxEnabled = true,
                                    address = address,
                                    onAddressChange = { updated ->
                                        viewModel.updateAddress(index) { updated }
                                    },
                                    onRemove = if (state.addresses.size > 1) {
                                        { viewModel.removeAddress(index) }
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                add {
                                    FormAddressStreetAutocompleteRow(
                                        type = address.type,
                                        onTypeChange = { type ->
                                            viewModel.updateAddress(index) { copy(type = type) }
                                        },
                                        typeOptions = ContactFieldOptions.addresses,
                                        value = address.street,
                                        onValueChange = { text ->
                                            viewModel.updateAddress(index) { copy(street = text) }
                                        },
                                        addressIndex = index,
                                        autocomplete = placeAutocomplete,
                                        onQueryChange = viewModel::onAddressStreetQueryChange,
                                        onPickSuggestion = viewModel::applyAddressSuggestion,
                                        onDismissSuggestions = viewModel::dismissPlaceAutocompleteSuggestions,
                                        placeholder = stringResource(R.string.field_address),
                                        typePickerTitle = stringResource(R.string.picker_address_type),
                                        onRemove = if (state.addresses.size > 1) {
                                            { viewModel.removeAddress(index) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                            if (!fieldSyntaxEnabled && expanded) {
                                add {
                                    FormTextRow(
                                        value = address.extendedAddress,
                                        onValueChange = { text ->
                                            viewModel.updateAddress(index) { copy(extendedAddress = text) }
                                        },
                                        placeholder = stringResource(R.string.field_extended_address),
                                        icon = Icons.Default.LocationOn,
                                    )
                                }
                                add {
                                    FormTextRow(
                                        value = address.city,
                                        onValueChange = { text ->
                                            viewModel.updateAddress(index) { copy(city = text) }
                                        },
                                        placeholder = stringResource(R.string.field_city),
                                        icon = Icons.Default.LocationOn,
                                    )
                                }
                                add {
                                    FormTextRow(
                                        value = address.region,
                                        onValueChange = { text ->
                                            viewModel.updateAddress(index) { copy(region = text) }
                                        },
                                        placeholder = stringResource(R.string.field_region),
                                        icon = Icons.Default.LocationOn,
                                    )
                                }
                                add {
                                    FormTextRow(
                                        value = address.postalCode,
                                        onValueChange = { text ->
                                            viewModel.updateAddress(index) { copy(postalCode = text) }
                                        },
                                        placeholder = stringResource(R.string.field_postal_code),
                                        icon = Icons.Default.LocationOn,
                                    )
                                }
                                add {
                                    FormTextRow(
                                        value = address.country,
                                        onValueChange = { text ->
                                            viewModel.updateAddress(index) { copy(country = text) }
                                        },
                                        placeholder = stringResource(R.string.field_country),
                                        icon = Icons.Default.LocationOn,
                                    )
                                }
                                if (state.isHiddenFieldVisible(ContactEditHiddenField.ADDRESS_PO_BOX, fieldMode)) {
                                    add {
                                        FormTextRow(
                                            value = address.poBox,
                                            onValueChange = { text ->
                                                viewModel.updateAddress(index) { copy(poBox = text) }
                                            },
                                            placeholder = stringResource(R.string.field_po_box),
                                            icon = Icons.Default.LocationOn,
                                            valueFocus = fieldFocus.valueFocus(
                                                ContactEditHiddenField.ADDRESS_PO_BOX,
                                                addressIndex = index,
                                            ),
                                        )
                                    }
                                }
                                add {
                                    FormTextRow(
                                        value = address.geo,
                                        onValueChange = { text ->
                                            viewModel.updateAddress(index) { copy(geo = text) }
                                        },
                                        placeholder = stringResource(R.string.field_coordinates),
                                        icon = Icons.Default.MyLocation,
                                    )
                                }
                            }
                            if (!fieldSyntaxEnabled) {
                                add {
                                    FormAddRow(
                                        label = if (expanded) stringResource(R.string.action_show_less) else stringResource(R.string.action_show_more),
                                        onClick = { viewModel.toggleAddressExpanded(index) },
                                    )
                                }
                            }
                        },
                    )
                    GroupedFormSpacer()
                }
                GroupedFormSection(
                    rows = listOf(
                        {
                            FormAddRow(
                                label = stringResource(R.string.add_address),
                                onClick = viewModel::addAddress,
                            )
                        },
                    ),
                )

                GroupedFormSpacer()

                GroupedFormSection(
                    rows = buildList {
                        if (fieldSyntaxEnabled) {
                            addScalarVCardBlock(
                                fieldSyntaxEnabled = true,
                                propertyName = ScalarVCardProperty.BDAY,
                                syntax = state.effectiveScalarSyntax(
                                    ScalarVCardProperty.BDAY,
                                    state.birthday,
                                ),
                                onSyntaxChange = {
                                    viewModel.updateScalarVcardProperty(ScalarVCardProperty.BDAY, it)
                                },
                                showTypeField = true,
                                showAbLabel = false,
                            )
                            if (state.isHiddenFieldVisible(ContactEditHiddenField.PLACE_OF_BIRTH, fieldMode)) {
                                addScalarVCardBlock(
                                    fieldSyntaxEnabled = true,
                                    propertyName = ScalarVCardProperty.BIRTHPLACE,
                                    syntax = state.effectiveScalarSyntax(
                                        ScalarVCardProperty.BIRTHPLACE,
                                        state.birthplace,
                                    ),
                                    onSyntaxChange = {
                                        viewModel.updateScalarVcardProperty(ScalarVCardProperty.BIRTHPLACE, it)
                                    },
                                    showTypeField = true,
                                    showAbLabel = false,
                                )
                            }
                            if (state.isHiddenFieldVisible(ContactEditHiddenField.ANNIVERSARY, fieldMode)) {
                                addScalarVCardBlock(
                                    fieldSyntaxEnabled = true,
                                    propertyName = ScalarVCardProperty.ANNIVERSARY,
                                    syntax = state.effectiveScalarSyntax(
                                        ScalarVCardProperty.ANNIVERSARY,
                                        state.anniversary,
                                    ),
                                    onSyntaxChange = {
                                        viewModel.updateScalarVcardProperty(ScalarVCardProperty.ANNIVERSARY, it)
                                    },
                                    showTypeField = true,
                                    showAbLabel = false,
                                )
                            }
                        } else {
                            add {
                                FormDateRow(
                                    value = state.birthday,
                                    onValueChange = viewModel::updateBirthday,
                                    placeholder = stringResource(R.string.field_birthday),
                                    icon = Icons.Default.Cake,
                                )
                            }
                            if (state.isHiddenFieldVisible(ContactEditHiddenField.PLACE_OF_BIRTH, fieldMode)) {
                                add {
                                    FormPlaceTextAutocompleteRow(
                                        value = state.birthplace,
                                        onValueChange = viewModel::updateBirthplace,
                                        target = PlaceAutocompleteTarget.Birthplace,
                                        autocomplete = placeAutocomplete,
                                        onQueryChange = viewModel::onPlaceFieldQueryChange,
                                        onPickSuggestion = viewModel::applyPlaceSuggestion,
                                        onDismissSuggestions = viewModel::dismissPlaceAutocompleteSuggestions,
                                        placeholder = stringResource(R.string.field_place_of_birth),
                                        valueFocus = fieldFocus.valueFocus(ContactEditHiddenField.PLACE_OF_BIRTH),
                                    )
                                }
                            }
                            if (state.isHiddenFieldVisible(ContactEditHiddenField.ANNIVERSARY, fieldMode)) {
                                add {
                                    FormDateRow(
                                        value = state.anniversary,
                                        onValueChange = viewModel::updateAnniversary,
                                        placeholder = stringResource(R.string.field_anniversary),
                                        icon = Icons.Default.Event,
                                        valueFocus = fieldFocus.valueFocus(ContactEditHiddenField.ANNIVERSARY),
                                    )
                                }
                            }
                        }
                    },
                )

                GroupedFormSpacer()

                val visibleImIndices = state.visibleInstantMessageIndices(fieldMode)
                val showAllImRows = state.showAllInstantMessageRows(fieldMode)
                if (state.instantMessageBlockVisible(fieldMode)) {
                    GroupedFormSection(
                        rows = buildList {
                            visibleImIndices.forEach { index ->
                                val field = state.imAccounts[index]
                                if (fieldSyntaxEnabled) {
                                    addTypedFieldVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        field = field,
                                        defaultPropertyName = "IMPP",
                                        onFieldChange = { viewModel.updateImField(index, it) },
                                        onRemove = if (showAllImRows && state.imAccounts.size > 1) {
                                            { viewModel.removeIm(index) }
                                        } else {
                                            null
                                        },
                                    )
                                } else {
                                    add {
                                        FormInstantMessageRow(
                                            type = field.type,
                                            onTypeChange = { viewModel.updateImType(index, it) },
                                            typeOptions = ContactFieldOptions.im,
                                            value = field.value,
                                            onValueChange = { viewModel.updateIm(index, it) },
                                            placeholder = stringResource(R.string.field_instant_message),
                                            nextcloudExamplePlaceholder = viewModel.cloudIdExample,
                                            searchState = cloudUserSearch,
                                            onOpenPicker = viewModel::resetCloudUserSearch,
                                            onSearchQueryChange = viewModel::searchCloudUsers,
                                            onPickUser = { user ->
                                                viewModel.setImNextcloudFromPicker(index, user)
                                            },
                                            typePickerTitle = stringResource(R.string.field_instant_messaging),
                                            valueFocus = fieldFocus.valueFocus(
                                                ContactEditHiddenField.INSTANT_MESSAGE,
                                                rowIndex = index,
                                            ),
                                            onRemove = if (showAllImRows && state.imAccounts.size > 1) {
                                                { viewModel.removeIm(index) }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                            if (showAllImRows) {
                                add {
                                    FormAddRow(
                                        label = stringResource(R.string.add_instant_message),
                                        onClick = viewModel::addIm,
                                    )
                                }
                            }
                        },
                    )
                    GroupedFormSpacer()
                }

                val visibleSocialIndices = state.visibleSocialProfileIndices(fieldMode)
                val showAllSocialRows = state.showAllSocialProfileRows(fieldMode)
                if (state.socialProfileBlockVisible(fieldMode)) {
                    GroupedFormSection(
                        rows = buildList {
                            visibleSocialIndices.forEach { index ->
                                val field = state.socialProfiles[index]
                                if (fieldSyntaxEnabled) {
                                    addTypedFieldVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        field = field,
                                        defaultPropertyName = "X-SOCIALPROFILE",
                                        onFieldChange = { viewModel.updateSocialProfileField(index, it) },
                                        onRemove = if (showAllSocialRows && state.socialProfiles.size > 1) {
                                            { viewModel.removeSocialProfile(index) }
                                        } else {
                                            null
                                        },
                                    )
                                } else {
                                    add {
                                        FormSocialProfileRow(
                                            type = field.type,
                                            onTypeChange = { viewModel.updateSocialProfileType(index, it) },
                                            typeOptions = ContactFieldOptions.social,
                                            value = field.value,
                                            onValueChange = { viewModel.updateSocialProfile(index, it) },
                                            placeholder = stringResource(R.string.field_social_profile),
                                            nextcloudExamplePlaceholder = viewModel.socialNextcloudExample,
                                            searchState = cloudUserSearch,
                                            onOpenPicker = viewModel::resetCloudUserSearch,
                                            onSearchQueryChange = viewModel::searchCloudUsers,
                                            onPickUser = { user ->
                                                viewModel.setSocialNextcloudFromPicker(index, user)
                                            },
                                            typePickerTitle = stringResource(R.string.field_social_network),
                                            valueFocus = fieldFocus.valueFocus(
                                                ContactEditHiddenField.SOCIAL_PROFILE,
                                                rowIndex = index,
                                            ),
                                            onRemove = if (showAllSocialRows && state.socialProfiles.size > 1) {
                                                { viewModel.removeSocialProfile(index) }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                            if (showAllSocialRows) {
                                add {
                                    FormAddRow(
                                        label = stringResource(R.string.add_social_profile),
                                        onClick = viewModel::addSocialProfile,
                                    )
                                }
                            }
                        },
                    )
                    GroupedFormSpacer()
                }

                if (state.isHiddenFieldVisible(ContactEditHiddenField.FEDERATED_CLOUD_ID, fieldMode)) {
                    GroupedFormSection(
                        rows = buildList {
                            state.cloudIds.forEachIndexed { index, field ->
                                if (fieldSyntaxEnabled) {
                                    addTypedFieldVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        field = field,
                                        defaultPropertyName = "CLOUD",
                                        onFieldChange = { viewModel.updateCloudIdField(index, it) },
                                        onRemove = if (state.cloudIds.size > 1) {
                                            { viewModel.removeCloudId(index) }
                                        } else {
                                            null
                                        },
                                    )
                                } else {
                                    add {
                                        FormCloudIdRow(
                                            type = field.type,
                                            onTypeChange = { viewModel.updateCloudIdType(index, it) },
                                            typeOptions = ContactFieldOptions.cloudIds,
                                            value = field.value,
                                            onValueChange = { viewModel.updateCloudId(index, it) },
                                            examplePlaceholder = viewModel.cloudIdExample,
                                            searchState = cloudUserSearch,
                                            onOpenPicker = viewModel::resetCloudUserSearch,
                                            onSearchQueryChange = viewModel::searchCloudUsers,
                                            onPickUser = { user ->
                                                viewModel.setCloudIdFromPicker(index, user.cloudId)
                                            },
                                            typePickerTitle = stringResource(R.string.picker_cloud_type),
                                            valueFocus = fieldFocus.valueFocus(
                                                ContactEditHiddenField.FEDERATED_CLOUD_ID,
                                                rowIndex = index,
                                            ),
                                            onRemove = if (state.cloudIds.size > 1) {
                                                { viewModel.removeCloudId(index) }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                            add {
                                FormAddRow(
                                    label = stringResource(R.string.add_federated_cloud_id),
                                    onClick = viewModel::addCloudId,
                                )
                            }
                        },
                    )
                    GroupedFormSpacer()
                }

                if (!fieldSyntaxEnabled && cloudUserSearch.canSearch) {
                    GroupedFormSection(
                        rows = listOf {
                            FormAddRow(
                                label = stringResource(R.string.add_nextcloud_account),
                                onClick = {
                                    viewModel.resetCloudUserSearch()
                                    showNextcloudAccountPicker = true
                                },
                            )
                        },
                    )
                }

                if (state.isHiddenFieldVisible(ContactEditHiddenField.WEBSITE, fieldMode)) {
                    if (fieldSyntaxEnabled || cloudUserSearch.canSearch) {
                        GroupedFormSpacer()
                    }
                    GroupedFormSection(
                        rows = buildList {
                            state.urls.forEachIndexed { index, field ->
                                if (fieldSyntaxEnabled) {
                                    addTypedFieldVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        field = field,
                                        defaultPropertyName = "URL",
                                        onFieldChange = { viewModel.updateUrlField(index, it) },
                                        onRemove = if (state.urls.size > 1) {
                                            { viewModel.removeUrl(index) }
                                        } else {
                                            null
                                        },
                                    )
                                } else {
                                    add {
                                        FormTypedTextRow(
                                            type = field.type,
                                            onTypeChange = { viewModel.updateUrlType(index, it) },
                                            typeOptions = ContactFieldOptions.urls,
                                            value = field.value,
                                            onValueChange = { viewModel.updateUrl(index, it) },
                                            placeholder = stringResource(R.string.field_website),
                                            icon = Icons.Default.Language,
                                            keyboardOptions = IdentifierKeyboard.uri,
                                            valueFocus = fieldFocus.valueFocus(
                                                ContactEditHiddenField.WEBSITE,
                                                rowIndex = index,
                                            ),
                                            onRemove = if (state.urls.size > 1) {
                                                { viewModel.removeUrl(index) }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                            add {
                                FormAddRow(
                                    label = stringResource(R.string.add_website),
                                    onClick = viewModel::addUrl,
                                )
                            }
                        },
                    )
                }

                if (state.isHiddenFieldVisible(ContactEditHiddenField.TIME_ZONE, fieldMode)) {
                    GroupedFormSpacer()
                    GroupedFormSection(
                        rows = buildList {
                            if (fieldSyntaxEnabled) {
                                addScalarVCardBlock(
                                    fieldSyntaxEnabled = true,
                                    propertyName = ScalarVCardProperty.TZ,
                                    syntax = state.effectiveScalarSyntax(
                                        ScalarVCardProperty.TZ,
                                        state.timeZone,
                                    ),
                                    onSyntaxChange = {
                                        viewModel.updateScalarVcardProperty(ScalarVCardProperty.TZ, it)
                                    },
                                    showTypeField = true,
                                    showAbLabel = false,
                                )
                            } else {
                                add {
                                    FormSelectRow(
                                        value = state.timeZone,
                                        onValueChange = viewModel::updateTimeZone,
                                        options = TimeZoneOptions.all,
                                        placeholder = stringResource(R.string.field_time_zone),
                                        icon = Icons.Default.Schedule,
                                        valueFocus = fieldFocus.valueFocus(ContactEditHiddenField.TIME_ZONE),
                                    )
                                }
                            }
                        },
                    )
                }

                if (state.isHiddenFieldVisible(ContactEditHiddenField.LANGUAGE, fieldMode)) {
                    GroupedFormSpacer()
                    GroupedFormSection(
                        rows = buildList {
                            state.languages.forEachIndexed { index, field ->
                                if (fieldSyntaxEnabled) {
                                    addTypedFieldVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        field = field,
                                        defaultPropertyName = "LANG",
                                        onFieldChange = { viewModel.updateLanguageField(index, it) },
                                        onRemove = if (state.languages.size > 1) {
                                            { viewModel.removeLanguage(index) }
                                        } else {
                                            null
                                        },
                                        showAbLabel = false,
                                    )
                                } else {
                                    add {
                                        FormSelectRow(
                                            value = field.value,
                                            onValueChange = { viewModel.updateLanguage(index, it) },
                                            options = LanguageOptions.all,
                                            placeholder = stringResource(R.string.field_language),
                                            icon = Icons.Default.Translate,
                                            pickerTitle = stringResource(R.string.field_spoken_languages),
                                            onRemove = if (state.languages.size > 1) {
                                                { viewModel.removeLanguage(index) }
                                            } else {
                                                null
                                            },
                                            labelForValue = LanguageOptions::label,
                                            valueFocus = fieldFocus.valueFocus(
                                                ContactEditHiddenField.LANGUAGE,
                                                rowIndex = index,
                                            ),
                                        )
                                    }
                                }
                            }
                            add {
                                FormAddRow(
                                    label = stringResource(R.string.add_language),
                                    onClick = viewModel::addLanguage,
                                )
                            }
                        },
                    )
                }

                if (state.isHiddenFieldVisible(ContactEditHiddenField.GENDER, fieldMode) ||
                    state.isHiddenFieldVisible(ContactEditHiddenField.RELATIONSHIP_TO_YOU, fieldMode)
                ) {
                    GroupedFormSpacer()
                    GroupedFormSection(
                        rows = buildList {
                            if (state.isHiddenFieldVisible(ContactEditHiddenField.GENDER, fieldMode)) {
                                if (fieldSyntaxEnabled) {
                                    addScalarVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        propertyName = ScalarVCardProperty.GENDER,
                                        syntax = state.effectiveScalarSyntax(
                                            ScalarVCardProperty.GENDER,
                                            state.gender,
                                        ),
                                        onSyntaxChange = {
                                            viewModel.updateScalarVcardProperty(ScalarVCardProperty.GENDER, it)
                                        },
                                        showTypeField = true,
                                        showAbLabel = false,
                                    )
                                } else {
                                    add {
                                        FormSelectRow(
                                            value = state.gender,
                                            onValueChange = viewModel::updateGender,
                                            options = ContactFieldOptions.gender,
                                            placeholder = stringResource(R.string.field_gender),
                                            icon = Icons.Default.Wc,
                                            valueFocus = fieldFocus.valueFocus(ContactEditHiddenField.GENDER),
                                        )
                                    }
                                }
                            }
                            if (state.isHiddenFieldVisible(ContactEditHiddenField.RELATIONSHIP_TO_YOU, fieldMode)) {
                                if (fieldSyntaxEnabled) {
                                    addScalarVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        propertyName = ScalarVCardProperty.RELATIONSHIP,
                                        syntax = state.effectiveScalarSyntax(
                                            ScalarVCardProperty.RELATIONSHIP,
                                            state.relationshipToYou,
                                        ),
                                        onSyntaxChange = {
                                            viewModel.updateScalarVcardProperty(ScalarVCardProperty.RELATIONSHIP, it)
                                        },
                                        showTypeField = true,
                                        showAbLabel = false,
                                    )
                                } else {
                                    add {
                                        FormSelectRow(
                                            value = state.relationshipToYou,
                                            onValueChange = viewModel::updateRelationshipToYou,
                                            options = ContactFieldOptions.relationshipToYou,
                                            placeholder = stringResource(R.string.field_relationship),
                                            icon = Icons.Default.People,
                                            valueFocus = fieldFocus.valueFocus(ContactEditHiddenField.RELATIONSHIP_TO_YOU),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                if (state.isHiddenFieldVisible(ContactEditHiddenField.RELATED_PERSON, fieldMode)) {
                    GroupedFormSpacer()
                    GroupedFormSection(
                        rows = buildList {
                            state.relatedNames.forEachIndexed { index, field ->
                                if (fieldSyntaxEnabled) {
                                    addTypedFieldVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        field = field,
                                        defaultPropertyName = "RELATED",
                                        onFieldChange = { viewModel.updateRelatedField(index, it) },
                                        onRemove = if (state.relatedNames.size > 1) {
                                            { viewModel.removeRelated(index) }
                                        } else {
                                            null
                                        },
                                    )
                                } else {
                                    add {
                                        FormTypedTextRow(
                                            type = field.type,
                                            onTypeChange = { viewModel.updateRelatedType(index, it) },
                                            typeOptions = ContactFieldOptions.related,
                                            value = field.value,
                                            onValueChange = { viewModel.updateRelated(index, it) },
                                            placeholder = stringResource(R.string.field_related_person),
                                            icon = Icons.Default.People,
                                            typePickerTitle = stringResource(R.string.picker_related_type),
                                            valueFocus = fieldFocus.valueFocus(
                                                ContactEditHiddenField.RELATED_PERSON,
                                                rowIndex = index,
                                            ),
                                            onRemove = if (state.relatedNames.size > 1) {
                                                { viewModel.removeRelated(index) }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                            add {
                                FormAddRow(
                                    label = stringResource(R.string.add_related_person),
                                    onClick = viewModel::addRelated,
                                )
                            }
                        },
                    )
                }

                if (state.customFields.isNotEmpty()) {
                    GroupedFormSpacer()
                    GroupedFormSection(
                        rows = buildList {
                            state.customFields.forEachIndexed { index, field ->
                                if (fieldSyntaxEnabled) {
                                    addCustomFieldVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        field = field,
                                        onFieldChange = { updated ->
                                            viewModel.updateCustomFieldFromVCard(index, updated)
                                        },
                                        onRemove = { viewModel.removeCustomField(index) },
                                    )
                                } else {
                                    add {
                                        FormTextRow(
                                            value = field.label,
                                            onValueChange = { viewModel.updateCustomFieldLabel(index, it) },
                                            placeholder = stringResource(R.string.field_name_placeholder),
                                            icon = Icons.Default.ShortText,
                                        )
                                    }
                                    add {
                                        FormTextRow(
                                            value = field.value,
                                            onValueChange = { viewModel.updateCustomFieldValue(index, it) },
                                            placeholder = stringResource(R.string.field_value),
                                            icon = Icons.AutoMirrored.Filled.Notes,
                                            onRemove = { viewModel.removeCustomField(index) },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                GroupedFormSpacer()

                GroupedFormSection(
                    rows = buildList {
                        if (fieldSyntaxEnabled) {
                            addScalarVCardBlock(
                                fieldSyntaxEnabled = true,
                                propertyName = ScalarVCardProperty.CATEGORIES,
                                syntax = state.effectiveScalarSyntax(
                                    ScalarVCardProperty.CATEGORIES,
                                    state.categories,
                                ),
                                onSyntaxChange = {
                                    viewModel.updateScalarVcardProperty(ScalarVCardProperty.CATEGORIES, it)
                                },
                                showTypeField = true,
                                showAbLabel = false,
                            )
                        } else {
                            add {
                                FormGroupsEditor(
                                    categories = state.categories,
                                    existingGroups = existingGroups,
                                    onAdd = viewModel::addCategory,
                                    onRemove = viewModel::removeCategory,
                                )
                            }
                        }
                    },
                )

                GroupedFormSpacer()

                if (fieldSyntaxEnabled) {
                    GroupedFormSection(
                        rows = buildList {
                            addScalarVCardBlock(
                                fieldSyntaxEnabled = true,
                                propertyName = ScalarVCardProperty.NOTE,
                                syntax = state.effectiveScalarSyntax(
                                    ScalarVCardProperty.NOTE,
                                    state.notes,
                                ),
                                onSyntaxChange = {
                                    viewModel.updateScalarVcardProperty(ScalarVCardProperty.NOTE, it)
                                },
                                showTypeField = true,
                                showAbLabel = false,
                                multilineValue = true,
                            )
                        },
                    )
                } else {
                    ContactNotesGroupedField(
                        notes = state.notes,
                        onNotesChange = viewModel::updateNotes,
                        notesMarkdown = state.notesMarkdown,
                        onNotesMarkdownChange = viewModel::updateNotesMarkdown,
                    )
                }

                if (state.isHiddenFieldVisible(ContactEditHiddenField.DATE_OF_DEATH, fieldMode) ||
                    state.isHiddenFieldVisible(ContactEditHiddenField.PLACE_OF_DEATH, fieldMode)
                ) {
                    GroupedFormSpacer()
                    GroupedFormSection(
                        rows = buildList {
                            if (state.isHiddenFieldVisible(ContactEditHiddenField.DATE_OF_DEATH, fieldMode)) {
                                if (fieldSyntaxEnabled) {
                                    addScalarVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        propertyName = ScalarVCardProperty.DEATHDATE,
                                        syntax = state.effectiveScalarSyntax(
                                            ScalarVCardProperty.DEATHDATE,
                                            state.deathDate,
                                        ),
                                        onSyntaxChange = {
                                            viewModel.updateScalarVcardProperty(ScalarVCardProperty.DEATHDATE, it)
                                        },
                                        showTypeField = true,
                                        showAbLabel = false,
                                    )
                                } else {
                                    add {
                                        FormDateRow(
                                            value = state.deathDate,
                                            onValueChange = viewModel::updateDeathDate,
                                            placeholder = stringResource(R.string.field_date_of_death),
                                            icon = Icons.Default.EventBusy,
                                            valueFocus = fieldFocus.valueFocus(ContactEditHiddenField.DATE_OF_DEATH),
                                        )
                                    }
                                }
                            }
                            if (state.isHiddenFieldVisible(ContactEditHiddenField.PLACE_OF_DEATH, fieldMode)) {
                                if (fieldSyntaxEnabled) {
                                    addScalarVCardBlock(
                                        fieldSyntaxEnabled = true,
                                        propertyName = ScalarVCardProperty.DEATHPLACE,
                                        syntax = state.effectiveScalarSyntax(
                                            ScalarVCardProperty.DEATHPLACE,
                                            state.deathPlace,
                                        ),
                                        onSyntaxChange = {
                                            viewModel.updateScalarVcardProperty(ScalarVCardProperty.DEATHPLACE, it)
                                        },
                                        showTypeField = true,
                                        showAbLabel = false,
                                    )
                                } else {
                                    add {
                                        FormPlaceTextAutocompleteRow(
                                            value = state.deathPlace,
                                            onValueChange = viewModel::updateDeathPlace,
                                            target = PlaceAutocompleteTarget.DeathPlace,
                                            autocomplete = placeAutocomplete,
                                            onQueryChange = viewModel::onPlaceFieldQueryChange,
                                            onPickSuggestion = viewModel::applyPlaceSuggestion,
                                            onDismissSuggestions = viewModel::dismissPlaceAutocompleteSuggestions,
                                            placeholder = stringResource(R.string.field_place_of_death),
                                            valueFocus = fieldFocus.valueFocus(ContactEditHiddenField.PLACE_OF_DEATH),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                GroupedFormSpacer()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showFullFields) {
                        FilledTonalButton(
                            onClick = viewModel::addCustomField,
                            modifier = Modifier.defaultMinSize(
                                minWidth = 180.dp,
                                minHeight = 52.dp,
                            ),
                            contentPadding = PaddingValues(
                                horizontal = 32.dp,
                                vertical = 14.dp,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.field_custom),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { showAddFieldPicker = true },
                            modifier = Modifier.defaultMinSize(
                                minWidth = 180.dp,
                                minHeight = 52.dp,
                            ),
                            contentPadding = PaddingValues(
                                horizontal = 32.dp,
                                vertical = 14.dp,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.field_other_section),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    if (settingsFieldSyntaxEnabled) {
                        ContactVCardModeButton(
                            enabled = state.editFieldSyntaxEnabled,
                            onClick = viewModel::toggleEditFieldSyntax,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(88.dp))
            }
        }
    }

    if (showAddFieldPicker) {
        ContactEditAddFieldPickerSheet(
            fields = addableHiddenFields,
            onSelect = { field ->
                val nextState = state.applyRevealHiddenField(field)
                viewModel.revealHiddenField(field)
                showAddFieldPicker = false
                fieldFocus.scheduleRevealFocus(field, nextState)
            },
            onSelectCustomField = {
                viewModel.addCustomField()
                showAddFieldPicker = false
            },
            onDismiss = { showAddFieldPicker = false },
        )
    }
}

@Composable
private fun crossLinkTitle(prompt: NextcloudCrossLinkPrompt): String {
    val count = listOf(
        prompt.addSocialProfile,
        prompt.addInstantMessage,
        prompt.addFederatedCloudId,
    ).count { it }
    return when {
        count != 1 -> stringResource(R.string.also_add_other)
        prompt.addSocialProfile -> stringResource(R.string.also_add_social)
        prompt.addInstantMessage -> stringResource(R.string.also_add_im)
        else -> stringResource(R.string.also_add_cloud)
    }
}

@Composable
private fun crossLinkMessage(prompt: NextcloudCrossLinkPrompt): String {
    val parts = buildList {
        if (prompt.addSocialProfile) add(stringResource(R.string.bullet_add_social, prompt.profileUrl))
        if (prompt.addInstantMessage) add(stringResource(R.string.bullet_add_im, prompt.cloudId))
        if (prompt.addFederatedCloudId) add(stringResource(R.string.bullet_add_cloud, prompt.cloudId))
    }
    if (parts.size == 1) {
        return when {
            prompt.addSocialProfile -> stringResource(R.string.add_social_too, prompt.profileUrl)
            prompt.addInstantMessage -> stringResource(R.string.add_im_too, prompt.cloudId)
            else -> stringResource(R.string.add_cloud_too, prompt.cloudId)
        }
    }
    return parts.joinToString("\n")
}

@Composable
private fun ContactVCardModeButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(
            text = if (enabled) stringResource(R.string.action_disable_vcard) else stringResource(R.string.action_enable_vcard),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
