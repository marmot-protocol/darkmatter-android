package dev.ipf.whitenoise.android.state

import org.junit.Assert.assertEquals
import org.junit.Test

class FontScaleTest {
    @Test
    fun unknownAndNullPreferencesFallBackToDefault() {
        assertEquals(FontScale.Default, FontScale.fromPreference(null))
        assertEquals(FontScale.Default, FontScale.fromPreference("unknown"))
        assertEquals(FontScale.DEFAULT, FontScale.fromPreference(null))
    }

    @Test
    fun knownPreferencesRoundTrip() {
        FontScale.entries.forEach { scale ->
            assertEquals(scale, FontScale.fromPreference(scale.preferenceValue))
        }
    }

    @Test
    fun defaultStepIsANoOpMultiplier() {
        // Default must leave the OS font-size setting fully in control: the
        // final size is system_scale × app_scale, so a 1.0 app factor is a
        // no-op on top of whatever the system chose.
        assertEquals(1.0f, FontScale.Default.scale, 0.0f)
    }

    @Test
    fun stepsMatchTheIssueScaleFactors() {
        assertEquals(0.85f, FontScale.Small.scale, 0.0f)
        assertEquals(1.0f, FontScale.Default.scale, 0.0f)
        assertEquals(1.15f, FontScale.Large.scale, 0.0f)
        assertEquals(1.30f, FontScale.ExtraLarge.scale, 0.0f)
    }
}
