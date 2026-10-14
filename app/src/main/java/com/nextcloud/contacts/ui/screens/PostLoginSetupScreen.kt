package com.nextcloud.contacts.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.prefs.ContactDatesDestination
import com.nextcloud.contacts.platform.CalendarPermissionRequester
import com.nextcloud.contacts.platform.ContactCalendarEventKind
import com.nextcloud.contacts.ui.components.AppSnackbarHost
import com.nextcloud.contacts.ui.setup.PostLoginSetup
import com.nextcloud.contacts.ui.util.rememberCalendarPermissionAction
import com.nextcloud.contacts.ui.util.rememberCallPhonePermissionAction
import com.nextcloud.contacts.ui.util.rememberReadContactsPermissionAction
import com.nextcloud.contacts.ui.util.rememberWriteContactsExportAction
import com.nextcloud.contacts.ui.viewmodel.PostLoginSetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostLoginSetupScreen(
    onDone: () -> Unit,
    viewModel: PostLoginSetupViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val container = (context.applicationContext as ContactsApp).container
    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var permissionRevision by androidx.compose.runtime.remember { mutableIntStateOf(0) }
    var pendingDestination by androidx.compose.runtime.remember {
        mutableStateOf<ContactDatesDestination?>(null)
    }

    val readContactsGranted = androidx.compose.runtime.remember(permissionRevision, context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val callPhoneGranted = androidx.compose.runtime.remember(permissionRevision, context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
    }

    // Ask only from a grant button or when a switch is turned on. Opening this page does not ask.
    val requestReadContacts = rememberReadContactsPermissionAction(
        onGranted = { permissionRevision++ },
        onDenied = { permissionRevision++ },
    )
    val requestCallPhone = rememberCallPhonePermissionAction(
        onRetry = { permissionRevision++ },
        onDenied = { permissionRevision++ },
    )
    val enableDeviceExport = rememberWriteContactsExportAction(
        requester = container.contactsPermissionRequester,
        onGranted = { viewModel.setExportToDeviceContacts(true) },
        onDenied = { },
    )
    val applyDatesDestination = rememberCalendarPermissionAction(
        requester = container.calendarPermissionRequester,
        onGranted = {
            pendingDestination?.let(viewModel::setDatesDestination)
            pendingDestination = null
        },
        onDenied = {
            pendingDestination = null
        },
    )

    val sharedDestination = PostLoginSetup.sharedDateDestination(
        birthdays = state.storedBirthdaysDestination,
        anniversaries = state.storedAnniversariesDestination,
        deaths = state.storedDatesOfDeathDestination,
        nextcloudCalendarAvailable = state.nextcloudCalendarAvailable,
    )

    fun onDestinationSelected(destination: ContactDatesDestination) {
        val alreadyStored = PostLoginSetup.destinationsForAllKinds(destination).all { (kind, value) ->
            val stored = when (kind) {
                ContactCalendarEventKind.BIRTHDAY -> state.storedBirthdaysDestination
                ContactCalendarEventKind.ANNIVERSARY -> state.storedAnniversariesDestination
                ContactCalendarEventKind.DEATH -> state.storedDatesOfDeathDestination
            }
            stored == value
        }
        if (alreadyStored) {
            val missingCalendarPermission = destination.includesSystem() &&
                !CalendarPermissionRequester.hasCalendarPermissions(context)
            if (!missingCalendarPermission) return
        }
        if (destination.includesSystem()) {
            pendingDestination = destination
            applyDatesDestination()
        } else {
            viewModel.setDatesDestination(destination)
        }
    }

    fun complete() {
        if (finishing) return
        finishing = true
        viewModel.finish(onDone)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Stay on this page. Done is the only control that leaves it or marks setup complete.
    BackHandler { }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.setup_title)) })
        },
        bottomBar = {
            Button(
                onClick = ::complete,
                enabled = !finishing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.action_done))
            }
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.setup_phone_calls)) },
                supportingContent = {
                    Text(stringResource(R.string.setup_phone_calls_summary))
                },
                trailingContent = {
                    if (callPhoneGranted) {
                        Text(
                            text = stringResource(R.string.setup_phone_calls_granted),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        TextButton(onClick = requestCallPhone) {
                            Text(stringResource(R.string.setup_phone_calls_grant))
                        }
                    }
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.section_connected_apps)) },
                supportingContent = { Text(stringResource(R.string.setup_connected_apps_summary)) },
                trailingContent = {
                    if (readContactsGranted) {
                        Text(
                            text = stringResource(R.string.setup_phone_calls_granted),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        TextButton(onClick = requestReadContacts) {
                            Text(stringResource(R.string.setup_phone_calls_grant))
                        }
                    }
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_show_in_phone)) },
                supportingContent = { Text(stringResource(R.string.setting_show_in_phone_summary)) },
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
                headlineContent = { Text(stringResource(R.string.section_calendar)) },
                supportingContent = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.setup_dates_summary))
                        SetupDatesDestinationControl(
                            selected = sharedDestination,
                            nextcloudCalendarAvailable = state.nextcloudCalendarAvailable,
                            onSelected = ::onDestinationSelected,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_website_logos)) },
                supportingContent = { Text(stringResource(R.string.setting_website_logos_summary)) },
                trailingContent = {
                    Switch(
                        checked = state.enableWebsiteLogos,
                        onCheckedChange = viewModel::setEnableWebsiteLogos,
                    )
                },
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupDatesDestinationControl(
    selected: ContactDatesDestination?,
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
