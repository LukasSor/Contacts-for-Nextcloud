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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nextcloud.contacts.ui.list.AlphabetIndexModel
import com.nextcloud.contacts.ui.list.AlphabetIndexSection
import com.nextcloud.contacts.ui.list.FAVORITES_INDEX_GLYPH
import com.nextcloud.contacts.ui.list.mapSectionIndexForScroll
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

val ContactListScrollbarEndPadding = 14.dp

val ContactListLetterStripEndPadding = 36.dp

const val ContactListScrollbarHideDelayMillis = 1000L

private val LetterStripWidth = 28.dp

/** End inset plus width stays inside [ContactListLetterStripEndPadding]. */
private val LetterStripEndInset = 4.dp

/**
 * Temporary start strip. Width stays inside the list's 16.dp start padding
 * so the letters sit in the gutter and do not cover the contact cards.
 */
private val StartLetterStripWidth = 14.dp

/**
 * Compact pitch. A little looser than 16dp, still a centered block,
 * not stretched over the list height.
 */
private val LetterSlotHeight = 20.dp

/** Immediate press zone. Stays in the list gutter, clear of the avatar. */
private val StartLetterGutter = 20.dp

/** Drag zone. Covers the side where the scrolling letters sit. */
private val StartLetterDragBand = 56.dp

private val ThumbThickness = 8.dp
private val TrackWidth = 16.dp
private val ThumbMinHeight = 64.dp
private const val ThumbMinSizeFraction = 0.12f
private val ThumbCorner = RoundedCornerShape(percent = 50)
private val LetterPreviewStartPadding = 8.dp

private const val ThumbFadeDurationMillis = 400

private const val LetterFadeDurationMillis = 120

