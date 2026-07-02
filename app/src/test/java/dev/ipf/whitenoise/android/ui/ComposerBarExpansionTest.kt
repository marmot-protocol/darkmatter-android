package dev.ipf.whitenoise.android.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.ipf.whitenoise.android.R
import dev.ipf.whitenoise.android.core.MessageTextCopy
import dev.ipf.whitenoise.android.ui.theme.WhiteNoiseTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the composer's expand arrow through its three states (#324) and
 * pins the behaviors the state machine test cannot see: the arrow's
 * accessible label per state, the full-screen back affordance stepping back
 * to Expanded (not Compact), and the draft text surviving every switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComposerBarExpansionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val expandLabel = context.getString(R.string.composer_expand)
    private val fullScreenLabel = context.getString(R.string.composer_expand_full_screen)
    private val compactLabel = context.getString(R.string.composer_compact)
    private val exitFullScreenLabel = context.getString(R.string.composer_exit_full_screen)
    private val resizeHandleLabel = context.getString(R.string.composer_resize_handle)
    private val sendLabel = context.getString(R.string.send)
    private val composerBarTag = "composer-bar"

    private fun setComposer() {
        composeRule.setContent {
            WhiteNoiseTheme {
                ComposerBar(
                    replyingTo = null,
                    messageTextCopy = MessageTextCopy.Default,
                    onCancelReply = {},
                    onSend = { _, _ -> },
                )
            }
        }
    }

    private fun setBottomAnchoredComposer() {
        composeRule.setContent {
            WhiteNoiseTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    ComposerBar(
                        replyingTo = null,
                        messageTextCopy = MessageTextCopy.Default,
                        onCancelReply = {},
                        onSend = { _, _ -> },
                        modifier = Modifier.align(Alignment.BottomCenter).testTag(composerBarTag),
                    )
                }
            }
        }
    }

    @Test
    fun expandArrowCyclesThroughAllThreeStates() {
        setComposer()

        composeRule.onNodeWithContentDescription(expandLabel).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(expandLabel).performClick()
        composeRule.onNodeWithContentDescription(fullScreenLabel).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(fullScreenLabel).performClick()
        composeRule.onNodeWithContentDescription(compactLabel).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(compactLabel).performClick()
        composeRule.onNodeWithContentDescription(expandLabel).assertIsDisplayed()
    }

    @Test
    fun fullScreenShowsABackAffordanceThatReturnsToExpanded() {
        setComposer()

        composeRule.onNodeWithContentDescription(expandLabel).performClick()
        composeRule.onNodeWithContentDescription(fullScreenLabel).performClick()

        composeRule.onNodeWithContentDescription(exitFullScreenLabel).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(exitFullScreenLabel).performClick()

        // Expanded, not Compact: the arrow offers full screen again.
        composeRule.onNodeWithContentDescription(fullScreenLabel).assertIsDisplayed()
    }

    @Test
    fun backAffordanceIsAbsentOutsideFullScreen() {
        setComposer()

        composeRule.onNodeWithContentDescription(exitFullScreenLabel).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(expandLabel).performClick()
        composeRule.onNodeWithContentDescription(exitFullScreenLabel).assertDoesNotExist()
    }

    @Test
    fun resizeHandleIsDiscoverable() {
        setComposer()

        composeRule.onNodeWithContentDescription(resizeHandleLabel).assertIsDisplayed()
    }

    @Test
    fun draggedExpandedComposerKeepsSendAnchoredToBottom() {
        setBottomAnchoredComposer()

        val dragDistance = with(composeRule.density) { 220.dp.toPx() }
        composeRule.onNodeWithContentDescription(resizeHandleLabel).performTouchInput {
            down(center)
            moveBy(Offset(0f, -dragDistance))
            up()
        }
        composeRule.waitForIdle()

        val composerBottom =
            composeRule
                .onNodeWithTag(composerBarTag)
                .fetchSemanticsNode()
                .boundsInRoot
                .bottom
        val sendBottom =
            composeRule
                .onNodeWithContentDescription(sendLabel)
                .fetchSemanticsNode()
                .boundsInRoot
                .bottom
        val bottomGap = composerBottom - sendBottom
        val maxAnchoredGap = with(composeRule.density) { 16.dp.toPx() }

        assertTrue(
            "Send should stay anchored to the bottom of the pinned Expanded composer; gap was $bottomGap px",
            bottomGap in 0f..maxAnchoredGap,
        )
    }

    @Test
    fun draftTextSurvivesAFullExpansionRoundTrip() {
        setComposer()

        val draft = "a long draft that outgrows the compact composer"
        composeRule.onNode(hasSetTextAction()).performTextInput(draft)

        composeRule.onNodeWithContentDescription(expandLabel).performClick()
        composeRule.onNodeWithContentDescription(fullScreenLabel).performClick()
        composeRule.onNodeWithContentDescription(exitFullScreenLabel).performClick()
        composeRule.onNodeWithContentDescription(fullScreenLabel).performClick()
        composeRule.onNodeWithContentDescription(compactLabel).performClick()

        composeRule.onNode(hasSetTextAction()).assertTextEquals(draft)
    }
}
