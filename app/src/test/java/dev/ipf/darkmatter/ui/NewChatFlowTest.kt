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
                selectedMembers = emptyList(),
                pendingRecipient = "",
                groupName = "Friends",
            ),
        )
        assertFalse(
            canSubmitNewChatSheet(
                directMessage = false,
                busy = false,
                selectedMembers = emptyList(),
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
                selectedMembers = listOf("npub1alice"),
                normalizedPendingRecipients = listOf("npub1bob"),
            ),
        )
    }

    @Test
    fun directMessageStillRequiresAndKeepsOneRecipient() {
        assertFalse(
            canSubmitNewChatSheet(
                directMessage = true,
                busy = false,
                selectedMembers = emptyList(),
                pendingRecipient = "",
                groupName = "",
            ),
        )
        assertTrue(
            canSubmitNewChatSheet(
                directMessage = true,
                busy = false,
                selectedMembers = emptyList(),
                pendingRecipient = "npub1alice",
                groupName = "",
            ),
        )
        assertEquals(
            listOf("npub1alice"),
            newChatMemberRefs(
                directMessage = true,
                selectedMembers = listOf("npub1alice"),
                normalizedPendingRecipients = listOf("npub1bob"),
            ),
        )
    }
}
