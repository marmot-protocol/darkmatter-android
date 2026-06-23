package dev.ipf.darkmatter.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmoledSurfaceThemeTest {
    @Test
    fun detectsAmoledOnlyWhenSurfaceTokensAreBlackAndUntinted() {
        val amoled =
            darkColorScheme(
                surface = Color.Black,
                surfaceVariant = Color.Black,
                surfaceContainer = Color.Black,
                surfaceContainerHigh = Color.Black,
                surfaceContainerHighest = Color.Black,
                surfaceTint = Color.Transparent,
            )
        val nearBlack = amoled.copy(surfaceVariant = Color(0xFF010101))
        val tinted = amoled.copy(surfaceTint = Color(0xFF06B6D4))

        assertTrue(amoled.isAmoledSurfaceTheme())
        assertFalse(nearBlack.isAmoledSurfaceTheme())
        assertFalse(tinted.isAmoledSurfaceTheme())
    }

    @Test
    fun amoledBorderTokenIsDimNeutralGrey() {
        assertEquals(Color(0xFF242424), AmoledSurfaceBorder)
        assertEquals(Color(0xFF2A2A2A), AmoledSurfaceOutline)
    }
}
