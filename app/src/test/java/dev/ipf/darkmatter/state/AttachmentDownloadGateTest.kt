package dev.ipf.darkmatter.state

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

class AttachmentDownloadGateTest {
    @Test
    fun smallAlbumDownloadsCanStartTogether() {
        runBlocking {
            val gate = AttachmentDownloadGate(parallelism = 3)
            val active = AtomicInteger(0)
            val maxActive = AtomicInteger(0)
            val allStarted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()

            val jobs =
                List(3) {
                    async(Dispatchers.Default) {
                        gate.withPermit {
                            val now = active.incrementAndGet()
                            maxActive.updateAndGet { previous -> max(previous, now) }
                            if (now == 3) allStarted.complete(Unit)
                            withTimeout(1_000) { release.await() }
                            active.decrementAndGet()
                        }
                    }
                }

            withTimeout(1_000) { allStarted.await() }
            assertEquals(3, maxActive.get())
            release.complete(Unit)
            jobs.awaitAll()
        }
    }

    @Test
    fun configuredLimitStillBoundsExcessDownloads() {
        runBlocking {
            val gate = AttachmentDownloadGate(parallelism = 2)
            val active = AtomicInteger(0)
            val started = AtomicInteger(0)
            val maxActive = AtomicInteger(0)
            val firstTwoStarted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()

            val jobs =
                List(3) {
                    async(Dispatchers.Default) {
                        gate.withPermit {
                            val now = active.incrementAndGet()
                            val totalStarted = started.incrementAndGet()
                            maxActive.updateAndGet { previous -> max(previous, now) }
                            if (totalStarted == 2) firstTwoStarted.complete(Unit)
                            try {
                                withTimeout(1_000) { release.await() }
                            } finally {
                                active.decrementAndGet()
                            }
                        }
                    }
                }

            withTimeout(1_000) { firstTwoStarted.await() }
            assertEquals(2, started.get())
            assertEquals(2, maxActive.get())
            release.complete(Unit)
            jobs.awaitAll()
            assertEquals(3, started.get())
        }
    }
}
