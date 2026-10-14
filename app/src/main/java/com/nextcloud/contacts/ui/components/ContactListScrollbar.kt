package com.nextcloud.contacts.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nextcloud.contacts.ui.list.AlphabetIndexModel
import com.nextcloud.contacts.ui.list.FAVORITES_INDEX_GLYPH
import com.nextcloud.contacts.ui.list.mapSectionIndexForScroll
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val ContactListScrollbarEndPadding = 14.dp

const val ContactListScrollbarHideDelayMillis = 1000L

private val ThumbThickness = 8.dp
private val TrackWidth = 16.dp
/** Also applied in px where the thumb is drawn. */
private val ThumbMinHeight = 64.dp
/** Minimum thumb height as a fraction of the track. */
private const val ThumbMinSizeFraction = 0.12f
private val ThumbCorner = RoundedCornerShape(percent = 50)
private val LetterPreviewStartPadding = 8.dp

private const val ThumbFadeDurationMillis = 400

private const val LetterFadeDurationMillis = 120

/**
 * Thumb on the end edge. Letter preview stays at the start-center of the viewport.
 * Dragging scrolls by position; this is not an A–Z index.
 */
@Composable
fun ContactListScrollbar(
    listState: LazyListState,
    sectionModel: AlphabetIndexModel,
    showScrollLetters: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val metrics by remember(listState) {
        derivedStateOf { computeScrollbarMetrics(listState) }
    }
    if (!metrics.enabled) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var interacting by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragThumbTopFraction by remember { mutableFloatStateOf(Float.NaN) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    val listScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    // isScrollInProgress stays true for the fling after the finger lifts.
    val wantThumbVisible = interacting || listScrolling
    val wantLettersVisible = showScrollLetters && (interacting || listScrolling)

    val thumbAlphaAnim = remember { Animatable(0f) }
    val thumbAlpha by thumbAlphaAnim.asState()
    LaunchedEffect(wantThumbVisible) {
        if (wantThumbVisible) {
            thumbAlphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ThumbFadeDurationMillis / 2,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            delay(ContactListScrollbarHideDelayMillis)
            thumbAlphaAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = ThumbFadeDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    val letterAlphaAnim = remember { Animatable(0f) }
    val letterAlpha by letterAlphaAnim.asState()
    LaunchedEffect(showScrollLetters, wantLettersVisible) {
        if (!showScrollLetters) {
            letterAlphaAnim.snapTo(0f)
            return@LaunchedEffect
        }
        if (wantLettersVisible) {
            letterAlphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = LetterFadeDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            letterAlphaAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = LetterFadeDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    // No tween. Interpolating toward a moving offset lags for the whole fling.
    val thumbFraction = if (!dragThumbTopFraction.isNaN()) {
        dragThumbTopFraction
    } else {
        metrics.thumbOffsetFraction
    }

    // Visible-item count changes every frame. Freeze thumb height for this gesture.
    var sessionThumbSizeFraction by remember { mutableFloatStateOf(Float.NaN) }
    SideEffect {
        when {
            wantThumbVisible -> {
                if (sessionThumbSizeFraction.isNaN()) {
                    sessionThumbSizeFraction = metrics.thumbSizeFraction
                }
            }
            else -> sessionThumbSizeFraction = Float.NaN
        }
    }
    val thumbSizeFraction = sessionThumbSizeFraction.takeUnless { it.isNaN() }
        ?: metrics.thumbSizeFraction

    val thumbChromeVisible = thumbAlpha > 0.01f
    val lettersChromeVisible = showScrollLetters && letterAlpha > 0.01f
    // Letter tracks the section at the viewport center. Near the ends, scroll
    // progress plus hysteresis. This only picks a letter; it does not scroll.
    var stickySectionIndex by remember(sectionModel) { mutableIntStateOf(-1) }
    val sectionPick by remember(listState, sectionModel, showScrollLetters) {
        derivedStateOf {
            if (!showScrollLetters || sectionModel.isEmpty) return@derivedStateOf null
            // Read scroll offset here or the preview stays stale during a fling.
            listState.firstVisibleItemIndex
            listState.firstVisibleItemScrollOffset
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf null
            val firstVisibleSection = sectionModel.sectionIndexForItem(visible.first().index)
                ?: return@derivedStateOf null
            val lastVisibleSection = sectionModel.sectionIndexForItem(visible.last().index)
                ?: firstVisibleSection
            val itemIndex = listState.itemIndexAtViewportCenter()
                ?: listState.firstVisibleItemIndex
            val centerSection = sectionModel.sectionIndexForItem(itemIndex)
                ?: firstVisibleSection
            SectionPickInputs(
                scrollProgress = listState.scrollProgressFraction(),
                centerBasedIndex = centerSection,
                firstVisibleSectionIndex = firstVisibleSection,
                lastVisibleSectionIndex = lastVisibleSection,
                sectionCount = sectionModel.sections.size,
            )
        }
    }
    val adjustedSection = sectionPick?.let { pick ->
        mapSectionIndexForScroll(
            scrollProgress = pick.scrollProgress,
            centerBasedIndex = pick.centerBasedIndex,
            firstVisibleSectionIndex = pick.firstVisibleSectionIndex,
            lastVisibleSectionIndex = pick.lastVisibleSectionIndex,
            sectionCount = pick.sectionCount,
            previousIndex = stickySectionIndex,
        )
    }
    SideEffect {
        stickySectionIndex = adjustedSection ?: -1
    }
    val previewWindow = adjustedSection?.let { index ->
        sectionModel.previewWindowAt(index)?.takeIf { it.glyphs.isNotEmpty() }
    }

    val unselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val selectedColor = MaterialTheme.colorScheme.primary
    val thumbColor = if (interacting) selectedColor else unselectedColor

    fun scrollToFraction(normalized: Float) {
        scrollJob?.cancel()
        scrollJob = scope.launch {
            scrollListToNormalized(listState, normalized.coerceIn(0f, 1f))
        }
    }

    // No graphicsLayer on this Box. A clipped layer hides the letter overlay.
    Box(modifier = modifier.fillMaxSize()) {
        val thumbMinPx = with(density) { ThumbMinHeight.toPx() }
        val trackH = trackHeightPx.toFloat().coerceAtLeast(1f)
        val thumbHeightPx = (trackH * thumbSizeFraction)
            .coerceAtLeast(thumbMinPx)
            .coerceAtMost(trackH)
        val travelPx = (trackH - thumbHeightPx).coerceAtLeast(0f)
        val thumbTopPx = thumbFraction * travelPx

        if (lettersChromeVisible) {
            previewWindow?.let { window ->
                LetterPreviewStack(
                    glyphs = window.glyphs,
                    currentIndex = window.currentIndex,
                    thumbDragging = interacting,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = LetterPreviewStartPadding)
                        .alpha(letterAlpha),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(TrackWidth)
                .padding(vertical = 4.dp)
                .onSizeChanged { trackHeightPx = it.height }
                .then(
                    if (thumbChromeVisible || wantThumbVisible) {
                        Modifier.pointerInput(metrics.enabled) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val height = size.height.toFloat()
                                if (height <= 0f) return@awaitEachGesture
                                val thumbH = (height * thumbSizeFraction)
                                    .coerceAtLeast(thumbMinPx)
                                    .coerceAtMost(height)
                                val travel = (height - thumbH).coerceAtLeast(1f)

                                interacting = true
                                val top = ((down.position.y - thumbH / 2f) / travel)
                                    .coerceIn(0f, 1f)
                                dragThumbTopFraction = top
                                scrollToFraction(top)
                                drag(down.id) { change ->
                                    val dragTop = ((change.position.y - thumbH / 2f) / travel)
                                        .coerceIn(0f, 1f)
                                    dragThumbTopFraction = dragTop
                                    scrollToFraction(dragTop)
                                    change.consume()
                                }
                                interacting = false
                                dragThumbTopFraction = Float.NaN
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (trackHeightPx > 0 && thumbChromeVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, thumbTopPx.roundToInt()) }
                        .width(ThumbThickness)
                        .height(with(density) { thumbHeightPx.toDp() })
                        .alpha(thumbAlpha)
                        .clip(ThumbCorner)
                        .background(thumbColor),
                )
            }
        }
    }
}

@Composable
private fun LetterPreviewStack(
    glyphs: List<String>,
    currentIndex: Int,
    thumbDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        glyphs.forEachIndexed { index, glyph ->
            val isCurrent = index == currentIndex
            PreviewGlyphCircle(
                glyph = glyph,
                isCurrent = isCurrent,
                thumbDragging = thumbDragging && isCurrent,
            )
        }
    }
}

@Composable
private fun PreviewGlyphCircle(
    glyph: String,
    isCurrent: Boolean,
    thumbDragging: Boolean,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val circleSize = when {
        isCurrent -> 44.dp
        else -> 32.dp
    }
    val bgAlpha = when {
        isCurrent && thumbDragging -> 1f
        isCurrent -> 0.92f
        else -> 0.42f
    }
    val glyphColor = when {
        isCurrent -> onPrimary
        else -> onPrimary.copy(alpha = 0.85f)
    }

    Box(
        modifier = Modifier
            .size(circleSize)
            .clip(CircleShape)
            .background(primary.copy(alpha = bgAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        PreviewGlyph(
            glyph = glyph,
            color = glyphColor,
            large = isCurrent,
        )
    }
}

@Composable
private fun PreviewGlyph(
    glyph: String,
    color: Color,
    large: Boolean,
    modifier: Modifier = Modifier,
) {
    // Many UI fonts have no ★ glyph.
    if (glyph == FAVORITES_INDEX_GLYPH) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = color,
            modifier = modifier.size(if (large) 26.dp else 16.dp),
        )
    } else {
        Text(
            text = glyph,
            style = if (large) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = color,
            fontWeight = if (large) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = modifier,
        )
    }
}

private data class SectionPickInputs(
    val scrollProgress: Float,
    val centerBasedIndex: Int,
    val firstVisibleSectionIndex: Int,
    val lastVisibleSectionIndex: Int,
    val sectionCount: Int,
)

private data class ScrollbarMetrics(
    val thumbOffsetFraction: Float,
    val thumbSizeFraction: Float,
    val enabled: Boolean,
)

/** Item under the viewport midpoint, or the nearest contact row if that point is a gap. */
private fun LazyListState.itemIndexAtViewportCenter(): Int? {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null

    // Item offsets are from the list top. CenterStart puts the letters at half the height.
    val viewportHeight = info.viewportSize.height.takeIf { it > 0 }
        ?: (info.viewportEndOffset - info.viewportStartOffset +
            info.beforeContentPadding + info.afterContentPadding)
    if (viewportHeight <= 0) return null
    val centerY = viewportHeight / 2

    val covering = visible.firstOrNull { item ->
        centerY >= item.offset && centerY < item.offset + item.size
    }
    if (covering != null) return covering.index

    fun distanceToCenter(item: LazyListItemInfo): Int {
        val top = item.offset
        val bottom = item.offset + item.size
        return when {
            centerY < top -> top - centerY
            centerY >= bottom -> centerY - bottom + 1
            else -> 0
        }
    }
    val contactRows = visible.filter { it.contentType == "contact_row" }
    val pool = contactRows.ifEmpty { visible }
    return pool.minByOrNull(::distanceToCenter)?.index
}

/**
 * 0 at the top, 1 at the bottom, snapped with canScrollBackward / canScrollForward.
 * First visible index plus offset in that item. Averaging visible row heights makes the thumb jump.
 */
private fun LazyListState.scrollProgressFraction(): Float {
    if (!canScrollBackward) return 0f
    if (!canScrollForward) return 1f
    return indexBasedScrollFraction()
}

/** First visible index plus offset in that item. Other on-screen rows are ignored. */
private fun LazyListState.indexBasedScrollFraction(): Float {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val total = info.totalItemsCount
    if (visible.isEmpty() || total <= 1) return 0f

    val firstSize = visible.first().size.coerceAtLeast(1)
    val frac = (firstVisibleItemScrollOffset.toFloat() / firstSize).coerceIn(0f, 1f)
    val position = firstVisibleItemIndex + frac
    val maxPosition = (total - 1).toFloat().coerceAtLeast(1f)
    return (position / maxPosition).coerceIn(0f, 1f)
}

/** Average contact-row height times the item count. Headers would skew it. */
private fun LazyListState.estimatedMainAxisContentHeight(): Float {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty() || info.totalItemsCount == 0) return 0f
    val rows = visible.filter { it.contentType == "contact_row" }
    val pool = rows.ifEmpty { visible }
    val avgItemSize = pool.map { it.size }.average().toFloat().coerceAtLeast(1f)
    return avgItemSize * info.totalItemsCount.toFloat() +
        info.beforeContentPadding.toFloat() +
        info.afterContentPadding.toFloat()
}

private fun computeScrollbarMetrics(listState: LazyListState): ScrollbarMetrics {
    val info = listState.layoutInfo
    val visible = info.visibleItemsInfo
    val total = info.totalItemsCount
    if (visible.isEmpty() || total == 0) {
        return ScrollbarMetrics(0f, 1f, enabled = false)
    }

    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    if (viewport <= 0f) {
        return ScrollbarMetrics(0f, 1f, enabled = false)
    }

    if (!listState.canScrollForward && !listState.canScrollBackward) {
        return ScrollbarMetrics(0f, 1f, enabled = false)
    }

    // Not the visible-item count. That changes as rows cross the edge.
    val contentHeight = listState.estimatedMainAxisContentHeight()
    val thumbSize = if (contentHeight <= viewport) {
        1f
    } else {
        (viewport / contentHeight).coerceIn(ThumbMinSizeFraction, 1f)
    }
    val travelNorm = when {
        !listState.canScrollBackward -> 0f
        !listState.canScrollForward -> 1f
        else -> listState.indexBasedScrollFraction()
    }

    return ScrollbarMetrics(
        thumbOffsetFraction = travelNorm,
        thumbSizeFraction = thumbSize,
        enabled = true,
    )
}

private suspend fun scrollListToNormalized(listState: LazyListState, normalized: Float) {
    val total = listState.layoutInfo.totalItemsCount
    if (total <= 0) return

    val n = normalized.coerceIn(0f, 1f)
    if (n <= 0f || total == 1) {
        listState.scrollToItem(0, 0)
        return
    }
    if (n >= 1f) {
        listState.scrollToItem(total - 1, 0)
        return
    }

    // Same index-plus-fraction as [indexBasedScrollFraction].
    val position = n * (total - 1).toFloat()
    val index = position.toInt().coerceIn(0, total - 1)
    val frac = (position - index).coerceIn(0f, 1f)
    val itemSize = listState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == index }?.size
        ?: listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size
        ?: 1
    val offset = (frac * itemSize).roundToInt().coerceAtLeast(0)
    listState.scrollToItem(index, offset)
}
