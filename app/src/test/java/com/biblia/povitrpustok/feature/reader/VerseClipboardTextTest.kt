package com.biblia.povitrpustok.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * F-23: clipboard/share text comes from the domain plain text (never HTML)
 * and always carries its reference.
 */
class VerseClipboardTextTest {

    @Test
    fun a_plain_verse_carries_book_chapter_and_verse() {
        assertEquals(
            "Juanv 3:16\nDev ak'kho sonvsar itlo mog kelo",
            verseClipboardText(
                bookName = "Juanv",
                chapterNumber = 3,
                verseStart = 16,
                verseEnd = 16,
                plainText = "Dev ak'kho sonvsar itlo mog kelo"
            )
        )
    }

    @Test
    fun a_source_range_keeps_its_range() {
        assertEquals(
            "Juanv 1:11–12\nText",
            verseClipboardText(
                bookName = "Juanv",
                chapterNumber = 1,
                verseStart = 11,
                verseEnd = 12,
                plainText = "Text"
            )
        )
    }

    @Test
    fun html_is_never_included() {
        val text = verseClipboardText(
            bookName = "Juanv",
            chapterNumber = 3,
            verseStart = 16,
            verseEnd = 16,
            plainText = "plain only"
        )

        assertFalse("markup must not leak into the clipboard", text.contains("<"))
        assertFalse("corpus markup must not leak into the clipboard", text.contains("font"))
    }

    @Test
    fun a_verse_without_plain_text_still_copies_its_reference() {
        assertEquals(
            "Juanv 3:16",
            verseClipboardText("Juanv", 3, 16, 16, "   ")
        )
    }
}
