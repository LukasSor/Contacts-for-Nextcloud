package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.data.prefs.ConnectedAppsAccess
import com.nextcloud.contacts.data.local.ImEntity
import com.nextcloud.contacts.data.local.ProviderIntegrationsCache
import com.nextcloud.contacts.platform.ContactsProviderAccounts
import com.nextcloud.contacts.platform.ContactsProviderIntegrations
import com.nextcloud.contacts.platform.MessagingAction
import com.nextcloud.contacts.platform.MessagingContactsIntents
import com.nextcloud.contacts.platform.MessagingContactsIntents.LaunchResult
import com.nextcloud.contacts.platform.MessengerImMapping
import android.provider.ContactsContract
import com.nextcloud.contacts.util.AppIconBitmapCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ConnectedAppsSection(
    phones: List<String>,
    existingIm: List<ImEntity> = emptyList(),
    providerIntegrationsCache: String = "",
    onProviderIntegrationsCached: (List<ContactsProviderIntegrations.AppGroup>) -> Unit = {},
    readContactsRevision: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ContactsApp).container }
    // Saved rows on the first frame. An empty start blanks the section when the
    // cache string is written back, then the same rows return.
    var discoveredGroups by remember(phones, providerIntegrationsCache) {
        mutableStateOf(ProviderIntegrationsCache.seedAppGroups(providerIntegrationsCache, phones))
    }
    var disabledAccountKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(phones, providerIntegrationsCache, readContactsRevision) {
        val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!ConnectedAppsAccess.discoveryEnabled(hasPermission)) {
            discoveredGroups = emptyList()
            return@LaunchedEffect
        }
        val seeded = ProviderIntegrationsCache.seedAppGroups(providerIntegrationsCache, phones)
        if (seeded.isNotEmpty()) {
            discoveredGroups = seeded
        }
        val groups = withContext(Dispatchers.IO) {
            ContactsProviderIntegrations.discoverGroupedCoalesced(context, phones)
        }
        discoveredGroups = groups
        onProviderIntegrationsCached(groups)
    }

    LaunchedEffect(Unit) {
        container.userPreferences.disabledAccountKeys.collect { keys ->
            disabledAccountKeys = keys
        }
    }

    val visibleGroups = remember(discoveredGroups, disabledAccountKeys) {
        discoveredGroups.filter { group ->
            !ContactsProviderAccounts.isDisabled(
                accountType = group.accountType,
                accountName = group.actions.firstOrNull()?.accountName,
                disabledKeys = disabledAccountKeys,
            )
        }
    }

    var expandedGroupKeys by remember { mutableStateOf(emptySet<String>()) }

    if (visibleGroups.isEmpty()) return

    fun handleLaunch(launch: () -> LaunchResult, failureLabel: String) {
        when (launch()) {
            LaunchResult.Success -> Unit
            LaunchResult.NeedsCallPermission -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_phone_permission_voice_video),
                    Toast.LENGTH_LONG,
                ).show()
            }
            LaunchResult.Failed -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_open_failed, failureLabel),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.section_connected_apps),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(GroupedFormDefaults.CornerOuter),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                visibleGroups.forEachIndexed { index, group ->
                    ProviderAppAccordion(
                        group = group,
                        linkedToIm = MessengerImMapping.isLinkedToInstantMessaging(
                            group = group,
                            existingIm = existingIm,
                        ),
                        linkedImHandle = MessengerImMapping.linkedInstantMessagingHandle(
                            group = group,
                            existingIm = existingIm,
                        ),
                        expanded = group.key in expandedGroupKeys,
                        onToggle = {
                            expandedGroupKeys = if (group.key in expandedGroupKeys) {
                                expandedGroupKeys - group.key
                            } else {
                                expandedGroupKeys + group.key
                            }
                        },
                        onAction = { action ->
                            handleLaunch(
                                launch = { MessagingContactsIntents.openProviderAction(context, action) },
                                failureLabel = action.label,
                            )
                        },
                    )
                    if (index < visibleGroups.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = GroupedFormDefaults.RowPadding),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderAppAccordion(
    group: ContactsProviderIntegrations.AppGroup,
    linkedToIm: Boolean,
    linkedImHandle: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAction: (ContactsProviderIntegrations.Action) -> Unit,
) {
    val context = LocalContext.current
    val iconBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, group.key) {
        value = withContext(Dispatchers.IO) {
            resolveGroupIcon(context, group)
        }
    }

    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "chevron",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val groupIcon = iconBitmap
            if (groupIcon != null) {
                Image(
                    bitmap = groupIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (linkedToIm) {
                    Text(
                        text = stringResource(R.string.label_linked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (group.actions.isEmpty() && !linkedImHandle.isNullOrBlank()) {
                    Text(
                        text = linkedImHandle,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 250),
                expandFrom = Alignment.Top,
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 250),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                group.actions.forEach { action ->
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 60.dp),
                        thickness = 1.dp,
                        color = dividerColor,
                    )
                    ConnectedAppActionRow(
                        icon = action.messagingAction?.let(::actionIcon)
                            ?: Icons.AutoMirrored.Filled.OpenInNew,
                        label = action.label,
                        onClick = { onAction(action) },
                    )
                }
            }
        }
    }
}

private fun resolveGroupIcon(
    context: android.content.Context,
    group: ContactsProviderIntegrations.AppGroup,
): androidx.compose.ui.graphics.ImageBitmap? {
    val packageCandidates = linkedSetOf<String>().apply {
        group.packageName?.let(::add)
        group.accountType?.takeIf { it.contains('.') }?.let(::add)
        group.actions.mapNotNull { it.packageName }.forEach(::add)
    }

    packageCandidates.forEach { packageName ->
        AppIconBitmapCache.get(context, packageName)?.let { return it }
    }

    val packageManager = context.packageManager
    group.actions.forEach { action ->
        val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, action.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val resolved = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull()
            ?.activityInfo
            ?.packageName
            ?: return@forEach

        AppIconBitmapCache.get(context, resolved)?.let { return it }
    }

    return null
}

@Composable
private fun ConnectedAppActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun actionIcon(action: MessagingAction): ImageVector = when (action) {
    MessagingAction.Message -> Icons.Default.Sms
    MessagingAction.Video -> Icons.Default.Videocam
    MessagingAction.Voice -> Icons.Default.Call
}
