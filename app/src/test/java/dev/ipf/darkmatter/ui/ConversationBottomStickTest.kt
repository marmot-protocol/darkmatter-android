package dev.ipf.darkmatter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationBottomStickTest {
    @Test
    fun bottomGrowthDelta_returnsGrowthWhenLastItemExpandsAtBottom() {
        val before =
            BottomStickSnapshot(
                key = "last-message",
                index = 4,
                size = 96,
                atBottom = true,
                isLastTimelineItem = true,
            )
        val after = before.copy(size = 118, atBottom = false)

        assertEquals(22, bottomStickScrollDelta(before, after))
    }

    @Test
    fun bottomGrowthDelta_ignoresGrowthWhenReaderIsNotAtBottom() {
        val before =
            BottomStickSnapshot(
                key = "last-message",
                index = 4,
                size = 96,
                atBottom = false,
                isLastTimelineItem = true,
            )
        val after = before.copy(size = 118)

        assertNull(bottomStickScrollDelta(before, after))
    }

    @Test
    fun bottomGrowthDelta_ignoresAppendsAndNonLastItems() {
        val before =
            BottomStickSnapshot(
                key = "old-last",
                index = 4,
                size = 96,
                atBottom = true,
                isLastTimelineItem = true,
            )

        assertNull(
            bottomStickScrollDelta(
                before,
                before.copy(key = "new-last", index = 5, size = 118),
            ),
        )
        assertNull(
            bottomStickScrollDelta(
                before,
                before.copy(size = 118, isLastTimelineItem = false),
            ),
        )
    }
}
