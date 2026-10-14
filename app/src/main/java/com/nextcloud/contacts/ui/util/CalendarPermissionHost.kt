package com.nextcloud.contacts.ui.util

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nextcloud.contacts.platform.CalendarPermissionRequester

private class CalendarPermissionCallbackSlot {
    var callback: ((Boolean) -> Unit)? = null
}

private class CalendarPermissionRequestState {
    var hasCompletedRequest: Boolean = false
}

@Composable
fun CalendarPermissionHost(requester: CalendarPermissionRequester) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val callbackSlot = remember { CalendarPermissionCallbackSlot() }
    val permissionRequestState = remember { CalendarPermissionRequestState() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permissionRequestState.hasCompletedRequest = true
        val granted = CalendarPermissionRequester.calendarPermissions.all { permission ->
            results[permission] == true
        }
        callbackSlot.callback?.invoke(granted)
        callbackSlot.callback = null
    }

    DisposableEffect(requester, launcher) {
        requester.register { callback ->
            if (CalendarPermissionRequester.hasCalendarPermissions(context)) {
                callback(true)
                return@register
            }
            if (activity != null &&
                permissionRequestState.hasCompletedRequest &&
                CalendarPermissionRequester.isCalendarPermissionPermanentlyDenied(activity)
            ) {
                CalendarPermissionRequester.openAppPermissionSettings(context)
                callback(false)
                return@register
            }
            callbackSlot.callback = callback
            launcher.launch(CalendarPermissionRequester.calendarPermissions)
        }
        onDispose { requester.unregister() }
    }
}
