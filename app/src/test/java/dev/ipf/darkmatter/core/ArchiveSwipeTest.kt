package dev.ipf.darkmatter.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the swipe-to-archive axis-lock math (#296). Mirrors
 * [ReplySwipeTest] — the gesture decision is a pure function so it can be
 * exercised without a Compose harness.
 *
 * Constants used here match the composable's tuning in `DarkMatterApp.kt`:
 * ratio = 2x, minLead = 24dp (≈63px at xxhdpi 2.625), slop = 8dp.
 */
class ArchiveSwipeTest {
    private val ratio = 2f
    private val minLeadPx = 63f
    private val slopPx = 21f

    @Test
    fun clearlyHorizontalDragEngagesSwipe() {
        // Mostly-sideways drag: 120px across, 10px down.
        assertTrue(ArchiveSwipe.shouldEngageSwipe(dx = 120f, dy = 10f, ratio = ratio, minLeadPx = minLeadPx))
    }

    @Test
    fun verticalDominantDragDoesNotEngageSwipe() {
        // A scroll with a small sideways component must NOT archive.
        assertFalse(ArchiveSwipe.shouldEngageSwipe(dx = 30f, dy = 120f, ratio = ratio, minLeadPx = minLeadPx))
        // Equal travel on both axes is ambiguous — refuse it.
        assertFalse(ArchiveSwipe.shouldEngageSwipe(dx = 80f, dy = 80f, ratio = ratio, minLeadPx = minLeadPx))
        // Diagonal that satisfies neither the ratio nor the lead.
        assertFalse(ArchiveSwipe.shouldEngageSwipe(dx = 60f, dy = 50f, ratio = ratio, minLeadPx = minLeadPx))
    }

    @Test
    fun ratioSatisfiedButLeadTooSmallDoesNotEngage() {
        // Near-origin: 4px vs 1px satisfies the 2x ratio but the absolute
        // lead (3px) is far below minLeadPx, so it's plainly jitter.
        assertFalse(ArchiveSwipe.shouldEngageSwipe(dx = 4f, dy = 1f, ratio = ratio, minLeadPx = minLeadPx))
    }

    @Test
    fun leadSatisfiedButRatioTooSmallDoesNotEngage() {
        // dx=140, dy=72: lead = 68 >= 63 (lead OK) but ratio fails
        // because 140 < 72*2 = 144. Both conditions are required.
        assertFalse(ArchiveSwipe.shouldEngageSwipe(dx = 140f, dy = 72f, ratio = ratio, minLeadPx = minLeadPx))
    }

    @Test
    fun bothRatioAndLeadSatisfiedEngages() {
        // dx=160, dy=40 → ratio 160 >= 80 ✓, lead 120 >= 63 ✓
        assertTrue(ArchiveSwipe.shouldEngageSwipe(dx = 160f, dy = 40f, ratio = ratio, minLeadPx = minLeadPx))
    }

    @Test
    fun engageIsDirectionAgnostic() {
        // RTL / leftward swipe (negative dx) decides identically — only
        // magnitude matters for the dominance test.
        assertTrue(ArchiveSwipe.shouldEngageSwipe(dx = -160f, dy = 40f, ratio = ratio, minLeadPx = minLeadPx))
        assertFalse(ArchiveSwipe.shouldEngageSwipe(dx = 30f, dy = -120f, ratio = ratio, minLeadPx = minLeadPx))
    }

    @Test
    fun axisDecidedOnlyAfterSlopCleared() {
        assertFalse(ArchiveSwipe.axisDecided(dx = 5f, dy = 5f, slopPx = slopPx))
        assertTrue(ArchiveSwipe.axisDecided(dx = 0f, dy = 30f, slopPx = slopPx))
        assertTrue(ArchiveSwipe.axisDecided(dx = 30f, dy = 0f, slopPx = slopPx))
    }
}
