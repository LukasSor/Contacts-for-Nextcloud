package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.nextcloud.contacts.platform.MessagingContactsIntents
import com.nextcloud.contacts.platform.MessagingContactsIntents.LaunchResult
import com.nextcloud.contacts.platform.MessengerImMapping
import com.nextcloud.contacts.platform.ServiceBrandIcons
import com.nextcloud.contacts.util.AppIconBitmapCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun InstantMessagingSection(
    accounts: List<ImEntity>,
    phones: List<String>,
    providerIntegrationsCache: String = "",
    onProviderIntegrationsCached: (List<ContactsProviderIntegrations.AppGroup>) -> Unit = {},
    readContactsRevision: Int = 0,
    modifier: Modifier = Modifier,
) {
    if (accounts.isEmpty()) return
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ContactsApp).container }
    // Same seed as Connected apps, so a covered messenger row does not flash in
    // while that section is briefly empty.
    var connectedImTypes by remember(phones, providerIntegrationsCache) {
        mutableStateOf(
            MessengerImMapping.connectedImTypes(
                ProviderIntegrationsCache.seedAppGroups(providerIntegrationsCache, phones),
            ),
        )
    }
    var disabledAccountKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        container.userPreferences.disabledAccountKeys.collect { keys ->
            disabledAccountKeys = keys
        }
    }

    LaunchedEffect(phones, disabledAccountKeys, providerIntegrationsCache, readContactsRevision) {
        val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!ConnectedAppsAccess.discoveryEnabled(hasPermission)) {
            connectedImTypes = emptySet()
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
            connectedImTypes = MessengerImMapping.connectedImTypes(seeded)
        }

        connectedImTypes = withContext(Dispatchers.IO) {
            val groups = ContactsProviderIntegrations.discoverGroupedCoalesced(context, phones)
                .let(::filterGroups)
            onProviderIntegrationsCached(groups)
            MessengerImMapping.connectedImTypes(groups)
        }
    }

    val standalone = remember(accounts, connectedImTypes) {
        accounts.filter { im ->
            !MessengerImMapping.isCoveredByConnectedApp(im, connectedImTypes)
        }
    }

    if (standalone.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.field_instant_messaging),
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
                standalone.forEachIndexed { index, im ->
                    InstantMessagingRow(im = im, phones = phones)
                    if (index < standalone.lastIndex) {
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
private fun InstantMessagingRow(
    im: ImEntity,
    phones: List<String>,
) {
    val context = LocalContext.current
    val type = im.type.ifBlank { "other" }
    val label = MessengerImMapping.typeLabel(context, type)
    val iconBitmap by produceState<ImageBitmap?>(null, type) {
        value = withContext(Dispatchers.IO) {
            MessengerImMapping.packagesForImType(type)
                .firstNotNullOfOrNull { pkg -> AppIconBitmapCache.get(context, pkg) }
        }
    }
    val iconRes = remember(type) { ServiceBrandIcons.imDrawableRes(type) }
    val accent = messengerAccent(type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when (
                    MessagingContactsIntents.openInstantMessaging(
                        context = context,
                        imType = type,
                        handle = im.value,
                        phones = phones,
                    )
                ) {
                    LaunchResult.Success -> Unit
                    LaunchResult.NeedsCallPermission -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_phone_permission_app),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    LaunchResult.Failed -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_open_failed, label),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MessengerIcon(iconBitmap = iconBitmap, iconRes = iconRes, label = label, accent = accent)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = im.value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.cd_open_item, label),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MessengerIcon(
    iconBitmap: ImageBitmap?,
    iconRes: Int?,
    label: String,
    accent: Color,
) {
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = label,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
        )
    } else if (iconRes != null) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(36.dp),
        )
    } else {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

private fun messengerAccent(imType: String): Color = when (imType.lowercase()) {
    "whatsapp" -> Color(0xFF25D366)
    "signal" -> Color(0xFF3A76F0)
    "telegram" -> Color(0xFF2AABEE)
    "discord" -> Color(0xFF5865F2)
    "threema" -> Color(0xFF3FE669)
    "matrix" -> Color(0xFF0DBD8B)
    "xmpp" -> Color(0xFF002B5C)
    "sip" -> Color(0xFF00897B)
    "line" -> Color(0xFF06C755)
    "wechat" -> Color(0xFF07C160)
    "kakaotalk" -> Color(0xFFFAE100)
    "zoom" -> Color(0xFF2D8CFF)
    "irc" -> Color(0xFFE74C3C)
    else -> Color(0xFF7C4DFF)
}

