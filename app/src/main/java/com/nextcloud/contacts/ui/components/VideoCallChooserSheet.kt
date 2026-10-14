package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextcloud.contacts.platform.VideoCallTargets
import com.nextcloud.contacts.util.AppIconBitmapCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCallChooserSheet(
    targets: List<VideoCallTargets.Target>,
    onSelect: (VideoCallTargets.Target) -> Unit,
    onDismiss: () -> Unit,
    sheetTitle: String = stringResource(R.string.sheet_video_call),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showDetails = remember(targets) {
        targets.groupingBy { it.label }.eachCount().any { it.value > 1 }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = sheetTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            targets.forEachIndexed { index, target ->
                VideoCallTargetRow(
                    target = target,
                    showDetail = showDetails,
                    onClick = { onSelect(target) },
                )
                if (index < targets.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCallTargetRow(
    target: VideoCallTargets.Target,
    showDetail: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val iconRes = target.iconRes
    val iconBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        null,
        target.id,
        target.packageName,
        iconRes,
    ) {
        if (iconRes != null) {
            value = null
        } else {
            value = withContext(Dispatchers.IO) {
                resolveTargetIcon(context, target)
            }
        }
    }
    val subtitle = target.detail?.takeIf { showDetail && it.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val targetIcon = iconBitmap
        when {
            iconRes != null -> {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                )
            }
            targetIcon != null -> {
                Image(
                    bitmap = targetIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun resolveTargetIcon(
    context: android.content.Context,
    target: VideoCallTargets.Target,
): androidx.compose.ui.graphics.ImageBitmap? {
    target.packageName?.let { AppIconBitmapCache.get(context, it) }?.let { return it }

    val action = (target.source as? VideoCallTargets.Source.Provider)?.action ?: return null
    val packageManager = context.packageManager
    val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, action.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val resolved = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .firstOrNull()
        ?.activityInfo
        ?.packageName
        ?: return null
    return AppIconBitmapCache.get(context, resolved)
}
