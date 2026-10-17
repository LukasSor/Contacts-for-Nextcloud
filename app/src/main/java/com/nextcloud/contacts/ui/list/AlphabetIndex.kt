package com.nextcloud.contacts.ui.list

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

const val FAVORITES_INDEX_GLYPH = "★"

/** Each end of the scroll range uses progress, not the viewport center. */
const val AlphabetScrollEdgeBand = 0.15f

/** Sticky width around [AlphabetScrollEdgeBand] so the letter does not flicker on the boundary. */
const val AlphabetScrollEdgeHysteresis = 0.03f

/** Neighbor letters wait until scroll passes their midpoint by this fraction of a slot. */
const val AlphabetSectionHysteresisSlots = 0.30f

@Immutable
data class AlphabetIndexSection(
    val glyph: String,
    /** Header index, or the first row when the section has no header. */
    val firstItemIndex: Int,
)

@Immutable
data class AlphabetIndexModel(
    val sections: List<AlphabetIndexSection>,
    /** Parallel to the lazy list. Empty when there are no sections. */
    val glyphByItemIndex: List<String>,
) {
    val isEmpty: Boolean get() = sections.isEmpty()

    fun glyphAtItemIndex(index: Int): String? {
        if (glyphByItemIndex.isEmpty()) return null
        val i = index.coerceIn(0, glyphByItemIndex.lastIndex)
        return glyphByItemIndex[i].ifEmpty { null }
    }

    /** Up to three glyphs around [glyph], clamped so an end letter still fills the window. */
    fun previewWindow(glyph: String): PreviewWindow? {
        if (sections.isEmpty()) return null
        val i = sections.indexOfFirst { it.glyph == glyph }
        if (i < 0) return PreviewWindow(glyphs = listOf(glyph), currentIndex = 0)
        return previewWindowAt(i)
    }

    fun previewWindowAt(sectionIndex: Int): PreviewWindow? {
        if (sections.isEmpty()) return null
        val i = sectionIndex.coerceIn(0, sections.lastIndex)
        val n = sections.size
        if (n <= 3) {
            return PreviewWindow(
                glyphs = sections.map { it.glyph },
                currentIndex = i,
            )
        }
        val start = (i - 1).coerceIn(0, n - 3)
        return PreviewWindow(
            glyphs = listOf(
                sections[start].glyph,
                sections[start + 1].glyph,
                sections[start + 2].glyph,
            ),
            currentIndex = i - start,
        )
    }

    fun sectionIndexOfGlyph(glyph: String): Int =
        sections.indexOfFirst { it.glyph == glyph }

    /** Null when [itemIndex] has no section. */
    fun sectionIndexForItem(itemIndex: Int): Int? {
        val glyph = glyphAtItemIndex(itemIndex) ?: return null
        val i = sectionIndexOfGlyph(glyph)
        return i.takeIf { it >= 0 }
    }
}

/**
 * End bands follow scroll progress, not the first or last visible row. Those
 * indices move by a pixel and the letter oscillates. [previousIndex] holds the
 * letter across a neighbor change and across the band edge.
 */
fun mapSectionIndexForScroll(
    scrollProgress: Float,
    centerBasedIndex: Int,
    firstVisibleSectionIndex: Int,
    lastVisibleSectionIndex: Int,
    sectionCount: Int,
    previousIndex: Int = -1,
    edgeBand: Float = AlphabetScrollEdgeBand,
    edgeHysteresis: Float = AlphabetScrollEdgeHysteresis,
    neighborHysteresisSlots: Float = AlphabetSectionHysteresisSlots,
): Int {
    if (sectionCount <= 0) return 0
    if (sectionCount == 1) return 0
    val last = sectionCount - 1
    val firstVisible = firstVisibleSectionIndex.coerceIn(0, last)
    val lastVisible = lastVisibleSectionIndex.coerceIn(firstVisible, last)
    val center = centerBasedIndex.coerceIn(0, last)
    val band = edgeBand.coerceIn(0.01f, 0.49f)
    val hyster = edgeHysteresis.coerceIn(0f, band)
    val p = scrollProgress.coerceIn(0f, 1f)

    // End bands follow scroll offset. The first and last visible rows chatter.
    val progressIndex = (p * last).roundToInt().coerceIn(0, last)

    val inTopBand = inEdgeBand(
        progress = p,
        band = band,
        hyster = hyster,
        previousIndex = previousIndex,
        sectionLast = last,
        top = true,
    )
    val inBottomBand = !inTopBand && inEdgeBand(
        progress = p,
        band = band,
        hyster = hyster,
        previousIndex = previousIndex,
        sectionLast = last,
        top = false,
    )

    val raw = when {
        p <= 0f -> 0
        p >= 1f -> last
        inTopBand || inBottomBand -> progressIndex
        center in firstVisible..lastVisible -> center
        center < firstVisible -> firstVisible
        else -> lastVisible
    }

    return stabilizeSectionIndex(
        candidate = raw,
        previous = previousIndex,
        scrollProgress = p,
        sectionCount = sectionCount,
        neighborHysteresisSlots = neighborHysteresisSlots,
    )
}

