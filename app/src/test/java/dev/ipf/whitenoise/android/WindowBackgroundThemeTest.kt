package dev.ipf.whitenoise.android

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WindowBackgroundThemeTest {
    @Test
    fun launcherThemeDrawsBlackBeforeFirstComposeFrame() {
        val context =
            RuntimeEnvironment.getApplication().apply {
                setTheme(R.style.Theme_WhiteNoise)
            }
        val attrs =
            intArrayOf(
                android.R.attr.windowBackground,
                android.R.attr.colorBackground,
                android.R.attr.windowSplashScreenBackground,
            )

        val values = context.theme.obtainStyledAttributes(attrs)
        try {
            val windowBackground = values.getDrawable(0)
            assertTrue(windowBackground is ColorDrawable)
            assertEquals(Color.BLACK, (windowBackground as ColorDrawable).color)
            assertEquals(Color.BLACK, values.getColor(1, Color.TRANSPARENT))
            assertEquals(Color.BLACK, values.getColor(2, Color.TRANSPARENT))
        } finally {
            values.recycle()
        }
    }
}
