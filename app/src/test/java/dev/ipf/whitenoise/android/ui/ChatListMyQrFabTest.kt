package dev.ipf.whitenoise.android.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.ipf.whitenoise.android.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatListMyQrFabTest {
    @get:Rule
    val composeRule = createComposeRule()

    // The chat-list QR shortcut (#819) is one tap: the FAB itself fires the
    // open callback directly — no menu, no intermediate screen — and stays
    // reachable to accessibility services via its content description.
    @Test
    fun myQrFabFiresOnASingleTapAndCarriesAContentDescription() {
        var taps = 0

        composeRule.setContent {
            ChatListMyQrFab(onClick = { taps++ })
        }

        val label = RuntimeEnvironment.getApplication().getString(R.string.my_qr_code)
        composeRule
            .onNodeWithContentDescription(label)
            .assertHasClickAction()
            .performClick()

        assertEquals(1, taps)
    }
}