/** Holds [previous] for a one-letter step until scroll clears the midpoint. A larger jump applies at once. */
fun stabilizeSectionIndex(
    candidate: Int,
    previous: Int,
    scrollProgress: Float,
    sectionCount: Int,
    neighborHysteresisSlots: Float = AlphabetSectionHysteresisSlots,
): Int {
    if (sectionCount <= 1) return 0
    val last = sectionCount - 1
    val cand = candidate.coerceIn(0, last)
    if (previous < 0) return cand
    val prev = previous.coerceIn(0, last)
    if (cand == prev) return prev

    val delta = cand - prev
    if (kotlin.math.abs(delta) >= 2) return cand

    val slot = 1f / last.toFloat()
    val boundary = (prev + cand) / 2f * slot
    val margin = neighborHysteresisSlots.coerceIn(0f, 0.49f) * slot
    val p = scrollProgress.coerceIn(0f, 1f)
    return if (cand > prev) {
        if (p >= boundary + margin) cand else prev
    } else {
        if (p <= boundary - margin) cand else prev
    }
}

/** Sticky around [band] so the edge and middle mappings do not swap every frame. */
private fun inEdgeBand(
    progress: Float,
    band: Float,
    hyster: Float,
    previousIndex: Int,
    sectionLast: Int,
    top: Boolean,
): Boolean {
    val edgeSectionLimit =
        ((band + hyster) * sectionLast).roundToInt().coerceIn(0, sectionLast)
    val wasInThisBand = when {
        previousIndex < 0 -> false
        top -> previousIndex <= edgeSectionLimit
        else -> previousIndex >= sectionLast - edgeSectionLimit
    }
    return when {
        previousIndex < 0 -> if (top) progress <= band else progress >= 1f - band
        wasInThisBand -> if (top) progress <= band + hyster else progress >= 1f - band - hyster
        else -> if (top) progress <= band - hyster else progress >= 1f - band + hyster
    }
}

@Immutable
data class PreviewWindow(
    val glyphs: List<String>,
    /** Slot in [glyphs] that is current. Not always the middle one. */
    val currentIndex: Int,
)

/** Letters that actually occur in [entries]. Favorites use [FAVORITES_INDEX_GLYPH]. */
fun buildAlphabetIndexModel(entries: List<ContactListEntry>): AlphabetIndexModel {
    if (entries.isEmpty()) {
        return AlphabetIndexModel(sections = emptyList(), glyphByItemIndex = emptyList())
    }

    val sections = ArrayList<AlphabetIndexSection>(16)
    val glyphs = ArrayList<String>(entries.size)
    var currentGlyph = ""

    entries.forEachIndexed { index, entry ->
        when (entry) {
            is ContactListEntry.FavoritesHeader -> {
                currentGlyph = FAVORITES_INDEX_GLYPH
                sections.add(AlphabetIndexSection(currentGlyph, index))
            }
            is ContactListEntry.LetterHeader -> {
                currentGlyph = entry.letter
                sections.add(AlphabetIndexSection(currentGlyph, index))
            }
            is ContactListEntry.ContactRow -> {
                if (currentGlyph.isEmpty()) {
                    currentGlyph = if (entry.sectionId == "favorites") {
                        FAVORITES_INDEX_GLYPH
                    } else {
                        entry.sectionId
                    }
                    sections.add(AlphabetIndexSection(currentGlyph, index))
                }
            }
            is ContactListEntry.FavoriteCardRow -> {
                if (currentGlyph.isEmpty()) {
                    currentGlyph = FAVORITES_INDEX_GLYPH
                    sections.add(AlphabetIndexSection(currentGlyph, index))
                }
            }
        }
        glyphs.add(currentGlyph)
    }

    return AlphabetIndexModel(sections = sections, glyphByItemIndex = glyphs)
}
