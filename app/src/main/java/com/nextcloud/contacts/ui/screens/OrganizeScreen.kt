package com.nextcloud.contacts.ui.screens

import com.nextcloud.contacts.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContactEmergency
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.data.binDaysRemaining
import com.nextcloud.contacts.data.ContactDuplicates
import com.nextcloud.contacts.data.ContactMerge
import com.nextcloud.contacts.data.MergeScalarConflict
import com.nextcloud.contacts.data.MergeScalarSnapshot
import com.nextcloud.contacts.data.MergeSingletonField
import com.nextcloud.contacts.data.prefs.CalendarEventDescriptionMode
import com.nextcloud.contacts.data.prefs.ContactDatesDestination
import com.nextcloud.contacts.platform.CalendarPermissionRequester
import com.nextcloud.contacts.platform.ContactCalendarEventKind
import com.nextcloud.contacts.platform.EmergencyContacts
import com.nextcloud.contacts.ui.components.AppSnackbarHost
import com.nextcloud.contacts.ui.components.ContactAvatar
import com.nextcloud.contacts.ui.components.IllustratedEmptyState
import com.nextcloud.contacts.ui.components.ObserveUiNotices
import com.nextcloud.contacts.ui.util.rememberCalendarPermissionAction
import com.nextcloud.contacts.ui.util.rememberDeviceContactsImportAction
import com.nextcloud.contacts.ui.util.rememberVcfExportDocumentAction
import com.nextcloud.contacts.ui.util.rememberVcfImportDocumentAction
import com.nextcloud.contacts.ui.util.rememberWriteContactsExportAction
import com.nextcloud.contacts.ui.viewmodel.DeletedContactItem
import com.nextcloud.contacts.ui.viewmodel.DuplicateContactSide
import com.nextcloud.contacts.ui.viewmodel.DuplicateFieldLine
import com.nextcloud.contacts.ui.viewmodel.OrganizeViewModel
import com.nextcloud.contacts.ui.viewmodel.SettingsViewModel

private val OrganizeCardShape = RoundedCornerShape(24.dp)
private val MergeValueShape = RoundedCornerShape(16.dp)
private val MergeAvatarSize = 80.dp
private val MergeLabelGap = 4.dp
private val MergePhotoNameGap = 8.dp
private val MergeNameDividerGap = 8.dp
private val MergeSwapButtonSize = 48.dp
private val MergeValueHeight = 56.dp
private val MergeValuePhotoSize = 40.dp

