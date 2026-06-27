package dev.ipf.whitenoise.android.state

/**
 * Pure selection + skew helpers for the disappearing-message background sweep
 * (#745). The actual prune/secure-delete is owned by the engine
 * (`secureDeleteExpired`); these side-effect-free helpers pin the two decisions
 * the Android side makes so they can be unit-tested without an engine, an
 * Android context, or WorkManager:
 *
 *  - which groups the sweep should touch (only those with a retention window
 *    set — a group with the timer off is a no-op), and
 *  - the device-clock cutoff (with a small skew tolerance) the sweep treats as
 *    "now" when reasoning about expiry, so a fast/slow device clock doesn't
 *    prune a message a hair early.
 *
 * Keeping these out of the worker mirrors the `decideForegroundStart` pattern
 * (see [dev.ipf.whitenoise.android.notifications.decideForegroundStart]).
 */
object DisappearingMessageSweep {
    /**
     * Skew tolerance applied to the device clock when deciding what counts as
     * "expired". The engine owns the authoritative prune, but a coarse sweep
     * should never be more eager than a device whose clock runs fast: subtract
     * this margin so a message within the skew window of its expiry survives to
     * the next sweep rather than vanishing early. Mirrors the coarse-cadence
     * intent of the in-conversation sweep.
     */
    const val CLOCK_SKEW_TOLERANCE_MS: Long = 5_000L

    /**
     * Whether a group with the given retention should be swept. `0` means the
     * disappearing-messages timer is off for that group, so the sweep must be a
     * no-op for it (acceptance criterion). Matches the in-conversation guard
     * `group.disappearingMessageSecs > 0uL`.
     */
    fun shouldSweepGroup(disappearingMessageSecs: ULong): Boolean = disappearingMessageSecs > 0uL

    /**
     * The device-clock instant the sweep should treat as "now" when reasoning
     * about expiry, pulled back by [CLOCK_SKEW_TOLERANCE_MS] and floored at zero
     * so an absurdly early clock can't produce a negative cutoff.
     */
    fun expiryCutoffMillis(nowMillis: Long): Long = (nowMillis - CLOCK_SKEW_TOLERANCE_MS).coerceAtLeast(0L)
}
