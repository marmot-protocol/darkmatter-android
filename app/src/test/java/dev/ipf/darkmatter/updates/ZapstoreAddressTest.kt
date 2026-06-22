package dev.ipf.darkmatter.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZapstoreAddressTest {
    @Test
    fun parsesCurrentReleaseAddressForPinnedPublisherAndApp() {
        val address =
            ZapstoreAddress.parse(
                value = "30063:${ZapstoreReleaseClient.ZAPSTORE_PUBLISHER_PUBKEY}:org.parres.darkmatter@2026.6.20",
                publisherPubkey = ZapstoreReleaseClient.ZAPSTORE_PUBLISHER_PUBKEY,
                appId = "org.parres.darkmatter",
            )

        assertEquals(ZapstoreAddress(dTag = "org.parres.darkmatter@2026.6.20", version = "2026.6.20"), address)
    }

    @Test
    fun rejectsWrongPublisherOrAppAddress() {
        assertNull(
            ZapstoreAddress.parse(
                value = "30063:0000000000000000000000000000000000000000000000000000000000000000:org.parres.darkmatter@2026.6.20",
                publisherPubkey = ZapstoreReleaseClient.ZAPSTORE_PUBLISHER_PUBKEY,
                appId = "org.parres.darkmatter",
            ),
        )
        assertNull(
            ZapstoreAddress.parse(
                value = "30063:${ZapstoreReleaseClient.ZAPSTORE_PUBLISHER_PUBKEY}:org.parres.whitenoise@2026.6.20",
                publisherPubkey = ZapstoreReleaseClient.ZAPSTORE_PUBLISHER_PUBKEY,
                appId = "org.parres.darkmatter",
            ),
        )
    }

    @Test
    fun extractsVersionFromReleaseDTag() {
        assertEquals("2026.6.20", ZapstoreAddress.versionFromReleaseDTag("org.parres.darkmatter@2026.6.20", "org.parres.darkmatter"))
        assertNull(ZapstoreAddress.versionFromReleaseDTag("org.parres.whitenoise@2026.6.20", "org.parres.darkmatter"))
    }

    @Test
    fun selectsNewestReleaseEventEvenWhenRelayReturnsOlderFirst() {
        val older = zapstoreReleaseEvent(dTag = "org.parres.darkmatter@2026.6.20", createdAt = 10)
        val newer = zapstoreReleaseEvent(dTag = "org.parres.darkmatter@2026.6.20", createdAt = 20)

        assertEquals(
            newer,
            ZapstoreReleaseClient.latestVerifiedReleaseEvent(
                events = listOf(older, newer),
                publisherPubkey = ZapstoreReleaseClient.ZAPSTORE_PUBLISHER_PUBKEY,
                dTag = "org.parres.darkmatter@2026.6.20",
                verifies = { true },
            ),
        )
    }

    private fun zapstoreReleaseEvent(
        dTag: String,
        createdAt: Long,
    ): NostrEvent =
        NostrEvent(
            id = "0".repeat(64),
            pubkey = ZapstoreReleaseClient.ZAPSTORE_PUBLISHER_PUBKEY,
            createdAt = createdAt,
            kind = 30063,
            tags = listOf(listOf("d", dTag)),
            content = "",
            sig = "0".repeat(128),
        )
}
