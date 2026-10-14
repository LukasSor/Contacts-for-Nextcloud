package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import com.nextcloud.contacts.platform.VideoCallTargets

@androidx.compose.material3.ExperimentalMaterial3Api
@androidx.compose.runtime.Composable
fun VoiceCallChooserSheet(
    targets: List<VideoCallTargets.Target>,
    onSelect: (VideoCallTargets.Target) -> Unit,
    onDismiss: () -> Unit,
) {
    VideoCallChooserSheet(
        targets = targets,
        onSelect = onSelect,
        onDismiss = onDismiss,
        sheetTitle = stringResource(R.string.sheet_voice_call),
    )
}
