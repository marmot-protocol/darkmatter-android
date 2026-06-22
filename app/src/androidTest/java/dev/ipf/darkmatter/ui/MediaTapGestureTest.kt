package dev.ipf.darkmatter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MediaTapGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tapInvokesMediaOpenWithoutActionMenu() {
        var taps = 0
        var longPresses = 0

        composeRule.setContent {
            Box(
                Modifier
                    .size(96.dp)
                    .testTag(MEDIA_TILE_TAG)
                    .mediaTapOrActionLongPress(
                        gestureKey = "tap-test",
                        onTap = { taps++ },
                        onLongPressWindowY = { longPresses++ },
                    ),
            )
        }

        composeRule.onNodeWithTag(MEDIA_TILE_TAG).performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(1, taps)
            assertEquals(0, longPresses)
        }
    }

    @Test
    fun longPressInvokesActionMenuWithoutMediaOpen() {
        var taps = 0
        var longPresses = 0

        composeRule.setContent {
            Box(
                Modifier
                    .size(96.dp)
                    .testTag(MEDIA_TILE_TAG)
                    .mediaTapOrActionLongPress(
                        gestureKey = "long-press-test",
                        onTap = { taps++ },
                        onLongPressWindowY = { longPresses++ },
                    ),
            )
        }

        composeRule.onNodeWithTag(MEDIA_TILE_TAG).performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(0, taps)
            assertEquals(1, longPresses)
        }
    }

    private companion object {
        const val MEDIA_TILE_TAG = "media-tile"
    }
}
