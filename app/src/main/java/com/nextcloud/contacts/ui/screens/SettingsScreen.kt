package com.nextcloud.contacts.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.app.LocaleManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nextcloud.contacts.R
import com.nextcloud.contacts.ui.util.IdentifierKeyboard
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.data.prefs.AvatarFrameStyle
import com.nextcloud.contacts.data.prefs.CalendarEventDescriptionMode
import com.nextcloud.contacts.data.prefs.ContactDatesDestination
import com.nextcloud.contacts.data.prefs.ContactEditFieldMode
import com.nextcloud.contacts.data.prefs.ThemeMode
import com.nextcloud.contacts.ui.components.AppSnackbarHost
import com.nextcloud.contacts.ui.components.ObserveUiNotices
import com.nextcloud.contacts.ui.util.rememberDeviceContactsImportAction
import com.nextcloud.contacts.ui.util.rememberReadContactsPermissionAction
import com.nextcloud.contacts.ui.util.rememberVcfExportDocumentAction
import com.nextcloud.contacts.ui.util.rememberVcfImportDocumentAction
import com.nextcloud.contacts.platform.CalendarPermissionRequester
import com.nextcloud.contacts.platform.ContactCalendarEventKind
import com.nextcloud.contacts.ui.util.rememberCalendarPermissionAction
import com.nextcloud.contacts.ui.util.rememberWriteContactsExportAction
import com.nextcloud.contacts.ui.viewmodel.SettingsViewModel
import java.net.URI

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
fun SettingsScreen(
    onBack: () -> Unit,
    onDisconnected: () -> Unit,
    onConnectAccount: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val container = (context.applicationContext as ContactsApp).container
    val snackbarHostState = remember { SnackbarHostState() }
    val importFromDevice = rememberDeviceContactsImportAction(
        onGranted = viewModel::importFromDevice,
        onDenied = { viewModel.showMessage(context.getString(R.string.msg_contacts_permission)) },
    )
    var contactsPermissionRevision by remember { mutableIntStateOf(0) }
    val readContactsGranted = remember(contactsPermissionRevision, context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val requestReadContacts = rememberReadContactsPermissionAction(
        onGranted = {
            contactsPermissionRevision++
            viewModel.refreshProviderAccounts()
        },
        onDenied = { contactsPermissionRevision++ },
    )
    val enableDeviceExport = rememberWriteContactsExportAction(
        requester = container.contactsPermissionRequester,
        onGranted = { viewModel.setExportToDeviceContacts(true) },
        onDenied = {
            viewModel.showMessage(
                context.getString(R.string.msg_contacts_settings_phonebook),
            )
        },
    )
    val exportContactsVcf = rememberVcfExportDocumentAction(
        onUriReady = viewModel::exportContactsToVcf,
    )
    val importContactsVcf = rememberVcfImportDocumentAction(
        onTextRead = viewModel::importContactsFromVcf,
        onReadFailed = { viewModel.showMessage(context.getString(R.string.msg_could_not_read_file)) },
    )
    val importExportBusy = state.isSyncing || state.isImporting || state.isExportingVcf
    data class PendingKindDestination(
        val kind: ContactCalendarEventKind,
        val destination: ContactDatesDestination,
    )

    var pendingKindDestination by remember { mutableStateOf<PendingKindDestination?>(null) }
    val applyKindDestination = rememberCalendarPermissionAction(
        requester = container.calendarPermissionRequester,
        onGranted = {
            pendingKindDestination?.let { (kind, destination) ->
                viewModel.setKindDestination(kind, destination)
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
            message?.let(viewModel::showMessage)
        },
    )
    fun selectedDestination(kind: ContactCalendarEventKind): ContactDatesDestination = when (kind) {
        ContactCalendarEventKind.BIRTHDAY -> state.birthdaysDestination
        ContactCalendarEventKind.ANNIVERSARY -> state.anniversariesDestination
        ContactCalendarEventKind.DEATH -> state.datesOfDeathDestination
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
            viewModel.setKindDestination(kind, destination)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    ObserveUiNotices { viewModel.showMessage(it) }

    LaunchedEffect(Unit) {
        viewModel.refreshProviderAccounts()
        viewModel.refreshNextcloudCalendarAvailability()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cd_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            state.account?.let { account ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = account.loginName,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = accountDomainLabel(account.server),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = viewModel::syncNow,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSyncing && !state.isImporting,
                        ) {
                            if (state.isSyncing) {
                                ButtonProgressIndicator()
                            } else {
                                Text(stringResource(R.string.action_sync_now))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.disconnect(onDisconnected) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_disconnect))
                        }
                    }
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_last_sync)) },
                    supportingContent = { Text(state.lastSyncLabel) },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ListItem(
                        modifier = Modifier.clickable(
                            role = Role.Button,
                            onClick = { context.openAppLanguageSettings() },
                        ),
                        headlineContent = { Text(stringResource(R.string.setting_language)) },
                        supportingContent = { SelectedAppLanguageLabel() },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        },
                    )
                }
            } ?: Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.status_not_connected),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.status_sign_in_to_sync),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onConnectAccount,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_connect_nextcloud))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.section_import_device),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_show_in_phone)) },
                supportingContent = {
                    Text(stringResource(R.string.setting_show_in_phone_summary))
                },
                trailingContent = {
                    Switch(
                        checked = state.exportToDeviceContacts,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                enableDeviceExport()
                            } else {
                                viewModel.setExportToDeviceContacts(false)
                            }
                        },
                    )
                },
            )
            ListItem(
                modifier = Modifier.clickable(enabled = !importExportBusy) { exportContactsVcf() },
                headlineContent = { Text(stringResource(R.string.setting_export_contacts)) },
                supportingContent = {
                    Text(stringResource(R.string.setting_export_contacts_summary))
                },
                trailingContent = {
                    if (state.isExportingVcf) {
                        ButtonProgressIndicator()
                    }
                },
            )
            ListItem(
                modifier = Modifier.clickable(enabled = !importExportBusy) { importContactsVcf() },
                headlineContent = { Text(stringResource(R.string.setting_import_contacts)) },
                supportingContent = {
                    Text(stringResource(R.string.setting_import_contacts_summary))
                },
                trailingContent = {
                    if (state.isImporting) {
                        ButtonProgressIndicator()
                    }
                },
            )
            OutlinedButton(
                onClick = importFromDevice,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                enabled = !importExportBusy,
            ) {
                Text(stringResource(R.string.import_from_device))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.section_visible_accounts),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            if (!readContactsGranted) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.section_connected_apps)) },
                    supportingContent = { Text(stringResource(R.string.setup_connected_apps_summary)) },
                    trailingContent = {
                        TextButton(onClick = requestReadContacts) {
                            Text(stringResource(R.string.setup_phone_calls_grant))
                        }
                    },
                )
            }
            Text(
                text = stringResource(R.string.visible_accounts_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (state.accountsNeedContactsPermission) {
                // Tapping this line asks. Opening Settings does not.
                Text(
                    text = stringResource(R.string.visible_accounts_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { requestReadContacts() },
                )
            }
            state.accountToggles.forEach { toggle ->
                ListItem(
                    headlineContent = { Text(toggle.displayName) },
                    supportingContent = { Text(toggle.accountType.ifBlank { stringResource(R.string.account_type_local) }) },
                    trailingContent = {
                        Switch(
                            checked = toggle.enabled,
                            onCheckedChange = { enabled ->
                                viewModel.setAccountEnabled(toggle.key, enabled)
                            },
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.section_messaging),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_auto_link)) },
                supportingContent = {
                    Text(stringResource(R.string.setting_auto_link_summary))
                },
                trailingContent = {
                    Switch(
                        checked = state.autoLinkMessengers,
                        onCheckedChange = viewModel::setAutoLinkMessengers,
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_hide_link_suggestions)) },
                supportingContent = {
                    Text(stringResource(R.string.setting_hide_link_suggestions_summary))
                },
                trailingContent = {
                    Switch(
                        checked = state.hideLinkSuggestions,
                        onCheckedChange = viewModel::setHideLinkSuggestions,
                    )
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.section_appearance),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_theme)) },
                supportingContent = {
                    ThemeSegmentedControl(
                        selected = state.themeMode,
                        onSelected = viewModel::setThemeMode,
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_dynamic_colors)) },
                supportingContent = { Text(stringResource(R.string.setting_dynamic_colors_summary)) },
                trailingContent = {
                    Switch(
                        checked = state.useDynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_organic_frame)) },
                supportingContent = { Text(stringResource(R.string.setting_organic_frame_summary)) },
                trailingContent = {
                    Switch(
                        checked = state.avatarFrameStyle == AvatarFrameStyle.ORGANIC,
                        onCheckedChange = viewModel::setOrganicAvatarFrame,
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_fields_when_editing)) },
                supportingContent = {
                    ContactFormFieldModeControl(
                        selected = state.contactEditFieldMode,
                        onSelected = viewModel::setContactEditFieldMode,
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_scroll_letters)) },
                supportingContent = {
                    Text(stringResource(R.string.setting_scroll_letters_summary))
                },
                trailingContent = {
                    Switch(
                        checked = state.showScrollLetters,
                        onCheckedChange = viewModel::setShowScrollLetters,
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_website_logos)) },
                supportingContent = {
                    Text(stringResource(R.string.setting_website_logos_summary))
                },
                trailingContent = {
                    Switch(
                        checked = state.enableWebsiteLogos,
                        onCheckedChange = viewModel::setEnableWebsiteLogos,
                    )
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.section_calendar),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.field_birthday)) },
                supportingContent = {
                    ContactDatesDestinationSegmentedControl(
                        selected = state.birthdaysDestination,
                        nextcloudCalendarAvailable = state.nextcloudCalendarAvailable,
                        onSelected = { requestKindDestination(ContactCalendarEventKind.BIRTHDAY, it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.field_anniversary)) },
                supportingContent = {
                    ContactDatesDestinationSegmentedControl(
                        selected = state.anniversariesDestination,
                        nextcloudCalendarAvailable = state.nextcloudCalendarAvailable,
                        onSelected = { requestKindDestination(ContactCalendarEventKind.ANNIVERSARY, it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.field_date_of_death)) },
                supportingContent = {
                    ContactDatesDestinationSegmentedControl(
                        selected = state.datesOfDeathDestination,
                        nextcloudCalendarAvailable = state.nextcloudCalendarAvailable,
                        onSelected = { requestKindDestination(ContactCalendarEventKind.DEATH, it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.label_description)) },
                supportingContent = {
                    CalendarEventDescriptionSegmentedControl(
                        selected = state.calendarEventDescriptionMode,
                        onSelected = viewModel::setCalendarEventDescriptionMode,
                    )
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.section_places),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_location_recommendations)) },
                supportingContent = {
                    Text(stringResource(R.string.setting_location_recommendations_summary))
                },
                trailingContent = {
                    Switch(
                        checked = state.locationRecommendationsEnabled,
                        onCheckedChange = viewModel::setLocationRecommendationsEnabled,
                    )
                },
            )
            OutlinedTextField(
                value = state.photonBaseUrl,
                onValueChange = viewModel::setPhotonBaseUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                enabled = state.locationRecommendationsEnabled,
                label = { Text(stringResource(R.string.setting_photon_url)) },
                placeholder = { Text(stringResource(R.string.photon_url_placeholder)) },
                singleLine = true,
                keyboardOptions = IdentifierKeyboard.uri,
            )
            Text(
                text = stringResource(R.string.setting_photon_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.section_advanced),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_vcard_mode)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.setting_vcard_mode_summary),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.fieldSyntaxEnabled,
                        onCheckedChange = viewModel::setFieldSyntaxEnabled,
                    )
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.section_about),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            ListItem(
                modifier = Modifier.clickable(role = Role.Button) {
                    context.openViewUrl(ABOUT_SOURCE_CODE_URL)
                },
                headlineContent = { Text(stringResource(R.string.setting_source_code)) },
                leadingContent = {
                    Icon(Icons.Filled.Code, contentDescription = null)
                },
            )
            ListItem(
                modifier = Modifier.clickable(role = Role.Button) {
                    context.openViewUrl(ABOUT_ISSUES_URL)
                },
                headlineContent = { Text(stringResource(R.string.setting_issues)) },
                leadingContent = {
                    Icon(Icons.Filled.BugReport, contentDescription = null)
                },
            )
            ListItem(
                modifier = Modifier.clickable(role = Role.Button) {
                    context.openViewUrl(ABOUT_LICENSE_URL)
                },
                headlineContent = { Text(stringResource(R.string.setting_license)) },
                leadingContent = {
                    Icon(Icons.Filled.Description, contentDescription = null)
                },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private const val ABOUT_SOURCE_CODE_URL = "https://github.com/LukasSor/Contacts-for-Nextcloud"
private const val ABOUT_ISSUES_URL = "https://github.com/LukasSor/Contacts-for-Nextcloud/issues"
private const val ABOUT_LICENSE_URL = "https://github.com/LukasSor/Contacts-for-Nextcloud/blob/main/LICENSE"

private fun Context.openAppLanguageSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        },
    )
}

private fun Context.openViewUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun SelectedAppLanguageLabel() {
    val context = LocalContext.current
    val configurationLocales = LocalConfiguration.current.locales
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeGeneration++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val perAppLocale = remember(context, resumeGeneration) {
        LocaleManagerCompat.getApplicationLocales(context).get(0)
    }
    val locale = perAppLocale ?: configurationLocales[0]
    Text(locale.getDisplayLanguage(locale))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactFormFieldModeControl(
    selected: ContactEditFieldMode,
    onSelected: (ContactEditFieldMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        ContactEditFieldMode.SIMPLE to stringResource(R.string.mode_simple),
        ContactEditFieldMode.WORK to stringResource(R.string.type_work),
        ContactEditFieldMode.FULL to stringResource(R.string.mode_full),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSegmentedControl(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.dest_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
        ThemeMode.AMOLED to stringResource(R.string.theme_amoled),
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

private fun accountDomainLabel(serverUrl: String): String {
    return runCatching {
        val host = URI(serverUrl.trim()).host
        if (!host.isNullOrBlank()) host else serverUrl.trimEnd('/')
    }.getOrDefault(serverUrl.trimEnd('/'))
}

