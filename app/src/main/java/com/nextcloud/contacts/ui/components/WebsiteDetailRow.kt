package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.data.local.ContactFieldOptions
import com.nextcloud.contacts.ui.util.localizedUrlLabel
import com.nextcloud.contacts.data.local.UrlEntity
import com.nextcloud.contacts.data.local.WebsiteLogoCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WebsiteDetailRow(
    url: UrlEntity,
    contactId: Long,
    websiteLogoCache: String,
    enableWebsiteLogos: Boolean,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ContactsApp).container }
    val websiteKey = remember(url.value) { WebsiteLogoCache.normalizeWebsiteKey(url.value) }
    val cachedLogo = remember(websiteKey, websiteLogoCache) {
        WebsiteLogoCache.get(websiteLogoCache, url.value)
    }
    var logoBytes by remember(websiteKey, cachedLogo?.fileName) {
        mutableStateOf<ByteArray?>(null)
    }

    LaunchedEffect(websiteKey, cachedLogo?.fileName) {
        logoBytes = withContext(Dispatchers.IO) {
            cachedLogo?.fileName?.takeIf { container.websiteLogoStore.hasLogo(it) }
                ?.let { container.websiteLogoStore.readLogoBytes(it) }
        }
    }

    LaunchedEffect(enableWebsiteLogos, contactId, url.value, websiteLogoCache) {
        if (!enableWebsiteLogos || websiteKey.isBlank()) return@LaunchedEffect
        val seed = WebsiteLogoCache.get(websiteLogoCache, url.value)
        val seedBytes = withContext(Dispatchers.IO) {
            seed?.fileName?.takeIf { container.websiteLogoStore.hasLogo(it) }
                ?.let { container.websiteLogoStore.readLogoBytes(it) }
        }
        if (seedBytes != null) {
            logoBytes = seedBytes
        }
        val refreshed = withContext(Dispatchers.IO) {
            container.contactRepository.refreshWebsiteLogo(contactId, url.value)
        }
        if (refreshed != null) {
            val bytes = withContext(Dispatchers.IO) {
                container.websiteLogoStore.readLogoBytes(refreshed.fileName)
            }
            if (bytes != null) {
                logoBytes = bytes
            }
        }
    }

    val logoBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, logoBytes) {
        value = logoBytes?.let { bytes ->
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
    }

    val openUri = remember(url.value) {
        if (url.value.startsWith("http")) {
            Uri.parse(url.value)
        } else {
            Uri.parse("https://${url.value}")
        }
    }

    ContactDetailFieldRow(
        icon = Icons.Default.Link,
        leadingIcon = if (enableWebsiteLogos && logoBitmap != null) {
            {
                val bitmap = logoBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            null
        },
        value = url.value,
        label = localizedUrlLabel(url.type),
        asLink = true,
        onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, openUri))
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.cd_open_website),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(20.dp),
            )
        },
    )
}
