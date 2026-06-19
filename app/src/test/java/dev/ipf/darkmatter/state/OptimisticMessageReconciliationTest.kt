package dev.ipf.darkmatter.state

import dev.ipf.marmotkit.AppMessageRecordFfi
import dev.ipf.marmotkit.MarkdownDocumentFfi
import dev.ipf.marmotkit.MessageTagFfi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimisticMessageReconciliationTest {
    @Test
    fun matchingPendingMessageIsReconciledWhenProjectionArrives() {
        val pending = timelineMessage("temp", MessageStatus.Pending)

        assertEquals(
            "temp",
            optimisticMessageIdForProjection(listOf(pending), message("confirmed")),
        )
    }

    @Test
    fun sentOptimisticMessageIsReconciledWhenProjectionArrivesAfterResponse() {
        val sent = timelineMessage("sent", MessageStatus.Sent)

        assertEquals(
            "sent",
            optimisticMessageIdForProjection(listOf(sent), message("confirmed")),
        )
    }

    @Test
    fun sentOptimisticReplacementIsSkippedWhenProjectionArrivesBeforeResponse() {
        assertEquals(
            false,
            shouldInsertSentOptimisticMessage("confirmed", setOf("confirmed")),
        )
        assertEquals(
            true,
            shouldInsertSentOptimisticMessage("confirmed", emptySet()),
        )
    }

    @Test
    fun queuedPendingMessagesReconcileOnlyTheMatchingProjection() {
        val first = timelineMessage("first-temp", MessageStatus.Pending, plaintext = "first")
        val second = timelineMessage("second-temp", MessageStatus.Pending, plaintext = "second")

        assertEquals(
            "first-temp",
            optimisticMessageIdForProjection(
                listOf(first, second),
                message("first-confirmed", plaintext = "first"),
            ),
        )
    }

    @Test
    fun delayedQueuedProjectionCanReconcileAfterWorkerWait() {
        val pending = timelineMessage("temp", MessageStatus.Pending)

        assertEquals(
            "temp",
            optimisticMessageIdForProjection(
                listOf(pending),
                message("confirmed", recordedAt = 10uL),
                allowDelayedProjection = true,
            ),
        )
    }

    @Test
    fun delayedHistoricalSnapshotDoesNotConsumeNewerPendingMessage() {
        val pending = timelineMessage("temp", MessageStatus.Pending, recordedAt = 10uL)

        assertNull(
            optimisticMessageIdForProjection(
                listOf(pending),
                message("historical", recordedAt = 1uL),
                allowDelayedProjection = true,
            ),
        )
    }

    @Test
    fun failedAndDifferentPendingMessagesAreNotReconciled() {
        val failed = timelineMessage("failed", MessageStatus.Failed)
        val different = timelineMessage("different", MessageStatus.Pending, plaintext = "another")

        assertNull(
            optimisticMessageIdForProjection(
                listOf(failed, different),
                message("confirmed"),
            ),
        )
    }

    @Test
    fun historicalMatchingMessageIsNotReconciled() {
        val pending = timelineMessage("temp", MessageStatus.Pending)

        assertNull(
            optimisticMessageIdForProjection(
                listOf(pending),
                message("historical", recordedAt = 10uL),
            ),
        )
    }

    @Test
    fun receivedMatchingMessageIsNotReconciled() {
        val pending = timelineMessage("temp", MessageStatus.Pending)

        assertNull(
            optimisticMessageIdForProjection(
                listOf(pending),
                message("received", direction = "received"),
            ),
        )
    }

    @Test
    fun multiMediaSendReconcilesByBridgeIdNotBySiblingHeuristic() {
        // Reproduction for a multi-document send where 3 docs are queued in
        // rapid succession (same direction/sender/kind/recordedAt) and each
        // optimistic carries a `_media_pending` shape. After the FIRST upload
        // confirms, performMediaUpload inserts a "bridge" optimistic keyed at
        // the confirmed event id. The relay then echoes back the kind-9
        // projection. The reconciler MUST return that bridge — not a sibling
        // pending — otherwise the wrong sibling gets removed and the user
        // sees pending bubbles vanish until each upload confirms in turn.
        val pendingB = mediaPending("temp-b", filename = "b.pdf")
        val pendingC = mediaPending("temp-c", filename = "c.pdf")
        val bridgeA = mediaSent("confirmed-a", filename = "a.pdf")
        val projection = mediaProjection("confirmed-a")

        // Bridge is inserted LAST (after the siblings were already pending),
        // so insertion-order iteration would otherwise hit pendingB first.
        assertEquals(
            "confirmed-a",
            optimisticMessageIdForProjection(
                listOf(pendingB, pendingC, bridgeA),
                projection,
            ),
        )
    }

    @Test
    fun queuedMessagesKeepTheirOrderWhenIdsChangeDuringConfirmation() {
        val first = timelineMessage("first-temp", MessageStatus.Pending, plaintext = "A", timelineOrder = 1uL)
        val second = timelineMessage("second-temp", MessageStatus.Pending, plaintext = "B", timelineOrder = 2uL)
        val third = timelineMessage("third-temp", MessageStatus.Pending, plaintext = "C", timelineOrder = 3uL)
        val confirmedFirst = timelineMessage("zz-confirmed", MessageStatus.Sent, plaintext = "A", timelineOrder = 1uL)

        assertEquals(
            listOf("A", "B", "C"),
            listOf(second, third, confirmedFirst)
                .sortedWith(::compareTimelineMessages)
                .map { it.record.plaintext },
        )
    }

    @Test
    fun successfulTextRetryClearsTheFailedRow() {
        val failed = timelineMessage("failed", MessageStatus.Failed, plaintext = "hello")

        assertEquals(
            listOf("msg:failed"),
            failedOptimisticKeysForConfirmation(listOf(failed), message("confirmed", plaintext = "hello")),
        )
    }

    @Test
    fun confirmationDoesNotClearAFailedMessageWithDifferentBody() {
        val failed = timelineMessage("failed", MessageStatus.Failed, plaintext = "hello")

        assertEquals(
            emptyList<String>(),
            failedOptimisticKeysForConfirmation(listOf(failed), message("confirmed", plaintext = "different")),
        )
    }

    @Test
    fun confirmationDoesNotClearAFailedMessageWithDifferentReplyTarget() {
        val failed =
            timelineMessage("failed", MessageStatus.Failed, plaintext = "hello", tags = replyTags("target-a"))

        assertEquals(
            emptyList<String>(),
            failedOptimisticKeysForConfirmation(
                listOf(failed),
                message("confirmed", plaintext = "hello", tags = replyTags("target-b")),
            ),
        )
    }

    @Test
    fun matchingReplyTargetFailedRowIsCleared() {
        val failed =
            timelineMessage("failed", MessageStatus.Failed, plaintext = "hello", tags = replyTags("target-a"))

        assertEquals(
            listOf("msg:failed"),
            failedOptimisticKeysForConfirmation(
                listOf(failed),
                message("confirmed", plaintext = "hello", tags = replyTags("target-a")),
            ),
        )
    }

    @Test
    fun onlyFailedRowsAreClearedNotPendingOrSent() {
        val pending = timelineMessage("pending", MessageStatus.Pending, plaintext = "hello")
        val sent = timelineMessage("sent", MessageStatus.Sent, plaintext = "hello")

        assertEquals(
            emptyList<String>(),
            failedOptimisticKeysForConfirmation(listOf(pending, sent), message("confirmed", plaintext = "hello")),
        )
    }

    @Test
    fun projectionArrivalClearsAMatchingFailedRow() {
        // The original send failed, but the relay delivered it after all. The
        // confirmed projection carries a brand-new event id, so the by-id
        // fast-path can't pair it — the conservative content match must.
        val failed = timelineMessage("failed", MessageStatus.Failed, plaintext = "hello")

        assertEquals(
            listOf("msg:failed"),
            failedOptimisticKeysForConfirmation(
                listOf(failed),
                message("late-confirmed", plaintext = "hello", recordedAt = 99uL),
            ),
        )
    }

    @Test
    fun twoIdenticalFailedTextRowsAreLeftAloneOnAmbiguity() {
        val first = timelineMessage("failed-1", MessageStatus.Failed, plaintext = "hello", recordedAt = 1uL)
        val second = timelineMessage("failed-2", MessageStatus.Failed, plaintext = "hello", recordedAt = 2uL)

        assertEquals(
            emptyList<String>(),
            failedOptimisticKeysForConfirmation(listOf(first, second), message("confirmed", plaintext = "hello")),
        )
    }

    @Test
    fun distinctFailedMessagesRemainIndependentWhenOneIsConfirmed() {
        val failedA = timelineMessage("failed-a", MessageStatus.Failed, plaintext = "first")
        val failedB = timelineMessage("failed-b", MessageStatus.Failed, plaintext = "second")

        val cleared =
            failedOptimisticKeysForConfirmation(listOf(failedA, failedB), message("confirmed", plaintext = "first"))

        assertEquals(listOf("msg:failed-a"), cleared)
        assertTrue("msg:failed-b" !in cleared)
    }

    @Test
    fun recordedAtFilterRestrictsCleanupToTheOriginalTimestamp() {
        val failed = timelineMessage("failed", MessageStatus.Failed, plaintext = "hello", recordedAt = 5uL)
        val confirmed = message("confirmed", plaintext = "hello", recordedAt = 9uL)

        assertEquals(
            listOf("msg:failed"),
            failedOptimisticKeysForConfirmation(listOf(failed), confirmed, requireRecordedAt = 5uL),
        )
        assertEquals(
            emptyList<String>(),
            failedOptimisticKeysForConfirmation(listOf(failed), confirmed, requireRecordedAt = 9uL),
        )
    }

    @Test
    fun failedMediaRowIsClearedByItsMediaProjection() {
        val failed = mediaPending("failed-media", filename = "a.pdf", status = MessageStatus.Failed)

        assertEquals(
            listOf("msg:failed-media"),
            failedOptimisticKeysForConfirmation(listOf(failed), mediaProjection("confirmed-a")),
        )
    }

    @Test
    fun distinctFailedMediaSendsRemainIndependentWhenOneIsConfirmed() {
        val failedA = mediaPending("failed-a", filename = "a.pdf", status = MessageStatus.Failed)
        val failedB = mediaPending("failed-b", filename = "b.pdf", status = MessageStatus.Failed)

        // Filenames survive into the imeta projection, so confirming a.pdf clears
        // only that bubble — b.pdf keeps its retry affordance.
        assertEquals(
            listOf("msg:failed-a"),
            failedOptimisticKeysForConfirmation(listOf(failedA, failedB), mediaProjection("confirmed-a", "a.pdf")),
        )
    }

    @Test
    fun failedMediaRowIsNotClearedByADifferentFilesProjection() {
        val failed = mediaPending("failed-media", filename = "a.pdf", status = MessageStatus.Failed)

        assertEquals(
            emptyList<String>(),
            failedOptimisticKeysForConfirmation(listOf(failed), mediaProjection("confirmed-b", "b.pdf")),
        )
    }

    @Test
    fun failedMediaRowIsNotClearedWhenAttachmentCountDiffers() {
        val failed = mediaPendingMulti("failed-album", filenames = listOf("a.pdf", "b.pdf"))

        assertEquals(
            emptyList<String>(),
            failedOptimisticKeysForConfirmation(listOf(failed), mediaProjection("confirmed-a", "a.pdf")),
        )
    }

    @Test
    fun twoFailedMediaSendsOfTheSameFileAreLeftAloneOnAmbiguity() {
        val first = mediaPending("failed-1", filename = "a.pdf", status = MessageStatus.Failed)
        val second = mediaPending("failed-2", filename = "a.pdf", status = MessageStatus.Failed)

        assertEquals(
            emptyList<String>(),
            failedOptimisticKeysForConfirmation(listOf(first, second), mediaProjection("confirmed-a", "a.pdf")),
        )
    }

    @Test
    fun mediaCleanupIsDeferredWhilePendingMediaIsStillInFlight() {
        // A media projection during a multi-media send belongs to one of the
        // pending uploads (the bridge insert resolves it); an unrelated failed
        // media row must not be swept up in that window.
        val pending = mediaPending("pending-media", filename = "p.pdf")
        val failed = mediaPending("failed-media", filename = "f.pdf", status = MessageStatus.Failed)

        assertEquals(
            emptyList<String>(),
            failedOptimisticKeysForConfirmation(listOf(pending, failed), mediaProjection("confirmed-a")),
        )
    }

    private fun mediaPending(
        id: String,
        filename: String,
        status: MessageStatus = MessageStatus.Pending,
    ): TimelineMessage =
        TimelineMessage(
            id = "msg:$id",
            record =
                AppMessageRecordFfi(
                    messageIdHex = id,
                    direction = "sent",
                    groupIdHex = "group",
                    sender = "alice",
                    plaintext = "📎 $filename",
                    contentTokens = MarkdownDocumentFfi(blocks = emptyList()),
                    kind = 9uL,
                    tags =
                        listOf(
                            MessageTagFfi(
                                listOf("_media_pending", filename, "application/pdf"),
                            ),
                        ),
                    recordedAt = 1uL,
                    receivedAt = 1uL,
                ),
            status = status,
            timelineOrder = 1uL,
        )

    private fun mediaSent(
        id: String,
        filename: String,
    ): TimelineMessage =
        TimelineMessage(
            id = "msg:$id",
            record =
                AppMessageRecordFfi(
                    messageIdHex = id,
                    direction = "sent",
                    groupIdHex = "group",
                    sender = "alice",
                    plaintext = "",
                    contentTokens = MarkdownDocumentFfi(blocks = emptyList()),
                    kind = 9uL,
                    tags =
                        listOf(
                            dev.ipf.marmotkit.MessageTagFfi(
                                listOf("imeta", "url https://example/$filename", "m application/pdf"),
                            ),
                        ),
                    recordedAt = 1uL,
                    receivedAt = 1uL,
                ),
            status = MessageStatus.Sent,
            timelineOrder = 1uL,
        )

    private fun mediaProjection(
        id: String,
        vararg filenames: String,
    ): AppMessageRecordFfi {
        val names = if (filenames.isEmpty()) arrayOf("a.pdf") else filenames
        return AppMessageRecordFfi(
            messageIdHex = id,
            direction = "sent",
            groupIdHex = "group",
            sender = "alice",
            plaintext = "",
            contentTokens = MarkdownDocumentFfi(blocks = emptyList()),
            kind = 9uL,
            tags = names.map(::validImetaTag),
            recordedAt = 1uL,
            receivedAt = 1uL,
        )
    }

    // A fully-formed encrypted-media-v1 imeta tag — MediaReferenceParser rejects
    // anything missing the required fields, so the matcher only sees real albums.
    private fun validImetaTag(
        filename: String,
        mediaType: String = "application/pdf",
    ): MessageTagFfi =
        MessageTagFfi(
            listOf(
                "imeta",
                "v encrypted-media-v1",
                "locator blossom-v1 https://example.com/$filename",
                "ciphertext_sha256 ${"a".repeat(64)}",
                "plaintext_sha256 ${"b".repeat(64)}",
                "nonce ${"c".repeat(24)}",
                "m $mediaType",
                "filename $filename",
            ),
        )

    private fun mediaPendingMulti(
        id: String,
        filenames: List<String>,
        status: MessageStatus = MessageStatus.Failed,
    ): TimelineMessage =
        TimelineMessage(
            id = "msg:$id",
            record =
                AppMessageRecordFfi(
                    messageIdHex = id,
                    direction = "sent",
                    groupIdHex = "group",
                    sender = "alice",
                    plaintext = "📎 ${filenames.size} attachments",
                    contentTokens = MarkdownDocumentFfi(blocks = emptyList()),
                    kind = 9uL,
                    tags = filenames.map { MessageTagFfi(listOf("_media_pending", it, "application/pdf")) },
                    recordedAt = 1uL,
                    receivedAt = 1uL,
                ),
            status = status,
            timelineOrder = 1uL,
        )

    private fun timelineMessage(
        id: String,
        status: MessageStatus,
        plaintext: String = "hello",
        timelineOrder: ULong = 0uL,
        recordedAt: ULong = 1uL,
        tags: List<MessageTagFfi> = emptyList(),
    ): TimelineMessage =
        TimelineMessage(
            id = "msg:$id",
            record = message(id, plaintext, recordedAt, tags = tags),
            status = status,
            timelineOrder = timelineOrder,
        )

    private fun message(
        id: String,
        plaintext: String = "hello",
        recordedAt: ULong = 1uL,
        direction: String = "sent",
        tags: List<MessageTagFfi> = emptyList(),
    ): AppMessageRecordFfi =
        AppMessageRecordFfi(
            messageIdHex = id,
            direction = direction,
            groupIdHex = "group",
            sender = "alice",
            plaintext = plaintext,
            contentTokens = MarkdownDocumentFfi(blocks = emptyList()),
            kind = 9uL,
            tags = tags,
            recordedAt = recordedAt,
            receivedAt = recordedAt,
        )

    private fun replyTags(target: String): List<MessageTagFfi> =
        listOf(
            MessageTagFfi(listOf("e", target)),
            MessageTagFfi(listOf("q", target)),
        )
}
