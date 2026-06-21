package dev.ipf.darkmatter.core

object ClipboardPasteAffordance {
    private const val PLAIN_TEXT_MIME_TYPE = "text/plain"

    fun canOfferPaste(mimeTypes: Iterable<String>): Boolean =
        mimeTypes.any { mimeType ->
            mimeType.equals(PLAIN_TEXT_MIME_TYPE, ignoreCase = true)
        }
}
