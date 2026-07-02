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
}
