package dev.ipf.whitenoise.android.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.ipf.whitenoise.android.R
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

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val expandLabel = context.getString(R.string.composer_expand)
    private val fullScreenLabel = context.getString(R.string.composer_expand_full_screen)
    private val compactLabel = context.getString(R.string.composer_compact)
    private val exitFullScreenLabel = context.getString(R.string.composer_exit_full_screen)
    private val resizeHandleLabel = context.getString(R.string.composer_resize_handle)

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
