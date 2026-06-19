package dev.ipf.darkmatter.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for issue #294: sending a message occasionally failed
 * with a "relays not connected"-style error even when relays were reachable a
 * moment later.
 *
 * Root cause: a publish that began during a *transient* relay-pool gap (socket
 * teardown mid-reconnect on a doze wake / network change) saw an empty or
 * still-handshaking pool at the single instant it fanned out, and the Nostr
 * transport returned a connect/publish timeout. `ConversationController.send()`
 * surfaced that first failure as a hard, user-visible "send failed" instead of
 * giving the pool a brief window to (re)connect and retrying.
 *
 * The fix retries the send across a bounded budget ([SEND_RETRY_ATTEMPTS]) but
 * ONLY for failures [isTransientRelaySendError] classifies as transient
 * connectivity — terminal/logic errors must still fail fast on the first
 * attempt, and a sustained outage (every retry exhausted) must still surface.
 * These tests pin the classifier so the retry can never start swallowing real
 * errors, nor stop covering the transient phrases the transport emits.
 */
class TransientRelaySendErrorTest {
    @Test
    fun connectRelayTimedOutIsTransient() {
        assertTrue(isTransientRelaySendError(RuntimeException("connect relay timed out")))
    }

    @Test
    fun sendEventTimedOutIsTransient() {
        assertTrue(isTransientRelaySendError(RuntimeException("send event timed out")))
    }

    @Test
    fun publishTimedOutWithZeroAcksIsTransient() {
        assertTrue(
            isTransientRelaySendError(
                RuntimeException("publish timed out after 30s: accepted 0 of required 1"),
            ),
        )
    }

    @Test
    fun relayDidNotAcknowledgeIsTransient() {
        assertTrue(
            isTransientRelaySendError(RuntimeException("relay did not acknowledge event")),
        )
    }

    @Test
    fun transportClosedVariantNameIsTransient() {
        // The UniFFI MarmotKitException.TransportClosed flattens to an empty
        // message; the variant's simpleName is what identifies it.
        class TransportClosed : Exception()
        assertTrue(isTransientRelaySendError(TransportClosed()))
    }

    @Test
    fun connectionRefusedAndResetAreTransient() {
        assertTrue(isTransientRelaySendError(RuntimeException("Connection refused")))
        assertTrue(isTransientRelaySendError(RuntimeException("connection reset by peer")))
    }

    @Test
    fun classifierWalksTheCauseChain() {
        val nested =
            RuntimeException(
                "publish failed",
                IllegalStateException("connect relay timed out"),
            )
        assertTrue(isTransientRelaySendError(nested))
    }

    @Test
    fun runtimeStoppingIsNotTransient() {
        // Runtime is shutting down (sign-out/teardown): retrying only delays a
        // send that can never land, so it must fail fast.
        class RuntimeStopping : Exception()
        assertFalse(isTransientRelaySendError(RuntimeStopping()))
    }

    @Test
    fun terminalLogicErrorsAreNotTransient() {
        // Unknown group / missing key package / invalid hex etc. are not
        // connectivity problems — retrying them is pointless and would delay a
        // legitimate failure toast.
        assertFalse(isTransientRelaySendError(RuntimeException("groupIdHex=abc123")))
        assertFalse(isTransientRelaySendError(IllegalArgumentException("details=bad input")))
        assertFalse(isTransientRelaySendError(RuntimeException("unexpected boom")))
    }

    @Test
    fun retryBudgetIsBoundedAndPositive() {
        // A misconfigured budget would either never retry (defeats the fix) or
        // retry unboundedly (hangs the send coroutine). Pin the invariant.
        assertTrue(SEND_RETRY_ATTEMPTS in 2..6)
        assertTrue(SEND_RETRY_BACKOFF_MS in 100L..3_000L)
    }
}
