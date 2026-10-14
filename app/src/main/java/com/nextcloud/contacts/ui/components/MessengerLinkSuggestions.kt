package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.data.prefs.ConnectedAppsAccess
import com.nextcloud.contacts.data.local.ImEntity
import com.nextcloud.contacts.data.local.ProviderIntegrationsCache
import com.nextcloud.contacts.platform.ContactsProviderAccounts
import com.nextcloud.contacts.platform.ContactsProviderIntegrations
import com.nextcloud.contacts.platform.MessengerImMapping
import kotlinx.coroutines.Dispatchers
import com.nextcloud.contacts.util.AppIconBitmapCache
import kotlinx.coroutines.withContext

private val LinkCardShape = RoundedCornerShape(GroupedFormDefaults.CornerOuter)
private val RowHorizontalPadding = GroupedFormDefaults.RowPadding
private val DividerColorAlpha = 0.12f

@Composable
fun MessengerLinkSuggestions(
    contactId: Long,
    phones: List<String>,
    existingIm: List<ImEntity>,
    onLink: (imType: String, handle: String) -> Unit,
    onDismiss: (imType: String) -> Unit,
    providerIntegrationsCache: String = "",
    onProviderIntegrationsCached: (List<ContactsProviderIntegrations.AppGroup>) -> Unit = {},
    readContactsRevision: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ContactsApp).container }
    val autoLink by container.userPreferences.autoLinkMessengers.collectAsState(initial = false)
    val hideLinkSuggestions by container.userPreferences.hideLinkSuggestions.collectAsState(initial = false)
    val dismissedKeys by container.userPreferences.dismissedImLinkKeys.collectAsState(initial = emptySet())
    var disabledAccountKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var suggestions by remember { mutableStateOf<List<MessengerImMapping.Suggestion>>(emptyList()) }
    var autoLinkedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        container.userPreferences.disabledAccountKeys.collect { keys ->
            disabledAccountKeys = keys
        }
    }

    LaunchedEffect(
        contactId,
        phones,
        existingIm,
        dismissedKeys,
        disabledAccountKeys,
        autoLink,
        hideLinkSuggestions,
        providerIntegrationsCache,
        readContactsRevision,
    ) {
        if (hideLinkSuggestions && !autoLink) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!ConnectedAppsAccess.discoveryEnabled(hasPermission)) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        fun filterGroups(groups: List<ContactsProviderIntegrations.AppGroup>) =
            groups.filter { group ->
                !ContactsProviderAccounts.isDisabled(
                    accountType = group.accountType,
                    accountName = group.actions.firstOrNull()?.accountName,
                    disabledKeys = disabledAccountKeys,
                )
            }

        val seeded = filterGroups(
            ProviderIntegrationsCache.seedAppGroups(providerIntegrationsCache, phones),
        )
        if (seeded.isNotEmpty()) {
            val seededSuggestions = MessengerImMapping.suggestions(
                groups = seeded,
                phones = phones,
                existingIm = existingIm,
                dismissedKeys = dismissedKeys,
                contactId = contactId,
            )
            suggestions = seededSuggestions
        }

        val groups = withContext(Dispatchers.IO) {
            val actions = ContactsProviderIntegrations.discover(context, phones)
            ContactsProviderIntegrations.groupActions(context, actions)
        }.let(::filterGroups)
        ContactsProviderIntegrations.cacheAppGroups(phones, groups)
        onProviderIntegrationsCached(groups)
        val next = MessengerImMapping.suggestions(
            groups = groups,
            phones = phones,
            existingIm = existingIm,
            dismissedKeys = dismissedKeys,
            contactId = contactId,
        )
        suggestions = next

        if (autoLink && next.isNotEmpty()) {
            next.forEach { suggestion ->
                val key = MessengerImMapping.dismissKey(contactId, suggestion.imType)
                if (key in autoLinkedKeys) return@forEach
                autoLinkedKeys = autoLinkedKeys + key
                onLink(suggestion.imType, suggestion.handle)
            }
        }
    }

    if (autoLink || hideLinkSuggestions || suggestions.isEmpty()) return

    LinkSuggestionsCard(
        suggestions = suggestions,
        onDismiss = onDismiss,
        onLink = onLink,
        modifier = modifier,
    )
}

@Composable
private fun LinkSuggestionsCard(
    suggestions: List<MessengerImMapping.Suggestion>,
    onDismiss: (imType: String) -> Unit,
    onLink: (imType: String, handle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DividerColorAlpha)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LinkCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = RowHorizontalPadding,
                        end = RowHorizontalPadding,
                        top = 14.dp,
                        bottom = 4.dp,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.link_apps_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.link_apps_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            suggestions.forEachIndexed { index, suggestion ->
                LinkSuggestionRow(
                    suggestion = suggestion,
                    onDismiss = { onDismiss(suggestion.imType) },
                    onLink = { onLink(suggestion.imType, suggestion.handle) },
                )
                if (index < suggestions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = RowHorizontalPadding),
                        thickness = 1.dp,
                        color = dividerColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkSuggestionActions(
    onDismiss: () -> Unit,
    onLink: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onDismiss,
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) {
            Text(
                text = stringResource(R.string.action_not_now),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        TextButton(
            onClick = onLink,
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) {
            Text(
                text = stringResource(R.string.action_link),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun LinkSuggestionAppIdentity(
    suggestion: MessengerImMapping.Suggestion,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(null, suggestion.packageName) {
        val pkg = suggestion.packageName?.trim()?.takeIf { it.isNotEmpty() }
        value = if (pkg == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                AppIconBitmapCache.get(context, pkg)
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val suggestionIcon = iconBitmap
        if (suggestionIcon != null) {
            Image(
                bitmap = suggestionIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = suggestion.appLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun LinkSuggestionRow(
    suggestion: MessengerImMapping.Suggestion,
    onDismiss: () -> Unit,
    onLink: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RowHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinkSuggestionAppIdentity(
            suggestion = suggestion,
            modifier = Modifier.weight(1f),
        )
        LinkSuggestionActions(onDismiss = onDismiss, onLink = onLink)
    }
}