@Composable
fun ContactListScrollbar(
    listState: LazyListState,
    sectionModel: AlphabetIndexModel,
    showScrollLetters: Boolean = true,
    lettersOnTheSide: Boolean = false,
    startScrubSection: Int? = null,
    modifier: Modifier = Modifier,
) {
    val metrics by remember(listState) {
        derivedStateOf { computeScrollbarMetrics(listState) }
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var interacting by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragThumbTopFraction by remember { mutableFloatStateOf(Float.NaN) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var endDragSection by remember { mutableIntStateOf(-1) }

    val listScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    // Offset changes cover scrolls that never flip isScrollInProgress, including
    // a fling that starts while a strip gesture is being released.
    var recentScroll by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        var previous: Pair<Int, Int>? = null
        var hideJob: Job? = null
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { current ->
            val prev = previous
            previous = current
            if (prev == null || prev == current) return@collect
            recentScroll = true
            hideJob?.cancel()
            hideJob = launch {
                delay(80)
                recentScroll = false
            }
        }
    }
    val endStripActive = endDragSection in sectionModel.sections.indices
    // True only between strip pointer-down and pointer-up.
    var stripFingerDown by remember { mutableStateOf(false) }
    val fingerOnLetterStrip = stripFingerDown || startScrubSection != null
    val wantThumbVisible = interacting || listScrolling
    // Ordinary scrolling and a drag on either letter strip show the short stack.
    val wantLettersVisible = showScrollLetters &&
        (fingerOnLetterStrip || interacting || listScrolling || (lettersOnTheSide && recentScroll))

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

    val thumbFraction = if (!dragThumbTopFraction.isNaN()) {
        dragThumbTopFraction
    } else {
        metrics.thumbOffsetFraction
    }

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
    var stickySectionIndex by remember(sectionModel) { mutableIntStateOf(-1) }
    val trackSection = showScrollLetters || lettersOnTheSide
    val sectionPick by remember(listState, sectionModel, trackSection) {
        derivedStateOf {
            if (!trackSection || sectionModel.isEmpty) return@derivedStateOf null
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
    val selectedSection = when {
        startScrubSection != null && startScrubSection in sectionModel.sections.indices ->
            startScrubSection
        endDragSection in sectionModel.sections.indices -> endDragSection
        else -> adjustedSection ?: -1
    }
    // Follow the finger while a strip is dragged, and the list letter otherwise.
    val previewWindow = selectedSection
        .takeIf { it in sectionModel.sections.indices }
        ?.let { index ->
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

    fun jumpToSection(index: Int) {
        val itemIndex = sectionModel.sections.getOrNull(index)?.firstItemIndex ?: return
        scrollJob?.cancel()
        scrollJob = scope.launch {
            listState.scrollToItem(itemIndex)
        }
    }

    val showThumb = !lettersOnTheSide && metrics.enabled
    val showEndStrip = lettersOnTheSide && sectionModel.sections.isNotEmpty()
    val showStartStrip = startScrubSection != null && sectionModel.sections.isNotEmpty()
    val centerGlyph = when {
        showStartStrip -> sectionModel.sections.getOrNull(selectedSection)?.glyph
        endStripActive -> sectionModel.sections.getOrNull(endDragSection)?.glyph
        else -> null
    }
    if (!showThumb && !showEndStrip && !showStartStrip && !lettersChromeVisible) return

    Box(modifier = modifier.fillMaxSize()) {
        val thumbMinPx = with(density) { ThumbMinHeight.toPx() }
        val trackH = trackHeightPx.toFloat().coerceAtLeast(1f)
        val thumbHeightPx = (trackH * thumbSizeFraction)
            .coerceAtLeast(thumbMinPx)
            .coerceAtMost(trackH)
        val travelPx = (trackH - thumbHeightPx).coerceAtLeast(0f)
        val thumbTopPx = thumbFraction * travelPx

        if (showStartStrip) {
            AlphabetLetterStrip(
                sections = sectionModel.sections,
                selectedIndex = selectedSection,
                stripWidth = StartLetterStripWidth,
                maxMarkSize = StartLetterStripWidth,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        if (showEndStrip) {
            AlphabetLetterStrip(
                sections = sectionModel.sections,
                selectedIndex = selectedSection,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = LetterStripEndInset),
                onSelect = { index ->
                    stripFingerDown = true
                    endDragSection = index
                    jumpToSection(index)
                },
                onGestureEnd = {
                    stripFingerDown = false
                    endDragSection = -1
                },
            )
        } else if (showThumb) {
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

        centerGlyph?.let { glyph ->
            CenterLetterBubble(
                glyph = glyph,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Above either strip. Drawn while the list scrolls and while a finger
        // drags either letter strip, when scroll letters are enabled.
        if (lettersChromeVisible) {
            previewWindow?.let { window ->
                LetterPreviewStack(
                    glyphs = window.glyphs,
                    currentIndex = window.currentIndex,
                    thumbDragging = interacting,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = LetterPreviewStartPadding)
                        .zIndex(1f)
                        .alpha(letterAlpha),
                )
            }
        }
    }
}

/**
 * Start-edge scrubber. A quick tap still opens the row. A press in the gutter,
 * or a drag on the side where the scrolling letters sit, shows the letter strip
 * and the center letter. The band is the start edge, so RTL stays on the start side.
 */
@Composable
fun Modifier.contactListStartLetterScrub(
    enabled: Boolean,
    listState: LazyListState,
    sectionModel: AlphabetIndexModel,
    onActiveSection: (Int?) -> Unit,
): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val gutterPx = with(density) { StartLetterGutter.toPx() }
    val bandPx = with(density) { StartLetterDragBand.toPx() }
    val scope = rememberCoroutineScope()
    val scrollJob = remember { mutableStateOf<Job?>(null) }
    if (!enabled || sectionModel.isEmpty) return this
    return pointerInput(sectionModel, layoutDirection, gutterPx, bandPx) {
        val touchSlop = viewConfiguration.touchSlop
        try {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val width = size.width.toFloat()
                val fromStart = if (layoutDirection == LayoutDirection.Rtl) {
                    width - down.position.x
                } else {
                    down.position.x
                }
                if (fromStart > bandPx) return@awaitEachGesture

                val pointerId = down.id
                val originY = down.position.y
                var scrubbing = fromStart <= gutterPx
                var lastIndex = -1

                fun select(y: Float) {
                    val count = sectionModel.sections.size
                    val available = size.height.toFloat()
                    val slotPx = letterSlotPx(available, count, LetterSlotHeight.toPx())
                    val blockHeight = slotPx * count.toFloat()
                    val index = sectionIndexAtY(
                        y = y - letterBlockTopPx(available, blockHeight),
                        height = blockHeight,
                        count = count,
                    )
                    if (index == lastIndex) return
                    lastIndex = index
                    onActiveSection(index)
                    val itemIndex = sectionModel.sections[index].firstItemIndex
                    scrollJob.value?.cancel()
                    scrollJob.value = scope.launch {
                        listState.scrollToItem(itemIndex)
                    }
                }

                try {
                    if (scrubbing) {
                        down.consume()
                        select(originY)
                    }

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            if (scrubbing) change.consume()
                            break
                        }
                        if (!scrubbing && abs(change.position.y - originY) >= touchSlop) {
                            scrubbing = true
                        }
                        if (scrubbing) {
                            change.consume()
                            select(change.position.y)
                        }
                    }
                } finally {
                    // Also when the pointer disappears without an up event.
                    if (scrubbing) onActiveSection(null)
                }
            }
        } finally {
            onActiveSection(null)
        }
    }
}

@Composable
private fun AlphabetLetterStrip(
    sections: List<AlphabetIndexSection>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    stripWidth: Dp = LetterStripWidth,
    maxMarkSize: Dp = 18.dp,
    onSelect: ((Int) -> Unit)? = null,
    onGestureEnd: (() -> Unit)? = null,
) {
    if (sections.isEmpty()) return
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(
        modifier = modifier
            .width(stripWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        val availablePx = with(density) { maxHeight.toPx() }
        val slotPx = letterSlotPx(availablePx, sections.size, with(density) { LetterSlotHeight.toPx() })
        val slot = with(density) { slotPx.toDp() }
        val fontSize = stripFontSize(slot)
        val markSize = slot.coerceAtMost(maxMarkSize).coerceAtLeast(10.dp)
        val blockHeight = slot * sections.size
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(blockHeight)
                .then(
                    if (onSelect != null) {
                        Modifier.pointerInput(sections, slotPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                try {
                                    fun select(y: Float) {
                                        onSelect(
                                            sectionIndexAtY(
                                                y = y,
                                                height = size.height.toFloat(),
                                                count = sections.size,
                                            ),
                                        )
                                    }
                                    select(down.position.y)
                                    down.consume()
                                    drag(down.id) { change ->
                                        select(change.position.y)
                                        change.consume()
                                    }
                                } finally {
                                    onGestureEnd?.invoke()
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sections.forEachIndexed { index, section ->
                Box(
                    modifier = Modifier
                        .height(slot)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    StripGlyph(
                        glyph = section.glyph,
                        selected = index == selectedIndex,
                        primary = primary,
                        onSurface = onSurface,
                        fontSize = fontSize,
                        markSize = markSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun StripGlyph(
    glyph: String,
    selected: Boolean,
    primary: Color,
    onSurface: Color,
    fontSize: TextUnit,
    markSize: Dp,
) {
    val color = if (selected) primary else onSurface
    Box(
        modifier = Modifier
            .size(markSize)
            .then(
                if (selected) {
                    Modifier
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.16f))
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph == FAVORITES_INDEX_GLYPH) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(markSize * 0.62f),
            )
        } else {
            Text(
                text = glyph,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = color,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CenterLetterBubble(
    glyph: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph == FAVORITES_INDEX_GLYPH) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(48.dp),
            )
        } else {
            Text(
                text = glyph.take(1),
                style = MaterialTheme.typography.displaySmall,
                color = primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private fun stripFontSize(slot: Dp): TextUnit {
    val raw = (slot.value * 0.68f).sp
    return when {
        raw < 9.sp -> 9.sp
        raw > 11.sp -> 11.sp
        else -> raw
    }
}

/** Preferred pitch, shrunk only when the centered block would not fit. */
private fun letterSlotPx(availablePx: Float, count: Int, preferredSlotPx: Float): Float {
    if (count <= 0 || availablePx <= 0f) return preferredSlotPx
    return preferredSlotPx.coerceAtMost(availablePx / count.toFloat())
}

private fun letterBlockTopPx(availablePx: Float, blockHeightPx: Float): Float {
    return ((availablePx - blockHeightPx) / 2f).coerceAtLeast(0f)
}

private fun sectionIndexAtY(y: Float, height: Float, count: Int): Int {
    if (count <= 1 || height <= 0f) return 0
    val fraction = (y / height).coerceIn(0f, 0.999999f)
    return (fraction * count).toInt().coerceIn(0, count - 1)
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

private fun LazyListState.itemIndexAtViewportCenter(): Int? {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null

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

private fun LazyListState.scrollProgressFraction(): Float {
    if (!canScrollBackward) return 0f
    if (!canScrollForward) return 1f
    return indexBasedScrollFraction()
}

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
