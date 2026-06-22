package dev.ipf.darkmatter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewChatFlowTest {
    @Test
    fun groupCreateRequiresNameButNoRecipients() {
        assertTrue(
            canSubmitNewChatSheet(
                directMessage = false,
                busy = false,
                pendingRecipient = "",
                groupName = "Friends",
            ),
        )
        assertFalse(
            canSubmitNewChatSheet(
                directMessage = false,
                busy = false,
                pendingRecipient = "npub1alice",
                groupName = "",
            ),
        )
    }

    @Test
    fun groupCreateStartsWithNoInvitedMembers() {
        assertEquals(
            emptyList<String>(),
            newChatMemberRefs(
                directMessage = false,
                normalizedPendingRecipients = listOf("npub1alice"),
            ),
        )
    }

    @Test
    fun directMessageStillRequiresAndKeepsOneRecipient() {
        assertFalse(
            canSubmitNewChatSheet(
                directMessage = true,
                busy = false,
                pendingRecipient = "",
                groupName = "",
            ),
        )
        assertTrue(
            canSubmitNewChatSheet(
                directMessage = true,
                busy = false,
                pendingRecipient = "npub1alice",
                groupName = "",
            ),
        )
        assertEquals(
            listOf("npub1alice"),
            newChatMemberRefs(
                directMessage = true,
                normalizedPendingRecipients = listOf("npub1alice", "npub1bob", "npub1alice"),
            ),
        )
    }

    @Test
    fun recipientPreviewMapsResolutionSignalsToState() {
        // Empty input -> no card.
        assertEquals(
            RecipientPreviewState.Empty,
            recipientPreviewState(hasInput = false, resolving = false, resolvedHex = null, hasProfile = false),
        )
        // Resolving wins over a not-yet-known key (NIP-05 lookup / kind:0 fetch).
        assertEquals(
            RecipientPreviewState.Resolving,
            recipientPreviewState(hasInput = true, resolving = true, resolvedHex = null, hasProfile = false),
        )
        // Settled with no key -> invalid.
        assertEquals(
            RecipientPreviewState.Invalid,
            recipientPreviewState(hasInput = true, resolving = false, resolvedHex = null, hasProfile = false),
        )
        // Resolved with metadata -> full card.
        assertEquals(
            RecipientPreviewState.Loaded,
            recipientPreviewState(hasInput = true, resolving = false, resolvedHex = "deadbeef", hasProfile = true),
        )
        // Resolved but no metadata -> fallback card.
        assertEquals(
            RecipientPreviewState.NoProfile,
            recipientPreviewState(hasInput = true, resolving = false, resolvedHex = "deadbeef", hasProfile = false),
        )
    }

    @Test
    fun recipientPreviewGatesSubmitOnlyForResolvingOrInvalid() {
        // Loaded and no-profile both confirm a real key -> action allowed.
        assertTrue(recipientPreviewAllowsSubmit(RecipientPreviewState.Loaded))
        assertTrue(recipientPreviewAllowsSubmit(RecipientPreviewState.NoProfile))
        // Empty defers to the surface's own validation (e.g. group create with
        // no recipient field) -> not blocked here.
        assertTrue(recipientPreviewAllowsSubmit(RecipientPreviewState.Empty))
        // In-flight / unresolvable identifiers block the action.
        assertFalse(recipientPreviewAllowsSubmit(RecipientPreviewState.Resolving))
        assertFalse(recipientPreviewAllowsSubmit(RecipientPreviewState.Invalid))
    }

    @Test
    fun emptyGroupInviteCtaRequiresLoadedAdminSelfOnlyGroup() {
        assertTrue(
            canInviteFromEmptyGroup(
                isSelfMember = true,
                isSelfAdmin = true,
                membersLoaded = true,
                memberCount = 1,
            ),
        )
        assertFalse(
            canInviteFromEmptyGroup(
                isSelfMember = true,
                isSelfAdmin = true,
                membersLoaded = true,
                memberCount = 0,
            ),
        )
        assertFalse(
            canInviteFromEmptyGroup(
                isSelfMember = true,
                isSelfAdmin = true,
                membersLoaded = true,
                memberCount = 2,
            ),
        )
        assertFalse(
            canInviteFromEmptyGroup(
                isSelfMember = true,
                isSelfAdmin = false,
                membersLoaded = true,
                memberCount = 1,
            ),
        )
        assertFalse(
            canInviteFromEmptyGroup(
                isSelfMember = false,
                isSelfAdmin = true,
                membersLoaded = true,
                memberCount = 1,
            ),
        )
        assertFalse(
            canInviteFromEmptyGroup(
                isSelfMember = true,
                isSelfAdmin = true,
                membersLoaded = false,
                memberCount = 1,
            ),
        )
    }
}
