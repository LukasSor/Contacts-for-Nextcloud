package com.nextcloud.contacts.ui.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nextcloud.contacts.platform.CalendarPermissionRequester
import com.nextcloud.contacts.platform.ContactsPermissionRequester
import com.nextcloud.contacts.platform.MessagingContactsIntents

private fun hasReadContactsPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun rememberCallPhonePermissionAction(
    onRetry: () -> Unit,
    onDenied: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onRetry() else onDenied()
    }
    return remember(context, launcher) {
        {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                onRetry()
            } else {
                launcher.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }
}

/**
 * Asks for CALL_PHONE only when a call is started and the permission is still requestable.
 * Grant places the call; denial, including a permanent denial, opens the dialer once.
 */
@Composable
fun rememberPhoneCallAction(
    onResult: (MessagingContactsIntents.LaunchResult, Boolean) -> Unit,
): (String, Boolean) -> Unit {
    val context = LocalContext.current
    val onResultState = rememberUpdatedState(onResult)
    val pending = remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var suppressPrompt by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pending.value ?: return@rememberLauncherForActivityResult
        pending.value = null
        if (!granted && isCallPermissionPermanentlyDenied(context)) {
            suppressPrompt = true
        }
        val result = MessagingContactsIntents.placePhoneCall(
            context,
            request.first,
            withVideo = request.second,
        )
        onResultState.value(result, request.second)
    }
    return { phone, withVideo ->
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted || suppressPrompt) {
            onResultState.value(
                MessagingContactsIntents.placePhoneCall(context, phone, withVideo = withVideo),
                withVideo,
            )
        } else {
            pending.value = phone to withVideo
            launcher.launch(Manifest.permission.CALL_PHONE)
        }
    }
}

private fun isCallPermissionPermanentlyDenied(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    return !ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.CALL_PHONE,
    )
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun rememberReadContactsPermissionAction(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onGranted() else onDenied()
    }
    return remember(context, launcher, onGranted, onDenied) {
        {
            if (hasReadContactsPermission(context)) {
                onGranted()
            } else {
                launcher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }
}

@Composable
fun rememberDeviceContactsImportAction(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
): () -> Unit = rememberReadContactsPermissionAction(onGranted, onDenied)

@Composable
fun rememberWriteContactsExportAction(
    requester: ContactsPermissionRequester,
    onGranted: () -> Unit,
    onDenied: () -> Unit,
): () -> Unit = remember(requester) {
    {
        requester.requestDeviceExportPermissions { granted ->
            if (granted) onGranted() else onDenied()
        }
    }
}

@Composable
fun rememberCalendarPermissionAction(
    requester: CalendarPermissionRequester,
    onGranted: () -> Unit,
    onDenied: () -> Unit,
): () -> Unit = remember(requester) {
    {
        requester.requestCalendarPermissions { granted ->
            if (granted) onGranted() else onDenied()
        }
    }
}
