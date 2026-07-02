package dev.ipf.whitenoise.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.TextUnit
import dev.ipf.whitenoise.android.state.FontScale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the in-app font-size scale (#403) actually reaches typography: the
 * theme multiplies every style's `fontSize` by the selected [FontScale] factor
 * and exposes the raw factor through [LocalFontScale]. The sizes stay in `sp`,
 * so the OS font-size setting still applies on top (system_scale × app_scale).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FontScaleThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultScaleLeavesTypographyUntouched() {
        var baseline: TextUnit? = null
        var scaled: TextUnit? = null

        composeRule.setContent {
            WhiteNoiseTheme(fontScale = FontScale.Default) {
                val size = MaterialTheme.typography.bodyLarge.fontSize
                SideEffect { scaled = size }
            }
            // A bare MaterialTheme with the app's own Typography is the baseline.
            MaterialTheme(typography = Typography) {
                val size = MaterialTheme.typography.bodyLarge.fontSize
                SideEffect { baseline = size }
            }
        }

        composeRule.runOnIdle {
            assertEquals(requireNotNull(baseline).value, requireNotNull(scaled).value, 0.001f)
        }
    }

    @Test
    fun eachStepScalesBodyTextByItsFactor() {
        val bodySizes = mutableMapOf<FontScale, Float>()

        composeRule.setContent {
            FontScale.entries.forEach { scale ->
                WhiteNoiseTheme(fontScale = scale) {
                    val size = MaterialTheme.typography.bodyLarge.fontSize.value
                    SideEffect { bodySizes[scale] = size }
                }
            }
        }

        composeRule.runOnIdle {
            val default = requireNotNull(bodySizes[FontScale.Default])
            FontScale.entries.forEach { scale ->
                val expected = default * scale.scale
                assertEquals(expected, requireNotNull(bodySizes[scale]), 0.01f)
            }
        }
    }

    @Test
    fun localFontScaleExposesTheSelectedStep() {
        var provided: FontScale? = null

        composeRule.setContent {
            WhiteNoiseTheme(fontScale = FontScale.Large) {
                val local = LocalFontScale.current
                SideEffect { provided = local }
            }
        }

        composeRule.runOnIdle {
            assertEquals(FontScale.Large, provided)
        }
    }
}
