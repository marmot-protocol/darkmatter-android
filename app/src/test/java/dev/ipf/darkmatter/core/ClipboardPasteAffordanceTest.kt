package dev.ipf.darkmatter.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardPasteAffordanceTest {
    @Test
    fun canOfferPasteOnlyForPlainTextMimeType() {
        assertTrue(ClipboardPasteAffordance.canOfferPaste(listOf("text/plain")))
        assertTrue(ClipboardPasteAffordance.canOfferPaste(listOf("image/png", "text/plain")))
        assertFalse(ClipboardPasteAffordance.canOfferPaste(emptyList()))
        assertFalse(ClipboardPasteAffordance.canOfferPaste(listOf("image/png", "text/html", "text/uri-list")))
    }
}
