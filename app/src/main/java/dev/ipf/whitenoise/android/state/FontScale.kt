package dev.ipf.whitenoise.android.state

/**
 * In-app font-size scale (issue #403). A discrete four-step multiplier applied
 * app-wide by scaling every [androidx.compose.material3.Typography] style's
 * `fontSize` (see `WhiteNoiseTheme`).
 *
 * The factor is expressed in `sp`, so it *composes* with the OS font-size
 * setting rather than replacing it: because Compose still applies
 * `Density.fontScale` to `sp` values, the final on-screen size is
 * `system_scale × app_scale`. [Default] (1.0) is a no-op that leaves the OS
 * setting fully in control.
 *
 * This is an Android platform UI preference, not Marmot protocol data, so
 * SharedPreferences is the correct home per AGENTS.md.
 */
enum class FontScale(
    val preferenceValue: String,
    val scale: Float,
) {
    Small("small", 0.85f),
    Default("default", 1.0f),
    Large("large", 1.15f),
    ExtraLarge("extra_large", 1.30f),
    ;

    companion object {
        val DEFAULT: FontScale = Default

        fun fromPreference(value: String?): FontScale = entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
    }
}
