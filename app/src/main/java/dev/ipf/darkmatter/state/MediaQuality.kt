package dev.ipf.darkmatter.state

/**
 * How aggressively outgoing media (images, voice notes) is compressed before
 * send. The setting is a *ceiling*, not a target — a source already smaller
 * than the level's target ships as-is (no upscaling, no bitrate inflation).
 *
 * [Standard] is the default: a sensible quality/data trade-off for most users.
 *
 * Each level carries the concrete knobs the existing media pipeline already
 * supports:
 *  - [imageMaxEdgePx] / [imageJpegQuality] feed `MediaPipeline.readDownscaledJpeg`.
 *  - [audioBitrateBps] feeds `VoiceRecorder` (mono AAC-LC; the engine has no
 *    Opus re-encode path yet, so the bitrate knob applies to the AAC encoder).
 *
 * Privacy floor (orthogonal to this knob): every level — *including* [Original]
 * — still re-encodes images through `Bitmap.compress`, which strips EXIF
 * (GPS coordinates, device make/model). "Original" means "don't downscale,
 * keep maximum fidelity", not "ship the source bytes with their metadata".
 * The user never has to choose between sending a photo as-is and leaking
 * their location.
 *
 * Video has no re-encode path in this client (matching darkmatter-ios /
 * darkmatter-desktop), so video is always sent as-is regardless of this
 * setting — the quality levels apply to images and voice notes only in v1.
 */
enum class MediaQuality(
    val preferenceValue: String,
    val imageMaxEdgePx: Int,
    val imageJpegQuality: Int,
    val audioBitrateBps: Int,
) {
    Low(
        preferenceValue = "low",
        imageMaxEdgePx = 1024,
        imageJpegQuality = 70,
        audioBitrateBps = 32_000,
    ),
    Standard(
        preferenceValue = "standard",
        imageMaxEdgePx = 2048,
        imageJpegQuality = 85,
        audioBitrateBps = 64_000,
    ),
    High(
        preferenceValue = "high",
        imageMaxEdgePx = 4096,
        imageJpegQuality = 92,
        audioBitrateBps = 96_000,
    ),

    /**
     * No downscale and maximum-fidelity re-encode. [imageMaxEdgePx] is
     * [Int.MAX_VALUE] so `MediaPipeline.targetDimensions` returns the source
     * dimensions unchanged (the ceiling never bites); the high JPEG quality
     * keeps the re-encode visually lossless while still stripping EXIF.
     * Audio uses the highest supported bitrate.
     */
    Original(
        preferenceValue = "original",
        imageMaxEdgePx = Int.MAX_VALUE,
        imageJpegQuality = 100,
        audioBitrateBps = 96_000,
    ),
    ;

    companion object {
        val DEFAULT: MediaQuality = Standard

        fun fromPreference(value: String?): MediaQuality = entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
    }
}
