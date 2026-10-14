package com.nextcloud.contacts.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.util.AvatarBitmapCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws [thumbnail] or [AvatarBitmapCache.peek]. Do not remember a cache miss:
 * a recycled row has to pick up a thumb decoded after it first composed.
 */
@Composable
fun ContactAvatar(
    contactId: Long,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    photoBytes: ByteArray? = null,
    loadStoredPhoto: Boolean = true,
    emptyIcon: ImageVector? = null,
    thumbnail: ImageBitmap? = null,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
    val initialsText = remember(displayName) { initials(displayName) }

    // Attached thumb, else a memory peek. Disk is the async branch below.
    val warm = when {
        thumbnail != null -> thumbnail
        photoBytes != null -> null
        loadStoredPhoto -> AvatarBitmapCache.peek(contactId, sizePx)
        else -> null
    }

    if (warm != null) {
        ContactAvatarChrome(
            bitmap = warm,
            initialsText = initialsText,
            size = size,
            emptyIcon = emptyIcon,
            modifier = modifier,
        )
    } else {
        ContactAvatarAsync(
            contactId = contactId,
            sizePx = sizePx,
            photoBytes = photoBytes,
            loadStoredPhoto = loadStoredPhoto,
            initialsText = initialsText,
            size = size,
            emptyIcon = emptyIcon,
            modifier = modifier,
        )
    }
}

@Composable
private fun ContactAvatarAsync(
    contactId: Long,
    sizePx: Int,
    photoBytes: ByteArray?,
    loadStoredPhoto: Boolean,
    initialsText: String,
    size: Dp,
    emptyIcon: ImageVector?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val photoStore = remember {
        (context.applicationContext as ContactsApp).container.contactPhotoStore
    }
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        contactId,
        sizePx,
        photoBytes,
        loadStoredPhoto,
    ) {
        value = when {
            photoBytes != null -> withContext(Dispatchers.Default) {
                AvatarBitmapCache.getOrDecodeBytes(
                    bytes = photoBytes,
                    cacheKey = "bytes-$contactId-${photoBytes.size}-$sizePx",
                    sizePx = sizePx,
                )
            }
            loadStoredPhoto -> withContext(Dispatchers.IO) {
                AvatarBitmapCache.getOrDecode(photoStore, contactId, sizePx)
            }
            else -> null
        }
    }
    ContactAvatarChrome(
        bitmap = bitmap,
        initialsText = initialsText,
        size = size,
        emptyIcon = emptyIcon,
        modifier = modifier,
    )
}

@Composable
private fun ContactAvatarChrome(
    bitmap: ImageBitmap?,
    initialsText: String,
    size: Dp,
    emptyIcon: ImageVector?,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            AvatarPlaceholder(initialsText, size, emptyIcon)
        }
    }
}

@Composable
private fun AvatarPlaceholder(
    initialsText: String,
    size: Dp,
    emptyIcon: ImageVector?,
) {
    if (emptyIcon != null) {
        Icon(
            imageVector = emptyIcon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.4f),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        Text(
            text = initialsText,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Medium,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

private fun initials(displayName: String): String {
    val parts = displayName
        .trim()
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        // Ignore nickname / note segments like "(wow)" or "[work]"
        .filterNot { part ->
            val wrapped = part.firstOrNull() to part.lastOrNull()
            wrapped == '(' to ')' || wrapped == '[' to ']' || wrapped == '{' to '}'
        }
        .mapNotNull { part ->
            part.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString()
        }

    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first()
        else -> parts.first() + parts.last()
    }
}
