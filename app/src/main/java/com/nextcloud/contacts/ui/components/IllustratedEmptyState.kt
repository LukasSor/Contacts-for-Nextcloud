package com.nextcloud.contacts.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private val EmptyIconSize = 84.dp
private val EmptyClusterSize = 176.dp

private data class BubbleSpec(
    val size: Dp,
    val x: Dp,
    val y: Dp,
    val driftX: Dp,
    val driftY: Dp,
    val phase: Float,
)

/** Irregular, not a ring. Offsets are from the icon center. */
private val BubbleSpecs = listOf(
    BubbleSpec(56.dp, (-38).dp, (-28).dp, 6.dp, 4.dp, 0.00f),
    BubbleSpec(32.dp, 44.dp, (-36).dp, 4.dp, 7.dp, 0.18f),
    BubbleSpec(18.dp, 60.dp, 8.dp, 5.dp, 3.dp, 0.41f),
    BubbleSpec(40.dp, 16.dp, 36.dp, 3.dp, 6.dp, 0.63f),
    BubbleSpec(14.dp, (-12).dp, 56.dp, 7.dp, 4.dp, 0.79f),
    BubbleSpec(26.dp, (-54).dp, 16.dp, 5.dp, 5.dp, 0.29f),
)

@Composable
fun IllustratedEmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(EmptyClusterSize),
            contentAlignment = Alignment.Center,
        ) {
            // Animator duration scale 0 leaves the bubbles in their rest pose.
            if (ValueAnimator.areAnimatorsEnabled()) {
                DriftingBubbles()
            } else {
                Bubbles(clock = null)
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(EmptyIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!supporting.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DriftingBubbles() {
    val transition = rememberInfiniteTransition(label = "emptyBubbles")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "emptyBubbleClock",
    )
    Bubbles(clock)
}

@Composable
private fun Bubbles(clock: Float?) {
    val colors = bubbleColors()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        BubbleSpecs.forEachIndexed { index, spec ->
            val driftX = clock?.let { bubbleWave(it, spec.phase) } ?: 0f
            val driftY = clock?.let { bubbleWave(it, spec.phase + 0.37f) } ?: 0f
            val scaleWave = clock?.let { bubbleWave(it, spec.phase + 0.23f) } ?: 0f
            val fadeWave = clock?.let { bubbleWave(it, spec.phase + 0.51f) } ?: 0f
            Box(
                modifier = Modifier
                    .offset(spec.x, spec.y)
                    .size(spec.size)
                    .graphicsLayer {
                        val scale = 1f + 0.06f * scaleWave
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.75f + 0.25f * ((fadeWave + 1f) / 2f)
                        translationX = spec.driftX.toPx() * driftX
                        translationY = spec.driftY.toPx() * driftY
                    }
                    .background(colors[index], CircleShape),
            )
        }
    }
}

@Composable
private fun bubbleColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return listOf(
        scheme.primary.copy(alpha = 0.22f),
        scheme.tertiary.copy(alpha = 0.30f),
        scheme.surfaceContainerHighest,
        scheme.secondary.copy(alpha = 0.26f),
        scheme.primaryContainer.copy(alpha = 0.62f),
        scheme.secondaryContainer.copy(alpha = 0.70f),
    )
}

private fun bubbleWave(clock: Float, phase: Float): Float {
    return sin((clock + phase) * 2.0 * PI).toFloat()
}
