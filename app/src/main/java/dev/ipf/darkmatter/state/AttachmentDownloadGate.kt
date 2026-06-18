package dev.ipf.darkmatter.state

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Bounded gate for decrypted attachment fetches.
 *
 * A single visible album can contain several images/videos, and a conversation
 * can render voice notes beside them. Serializing every miss behind one permit
 * makes perceived download latency roughly the sum of all visible media fetches.
 * Keep the cap small so Blossom / FFI work is still bounded, but let the first
 * screenful of media overlap network setup and decryption.
 */
internal class AttachmentDownloadGate(
    parallelism: Int = DEFAULT_PARALLELISM,
) {
    init {
        require(parallelism > 0) { "parallelism must be positive" }
    }

    private val semaphore = Semaphore(parallelism)

    suspend fun <T> withPermit(block: suspend () -> T): T = semaphore.withPermit { block() }

    internal companion object {
        const val DEFAULT_PARALLELISM = 3
    }
}
