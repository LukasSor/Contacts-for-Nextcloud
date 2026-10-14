package com.nextcloud.contacts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.prefs.AvatarFrameStyle

@Composable
fun ContactPhotoViewer(
    contactId: Long,
    displayName: String,
    frameStyle: AvatarFrameStyle,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1210))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            FramedContactPhoto(
                contactId = contactId,
                displayName = displayName,
                frameStyle = frameStyle,
                size = 320.dp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* absorb taps on the photo */ },
                ),
            )
        }
    }
}

@Composable
fun FramedContactPhoto(
    contactId: Long,
    displayName: String,
    frameStyle: AvatarFrameStyle,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val photoStore = remember { (context.applicationContext as ContactsApp).container.contactPhotoStore }
    val photoFile = remember(contactId) { photoStore.photoFile(contactId) }
    val hasPhoto = photoFile.exists() && photoFile.length() > 0L
    val organicMask = if (frameStyle == AvatarFrameStyle.ORGANIC) {
        ImageBitmap.imageResource(R.drawable.avatar_frame_organic_mask)
    } else {
        null
    }

    val frameModifier = when (frameStyle) {
        AvatarFrameStyle.CIRCLE -> Modifier.clip(CircleShape)
        AvatarFrameStyle.ORGANIC -> Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                organicMask?.let { mask ->
                    drawImage(
                        image = mask,
                        dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
    }

    Box(
        modifier = modifier
            .size(size)
            .then(frameModifier)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (hasPhoto) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photoFile)
                    .crossfade(true)
                    .build(),
                contentDescription = displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initialsForViewer(displayName),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium,
                fontSize = (size.value * 0.28f).sp,
            )
        }
    }
}

private fun initialsForViewer(displayName: String): String {
    val parts = displayName
        .trim()
        .split(Regex("\\s+"))
        .mapNotNull { part ->
            part.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString()
        }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first()
        else -> parts.first() + parts.last()
    }
}
