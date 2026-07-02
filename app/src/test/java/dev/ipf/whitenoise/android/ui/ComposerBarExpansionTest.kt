package dev.ipf.whitenoise.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.ipf.whitenoise.android.core.MessageTextCopy
import dev.ipf.whitenoise.android.ui.theme.WhiteNoiseTheme
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

    private val expandLabel = "Expand message field"
    private val fullScreenLabel = "Open full-screen editor"
    private val collapseLabel = "Collapse message field"
    private val exitFullScreenLabel = "Exit full-screen editor"

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

    @Test
    fun expandArrowCyclesThroughAllThreeStates() {
        setComposer()

        composeRule.onNodeWithContentDescription(expandLabel).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(expandLabel).performClick()
        composeRule.onNodeWithContentDescription(fullScreenLabel).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(fullScreenLabel).performClick()
        composeRule.onNodeWithContentDescription(collapseLabel).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(collapseLabel).performClick()
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
    fun draftTextSurvivesAFullExpansionRoundTrip() {
        setComposer()

        val draft = "a long draft that outgrows the compact composer"
        composeRule.onNode(hasSetTextAction()).performTextInput(draft)

        composeRule.onNodeWithContentDescription(expandLabel).performClick()
        composeRule.onNodeWithContentDescription(fullScreenLabel).performClick()
        composeRule.onNodeWithContentDescription(exitFullScreenLabel).performClick()
        composeRule.onNodeWithContentDescription(fullScreenLabel).performClick()
        composeRule.onNodeWithContentDescription(collapseLabel).performClick()

        composeRule.onNode(hasSetTextAction()).assertTextEquals(draft)
    }
}
