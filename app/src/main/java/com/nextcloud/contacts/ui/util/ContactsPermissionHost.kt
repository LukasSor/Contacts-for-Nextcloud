package com.nextcloud.contacts.ui.util

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.platform.ContactsPermissionRequester
import kotlinx.coroutines.flow.first

@Composable
fun ContactsPermissionHost(requester: ContactsPermissionRequester) {
    val context = LocalContext.current
    val container = (context.applicationContext as ContactsApp).container
    var pendingCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = ContactsPermissionRequester.exportPermissions.all { permission ->
            results[permission] == true
        }
        pendingCallback?.invoke(granted)
        pendingCallback = null
    }

    DisposableEffect(requester, launcher) {
        requester.register { callback ->
            if (ContactsPermissionRequester.hasDeviceExportPermissions(context)) {
                callback(true)
            } else {
                pendingCallback = callback
                launcher.launch(ContactsPermissionRequester.exportPermissions)
            }
        }
        onDispose { requester.unregister() }
    }

    LaunchedEffect(Unit) {
        val exportEnabled = container.userPreferences.exportToDeviceContacts.first()
        if (exportEnabled && !ContactsPermissionRequester.hasDeviceExportPermissions(context)) {
            container.userPreferences.setExportToDeviceContacts(false)
        }
    }
}
