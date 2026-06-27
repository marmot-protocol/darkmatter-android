package dev.ipf.whitenoise.android.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure selection + skew decisions behind the #745 background sweep.
 * The engine owns the authoritative prune; these guarantee the Android side
 * (1) never sweeps a group whose timer is off and (2) never treats a message
 * within the clock-skew window as already expired.
 */
class DisappearingMessageSweepTest {
    @Test
    fun skipsGroupsWithTimerOff() {
        // Retention 0 == disappearing messages off; the sweep must be a no-op.
        assertFalse(DisappearingMessageSweep.shouldSweepGroup(0uL))
    }

    @Test
    fun sweepsGroupsWithRetentionSet() {
        assertTrue(DisappearingMessageSweep.shouldSweepGroup(1uL))
        assertTrue(DisappearingMessageSweep.shouldSweepGroup(60uL))
        assertTrue(DisappearingMessageSweep.shouldSweepGroup(ULong.MAX_VALUE))
    }

    @Test
    fun expiryCutoffPullsBackBySkewTolerance() {
        val now = 1_000_000L
        assertEquals(
            now - DisappearingMessageSweep.CLOCK_SKEW_TOLERANCE_MS,
            DisappearingMessageSweep.expiryCutoffMillis(now),
        )
    }

    @Test
    fun expiryCutoffNeverGoesNegativeForAnEarlyClock() {
        // A clock reading below the skew margin must floor at zero rather than
        // produce a negative cutoff.
        assertEquals(0L, DisappearingMessageSweep.expiryCutoffMillis(0L))
        assertEquals(0L, DisappearingMessageSweep.expiryCutoffMillis(DisappearingMessageSweep.CLOCK_SKEW_TOLERANCE_MS - 1))
    }

    @Test
    fun skewToleranceIsSmallButNonZero() {
        // Coarse cadence, small tolerance: enough to absorb device-clock jitter
        // without meaningfully extending the retention window.
        assertTrue(DisappearingMessageSweep.CLOCK_SKEW_TOLERANCE_MS in 1L..60_000L)
    }
}
