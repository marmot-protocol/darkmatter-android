package dev.ipf.whitenoise.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the composer expansion state machine (#324): the expand arrow cycles
 * Compact → Expanded → FullScreen → Compact, while the collapse affordance
 * (system back or the full-screen back arrow) steps back exactly one state —
 * full screen returns to Expanded, never straight to Compact.
 */
class ComposerExpansionTest {
    @Test
    fun expandArrowAdvancesCompactToExpanded() {
        assertEquals(
            ComposerExpansion.Expanded,
            advancedComposerExpansion(ComposerExpansion.Compact),
        )
    }

    @Test
    fun expandArrowAdvancesExpandedToFullScreen() {
        assertEquals(
            ComposerExpansion.FullScreen,
            advancedComposerExpansion(ComposerExpansion.Expanded),
        )
    }

    @Test
    fun expandArrowWrapsFullScreenBackToCompact() {
        assertEquals(
            ComposerExpansion.Compact,
            advancedComposerExpansion(ComposerExpansion.FullScreen),
        )
    }

    @Test
    fun threeTapsOfTheExpandArrowReturnToTheStartingState() {
        var state = ComposerExpansion.Compact
        repeat(3) { state = advancedComposerExpansion(state) }
        assertEquals(ComposerExpansion.Compact, state)
    }

    @Test
    fun collapseReturnsFullScreenToExpandedNotCompact() {
        assertEquals(
            ComposerExpansion.Expanded,
            collapsedComposerExpansion(ComposerExpansion.FullScreen),
        )
    }

    @Test
    fun collapseReturnsExpandedToCompact() {
        assertEquals(
            ComposerExpansion.Compact,
            collapsedComposerExpansion(ComposerExpansion.Expanded),
        )
    }

    @Test
    fun collapseOnTheCompactComposerIsANoOp() {
        assertEquals(
            ComposerExpansion.Compact,
            collapsedComposerExpansion(ComposerExpansion.Compact),
        )
    }

    @Test
    fun dividerDragUsesOnePixelGranularity() {
        assertEquals(
            101f,
            draggedComposerPinnedHeightPx(
                startHeightPx = 100f,
                dragDeltaYPx = -1f,
                minHeightPx = 64f,
                maxHeightPx = 320f,
            ),
            0.001f,
        )
        assertEquals(
            99f,
            draggedComposerPinnedHeightPx(
                startHeightPx = 100f,
                dragDeltaYPx = 1f,
                minHeightPx = 64f,
                maxHeightPx = 320f,
            ),
            0.001f,
        )
    }

    @Test
    fun dividerDragClampsBetweenCompactAndFullScreenExtents() {
        assertEquals(
            64f,
            draggedComposerPinnedHeightPx(
                startHeightPx = 100f,
                dragDeltaYPx = 1000f,
                minHeightPx = 64f,
                maxHeightPx = 320f,
            ),
            0.001f,
        )
        assertEquals(
            320f,
            draggedComposerPinnedHeightPx(
                startHeightPx = 100f,
                dragDeltaYPx = -1000f,
                minHeightPx = 64f,
                maxHeightPx = 320f,
            ),
            0.001f,
        )
    }

    @Test
    fun pinnedHeightSelectsMatchingExpansionState() {
        assertEquals(
            ComposerExpansion.Compact,
            composerExpansionForPinnedHeight(heightPx = 64f, minHeightPx = 64f, maxHeightPx = 320f),
        )
        assertEquals(
            ComposerExpansion.Expanded,
            composerExpansionForPinnedHeight(heightPx = 120f, minHeightPx = 64f, maxHeightPx = 320f),
        )
        assertEquals(
            ComposerExpansion.FullScreen,
            composerExpansionForPinnedHeight(heightPx = 320f, minHeightPx = 64f, maxHeightPx = 320f),
        )
    }
}