@Composable
private fun ButtonProgressIndicator() {
    CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(
    onBack: () -> Unit,
    onOpenBin: () -> Unit,
    onOpenImportDevice: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenMerge: () -> Unit,
    viewModel: OrganizeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    OrganizeScaffold(
        title = stringResource(R.string.screen_organize),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = OrganizeCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OrganizeMenuRow(
                        icon = Icons.Filled.ImportExport,
                        title = stringResource(R.string.section_import_device),
                        summary = stringResource(R.string.organize_import_summary),
                        onClick = onOpenImportDevice,
                    )
                    OrganizeMenuDivider()
                    OrganizeMenuRow(
                        icon = Icons.Filled.Merge,
                        title = stringResource(R.string.section_merge_fix),
                        summary = stringResource(R.string.organize_merge_summary),
                        onClick = onOpenMerge,
                    )
                    OrganizeMenuDivider()
                    OrganizeMenuRow(
                        icon = Icons.Filled.CalendarMonth,
                        title = stringResource(R.string.section_calendar),
                        summary = stringResource(R.string.organize_calendar_summary),
                        onClick = onOpenCalendar,
                    )
                    OrganizeMenuDivider()
                    OrganizeMenuRow(
                        icon = Icons.Filled.ContactEmergency,
                        title = stringResource(R.string.section_emergency_contacts),
                        summary = stringResource(R.string.emergency_contacts_opens_phone),
                        onClick = {
                            val opened = EmergencyContacts.openEditor(context)
                            if (!opened) {
                                viewModel.showMessage(context.getString(R.string.msg_emergency_unavailable))
                            }
                        },
                    )
                    OrganizeMenuDivider()
                    OrganizeMenuRow(
                        icon = Icons.Filled.Delete,
                        title = stringResource(R.string.section_bin),
                        summary = stringResource(R.string.organize_bin_summary),
                        onClick = onOpenBin,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrganizeMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    )
}

@Composable
private fun OrganizeMenuRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeBinScreen(
    onBack: () -> Unit,
    viewModel: OrganizeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    OrganizeScaffold(
        title = stringResource(R.string.section_bin),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        val deleted = state.deleted
        if (deleted == null) {
            Spacer(
                modifier = Modifier
                    .padding(padding)
                    .height(8.dp),
            )
        } else if (deleted.isEmpty()) {
            IllustratedEmptyState(
                icon = Icons.Outlined.DeleteOutline,
                message = stringResource(R.string.empty_bin),
                supporting = stringResource(R.string.bin_kept_days),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                SupportingLine(stringResource(R.string.bin_kept_days))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    deleted.forEach { contact ->
                        key(contact.id) {
                            BinContactCard(
                                contact = contact,
                                restoreEnabled = !state.busy,
                                onRestore = { viewModel.restore(contact.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BinContactCard(
    contact: DeletedContactItem,
    restoreEnabled: Boolean,
    onRestore: () -> Unit,
) {
    val name = contact.displayName.ifBlank { stringResource(R.string.fallback_contact) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OrganizeCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(
                contactId = contact.id,
                displayName = contact.displayName,
                size = 40.dp,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val daysLeft = binDaysRemaining(contact.deletedAt, System.currentTimeMillis())
                Text(
                    text = pluralStringResource(R.plurals.bin_days_left, daysLeft, daysLeft),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onRestore,
                enabled = restoreEnabled,
            ) {
                Text(stringResource(R.string.action_restore))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeImportScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val settings by settingsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val container = (context.applicationContext as ContactsApp).container
    val snackbarHostState = remember { SnackbarHostState() }

    val importFromDevice = rememberDeviceContactsImportAction(
        onGranted = settingsViewModel::importFromDevice,
        onDenied = { settingsViewModel.showMessage(context.getString(R.string.msg_contacts_permission)) },
    )
    val enableDeviceExport = rememberWriteContactsExportAction(
        requester = container.contactsPermissionRequester,
        onGranted = { settingsViewModel.setExportToDeviceContacts(true) },
        onDenied = {
            settingsViewModel.showMessage(
                context.getString(R.string.msg_contacts_settings_phonebook),
            )
        },
    )
    val exportContactsVcf = rememberVcfExportDocumentAction(
        onUriReady = settingsViewModel::exportContactsToVcf,
    )
    val importContactsVcf = rememberVcfImportDocumentAction(
        onTextRead = settingsViewModel::importContactsFromVcf,
        onReadFailed = { settingsViewModel.showMessage(context.getString(R.string.msg_could_not_read_file)) },
    )
    val importExportBusy = settings.isSyncing || settings.isImporting || settings.isExportingVcf

    LaunchedEffect(settings.message) {
        settings.message?.let {
            snackbarHostState.showSnackbar(it)
            settingsViewModel.clearMessage()
        }
    }
    ObserveUiNotices { settingsViewModel.showMessage(it) }

    OrganizeScaffold(
        title = stringResource(R.string.section_import_device),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = OrganizeCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        colors = rowColors,
                        headlineContent = { Text(stringResource(R.string.setting_show_in_phone)) },
                        supportingContent = {
                            Text(stringResource(R.string.setting_show_in_phone_summary))
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.exportToDeviceContacts,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        enableDeviceExport()
                                    } else {
                                        settingsViewModel.setExportToDeviceContacts(false)
                                    }
                                },
                            )
                        },
                    )
                    ListItem(
                        colors = rowColors,
                        modifier = Modifier.clickable(enabled = !importExportBusy) { exportContactsVcf() },
                        headlineContent = { Text(stringResource(R.string.setting_export_contacts)) },
                        supportingContent = {
                            Text(stringResource(R.string.setting_export_contacts_summary))
                        },
                        trailingContent = {
                            if (settings.isExportingVcf) {
                                ButtonProgressIndicator()
                            }
                        },
                    )
                    ListItem(
                        colors = rowColors,
                        modifier = Modifier.clickable(enabled = !importExportBusy) { importContactsVcf() },
                        headlineContent = { Text(stringResource(R.string.setting_import_contacts)) },
                        supportingContent = {
                            Text(stringResource(R.string.setting_import_contacts_summary))
                        },
                        trailingContent = {
                            if (settings.isImporting) {
                                ButtonProgressIndicator()
                            }
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = importFromDevice,
                modifier = Modifier.fillMaxWidth(),
                enabled = !importExportBusy,
            ) {
                Text(stringResource(R.string.import_from_device))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeCalendarScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val settings by settingsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val container = (context.applicationContext as ContactsApp).container
    val snackbarHostState = remember { SnackbarHostState() }

    data class PendingKindDestination(
        val kind: ContactCalendarEventKind,
        val destination: ContactDatesDestination,
    )
    var pendingKindDestination by remember { mutableStateOf<PendingKindDestination?>(null) }
    val applyKindDestination = rememberCalendarPermissionAction(
        requester = container.calendarPermissionRequester,
        onGranted = {
            pendingKindDestination?.let { (kind, destination) ->
                settingsViewModel.setKindDestination(kind, destination)
            }
            pendingKindDestination = null
        },
        onDenied = {
            val kind = pendingKindDestination?.kind
            pendingKindDestination = null
            val message = when (kind) {
                ContactCalendarEventKind.BIRTHDAY ->
                    context.getString(R.string.msg_calendar_birthdays)
                ContactCalendarEventKind.ANNIVERSARY ->
                    context.getString(R.string.msg_calendar_anniversaries)
                ContactCalendarEventKind.DEATH ->
                    context.getString(R.string.msg_calendar_deaths)
                null -> null
            }
            message?.let(settingsViewModel::showMessage)
        },
    )
    fun selectedDestination(kind: ContactCalendarEventKind): ContactDatesDestination = when (kind) {
        ContactCalendarEventKind.BIRTHDAY -> settings.birthdaysDestination
        ContactCalendarEventKind.ANNIVERSARY -> settings.anniversariesDestination
        ContactCalendarEventKind.DEATH -> settings.datesOfDeathDestination
    }
    fun requestKindDestination(kind: ContactCalendarEventKind, destination: ContactDatesDestination) {
        if (destination == selectedDestination(kind)) {
            val missingCalendarPermission = destination.includesSystem() &&
                !CalendarPermissionRequester.hasCalendarPermissions(context)
            if (!missingCalendarPermission) return
        }
        if (destination.includesSystem()) {
            pendingKindDestination = PendingKindDestination(kind, destination)
            applyKindDestination()
        } else {
            settingsViewModel.setKindDestination(kind, destination)
        }
    }

    LaunchedEffect(settings.message) {
        settings.message?.let {
            snackbarHostState.showSnackbar(it)
            settingsViewModel.clearMessage()
        }
    }
    ObserveUiNotices { settingsViewModel.showMessage(it) }
    LaunchedEffect(Unit) {
        settingsViewModel.refreshNextcloudCalendarAvailability()
    }

    OrganizeScaffold(
        title = stringResource(R.string.section_calendar),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = OrganizeCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        colors = rowColors,
                        headlineContent = { Text(stringResource(R.string.field_birthday)) },
                        supportingContent = {
                            ContactDatesDestinationSegmentedControl(
                                selected = settings.birthdaysDestination,
                                nextcloudCalendarAvailable = settings.nextcloudCalendarAvailable,
                                onSelected = { requestKindDestination(ContactCalendarEventKind.BIRTHDAY, it) },
                            )
                        },
                    )
                    ListItem(
                        colors = rowColors,
                        headlineContent = { Text(stringResource(R.string.field_anniversary)) },
                        supportingContent = {
                            ContactDatesDestinationSegmentedControl(
                                selected = settings.anniversariesDestination,
                                nextcloudCalendarAvailable = settings.nextcloudCalendarAvailable,
                                onSelected = { requestKindDestination(ContactCalendarEventKind.ANNIVERSARY, it) },
                            )
                        },
                    )
                    ListItem(
                        colors = rowColors,
                        headlineContent = { Text(stringResource(R.string.field_date_of_death)) },
                        supportingContent = {
                            ContactDatesDestinationSegmentedControl(
                                selected = settings.datesOfDeathDestination,
                                nextcloudCalendarAvailable = settings.nextcloudCalendarAvailable,
                                onSelected = { requestKindDestination(ContactCalendarEventKind.DEATH, it) },
                            )
                        },
                    )
                    ListItem(
                        colors = rowColors,
                        headlineContent = { Text(stringResource(R.string.label_description)) },
                        supportingContent = {
                            CalendarEventDescriptionSegmentedControl(
                                selected = settings.calendarEventDescriptionMode,
                                onSelected = settingsViewModel::setCalendarEventDescriptionMode,
                            )
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private data class PendingPairMerge(
    val keeperId: Long,
    val duplicateId: Long,
    val fieldsFromDuplicate: Set<MergeSingletonField>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeMergeScreen(
    onBack: () -> Unit,
    viewModel: OrganizeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingMerge by remember { mutableStateOf<PendingPairMerge?>(null) }
    var swappedPairs by remember { mutableStateOf(setOf<String>()) }
    var fieldsFromRight by remember { mutableStateOf(mapOf<String, Set<MergeSingletonField>>()) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    pendingMerge?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingMerge = null },
            title = { Text(stringResource(R.string.dialog_merge_contacts)) },
            text = { Text(stringResource(R.string.dialog_merge_contacts_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingMerge = null
                        viewModel.merge(
                            pending.keeperId,
                            pending.duplicateId,
                            pending.fieldsFromDuplicate,
                        )
                    },
                    enabled = !state.busy,
                ) {
                    Text(stringResource(R.string.action_merge))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMerge = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    OrganizeScaffold(
        title = stringResource(R.string.section_merge_fix),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        val groups = state.duplicateMatches
        if (groups == null) {
            Spacer(
                modifier = Modifier
                    .padding(padding)
                    .height(8.dp),
            )
        } else if (groups.isEmpty()) {
            IllustratedEmptyState(
                icon = Icons.Outlined.People,
                message = stringResource(R.string.empty_no_duplicates),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                var shownSection = false
                listOf(
                    ContactDuplicates.Match.PHONE to R.string.merge_same_phone,
                    ContactDuplicates.Match.EMAIL to R.string.merge_same_email,
                    ContactDuplicates.Match.NAME to R.string.merge_same_name,
                ).forEach { (match, titleRes) ->
                    val items = groups.filter { it.match == match }
                    if (items.isEmpty()) return@forEach
                    if (shownSection) {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                    shownSection = true
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 6.dp),
                    )
                    var pairIndex = 0
                    items.forEach { item ->
                        val anchor = item.contacts.first()
                        val others = item.contacts.filter { it.id != anchor.id }
                        others.forEach { other ->
                            if (pairIndex > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            pairIndex++
                            val pairKey = "${item.key}|${other.id}"
                            val swapped = pairKey in swappedPairs
                            val left = if (swapped) other else anchor
                            val right = if (swapped) anchor else other
                            key(pairKey) {
                                MergePairCard(
                                    match = item.match,
                                    sharedValue = item.sharedValue,
                                    primary = left,
                                    duplicate = right,
                                    enabled = !state.busy,
                                    keptFromRight = fieldsFromRight[pairKey].orEmpty(),
                                    onSwapProfiles = {
                                        swappedPairs = if (pairKey in swappedPairs) {
                                            swappedPairs - pairKey
                                        } else {
                                            swappedPairs + pairKey
                                        }
                                        fieldsFromRight = fieldsFromRight - pairKey
                                    },
                                    onToggleField = { field, keepRight ->
                                        if (ContactMerge.canKeepFromOther(field)) {
                                            val current = fieldsFromRight[pairKey].orEmpty()
                                            val next = if (keepRight) current + field else current - field
                                            fieldsFromRight = if (next.isEmpty()) {
                                                fieldsFromRight - pairKey
                                            } else {
                                                fieldsFromRight + (pairKey to next)
                                            }
                                        }
                                    },
                                    onMerge = {
                                        pendingMerge = PendingPairMerge(
                                            left.id,
                                            right.id,
                                            fieldsFromRight[pairKey].orEmpty()
                                                .filter(ContactMerge::canKeepFromOther)
                                                .toSet(),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergePairCard(
    match: ContactDuplicates.Match,
    sharedValue: String,
    primary: DuplicateContactSide,
    duplicate: DuplicateContactSide,
    enabled: Boolean,
    keptFromRight: Set<MergeSingletonField>,
    onSwapProfiles: () -> Unit,
    onToggleField: (MergeSingletonField, Boolean) -> Unit,
    onMerge: () -> Unit,
) {
    val conflicts = ContactMerge.singletonConflicts(primary.scalars, duplicate.scalars)
    val keptRows = mergeKeptRows(primary, duplicate, sharedValue)
    var expanded by remember { mutableStateOf(false) }
    val matchLabelRes = when (match) {
        ContactDuplicates.Match.PHONE -> R.string.field_phone
        ContactDuplicates.Match.EMAIL -> R.string.field_email
        ContactDuplicates.Match.NAME -> R.string.field_name
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OrganizeCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                val labelHeight = with(LocalDensity.current) {
                    MaterialTheme.typography.labelLarge.lineHeight.toDp()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    MergeProfile(
                        contact = primary,
                        caption = stringResource(R.string.merge_kept),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onSwapProfiles,
                        enabled = enabled,
                        modifier = Modifier.padding(
                            top = labelHeight + MergeLabelGap + (MergeAvatarSize - MergeSwapButtonSize) / 2,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = stringResource(R.string.action_swap),
                        )
                    }
                    MergeProfile(
                        contact = duplicate,
                        caption = stringResource(R.string.merge_other),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = MergeNameDividerGap),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(matchLabelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sharedValue,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MergeLabelGap),
                )
            }
            if (expanded && conflicts.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        conflicts.forEachIndexed { index, conflict ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            MergeSingletonSwapRow(
                                conflict = conflict,
                                keepRight = conflict.field in keptFromRight,
                                enabled = enabled,
                                leftContactId = primary.id,
                                rightContactId = duplicate.id,
                                leftName = primary.displayName,
                                rightName = duplicate.displayName,
                                onSwap = {
                                    onToggleField(conflict.field, conflict.field !in keptFromRight)
                                },
                            )
                        }
                    }
                }
            }
            if (expanded && keptRows.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.merge_both_kept),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    keptRows.forEach { row ->
                        MergeKeptFieldRow(row)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (expanded) R.string.action_show_less else R.string.action_show_more,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = onMerge,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(R.string.action_merge),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MergeProfile(
    contact: DuplicateContactSide,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val name = contact.displayName.ifBlank { stringResource(R.string.fallback_contact) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(MergeLabelGap))
        ContactAvatar(
            contactId = contact.id,
            displayName = contact.displayName,
            size = MergeAvatarSize,
        )
        Spacer(modifier = Modifier.height(MergePhotoNameGap))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MergeSingletonSwapRow(
    conflict: MergeScalarConflict,
    keepRight: Boolean,
    enabled: Boolean,
    leftContactId: Long,
    rightContactId: Long,
    leftName: String,
    rightName: String,
    onSwap: () -> Unit,
) {
    val kept = if (keepRight) conflict.right else conflict.left
    val other = if (keepRight) conflict.left else conflict.right
    val keptContactId = if (keepRight) rightContactId else leftContactId
    val otherContactId = if (keepRight) leftContactId else rightContactId
    val keptName = if (keepRight) rightName else leftName
    val otherName = if (keepRight) leftName else rightName
    val isPhoto = conflict.field == MergeSingletonField.PHOTO
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(conflict.field.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MergeLabelGap),
            shape = MergeValueShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MergeValueHeight)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    MergeChosenValue(
                        snapshot = kept,
                        selected = true,
                        contactId = keptContactId,
                        displayName = keptName,
                        isPhoto = isPhoto,
                        alignEnd = true,
                    )
                }
                IconButton(onClick = onSwap, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = stringResource(R.string.action_swap),
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    MergeChosenValue(
                        snapshot = other,
                        selected = false,
                        contactId = otherContactId,
                        displayName = otherName,
                        isPhoto = isPhoto,
                        alignEnd = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun MergeChosenValue(
    snapshot: MergeScalarSnapshot,
    selected: Boolean,
    contactId: Long,
    displayName: String,
    isPhoto: Boolean,
    alignEnd: Boolean,
) {
    if (isPhoto) {
        ContactAvatar(
            contactId = contactId,
            displayName = displayName,
            size = MergeValuePhotoSize,
        )
        return
    }
    val text = if (snapshot.valueRes != 0) stringResource(snapshot.valueRes) else snapshot.value
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

private data class MergeKeptRow(
    val labelRes: Int,
    val literalLabel: String,
    val leftValues: List<MergeKeptValue>,
    val rightValues: List<MergeKeptValue>,
)

private data class MergeKeptValue(
    val valueRes: Int,
    val value: String,
)

private fun mergeKeptRows(
    primary: DuplicateContactSide,
    duplicate: DuplicateContactSide,
    sharedValue: String,
): List<MergeKeptRow> {
    val shared = sharedValue.trim()
    val seen = mutableSetOf<String>()
    val labels = linkedMapOf<String, Pair<Int, String>>()
    val leftValues = linkedMapOf<String, MutableList<MergeKeptValue>>()
    val rightValues = linkedMapOf<String, MutableList<MergeKeptValue>>()
    fun add(line: DuplicateFieldLine, fromLeft: Boolean) {
        val value = line.value.trim()
        if (line.valueRes == 0 && value.isEmpty()) return
        if (line.valueRes == 0 && value.equals(shared, ignoreCase = true)) return
        val literal = line.literalLabel.trim()
        if (line.labelRes == 0 && literal.isEmpty()) return
        val groupKey = "${line.labelRes}|${literal.lowercase()}"
        val valueKey = "$groupKey|${line.valueRes}|${value.lowercase()}"
        if (!seen.add(valueKey)) return
        labels.getOrPut(groupKey) { line.labelRes to literal }
        val side = if (fromLeft) leftValues else rightValues
        side.getOrPut(groupKey) { mutableListOf() } += MergeKeptValue(line.valueRes, value)
    }
    ContactMerge.scalarsOnlyOn(primary.scalars, duplicate.scalars).forEach { add(it.toFieldLine(), fromLeft = true) }
    ContactMerge.scalarsOnlyOn(duplicate.scalars, primary.scalars).forEach { add(it.toFieldLine(), fromLeft = false) }
    primary.lines.forEach { add(it, fromLeft = true) }
    duplicate.lines.forEach { add(it, fromLeft = false) }
    return labels.map { (key, label) ->
        MergeKeptRow(
            labelRes = label.first,
            literalLabel = label.second,
            leftValues = leftValues[key].orEmpty(),
            rightValues = rightValues[key].orEmpty(),
        )
    }.filter { row -> row.leftValues.isNotEmpty() || row.rightValues.isNotEmpty() }
}

private fun MergeScalarSnapshot.toFieldLine(): DuplicateFieldLine = DuplicateFieldLine(
    labelRes = field.labelRes,
    valueRes = valueRes,
    value = value,
)

@Composable
private fun MergeKeptFieldRow(row: MergeKeptRow) {
    val label = when {
        row.labelRes != 0 -> stringResource(row.labelRes)
        else -> row.literalLabel
    }
    if (label.isBlank() || (row.leftValues.isEmpty() && row.rightValues.isEmpty())) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MergeLabelGap),
            shape = MergeValueShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    MergeKeptValueColumn(
                        values = row.leftValues,
                        modifier = Modifier.weight(1f),
                    )
                    MergeKeptValueColumn(
                        values = row.rightValues,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MergeKeptValueColumn(
    values: List<MergeKeptValue>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        values.forEach { item ->
            val text = if (item.valueRes != 0) stringResource(item.valueRes) else item.value
            if (text.isBlank()) return@forEach
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizeScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        content = content,
    )
}

@Composable
private fun SupportingLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDatesDestinationSegmentedControl(
    selected: ContactDatesDestination,
    nextcloudCalendarAvailable: Boolean?,
    onSelected: (ContactDatesDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = buildList {
        add(ContactDatesDestination.NONE to stringResource(R.string.type_none))
        add(ContactDatesDestination.SYSTEM to stringResource(R.string.dest_system))
        if (nextcloudCalendarAvailable == true) {
            add(ContactDatesDestination.NEXTCLOUD to "Nextcloud")
            add(ContactDatesDestination.BOTH to stringResource(R.string.dest_both))
        }
    }
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (destination, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                ),
                onClick = { onSelected(destination) },
                selected = selected == destination,
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarEventDescriptionSegmentedControl(
    selected: CalendarEventDescriptionMode,
    onSelected: (CalendarEventDescriptionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        CalendarEventDescriptionMode.DEFAULT to stringResource(R.string.desc_default),
        CalendarEventDescriptionMode.NOTES to stringResource(R.string.desc_notes),
        CalendarEventDescriptionMode.DETAILS to stringResource(R.string.desc_details),
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                ),
                onClick = { onSelected(mode) },
                selected = selected == mode,
                label = { Text(label) },
            )
        }
    }
}
