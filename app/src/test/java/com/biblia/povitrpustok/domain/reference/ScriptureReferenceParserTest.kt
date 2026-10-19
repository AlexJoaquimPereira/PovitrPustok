package com.biblia.povitrpustok.domain.reference

import com.biblia.povitrpustok.domain.model.ScriptureReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure parser tests (Phase 6 §17, §20) — no database involved.
 */
class ScriptureReferenceParserTest {

    private fun valid(input: String): ScriptureReference {
        val result = ScriptureReferenceParser.parse(input)
        assertTrue("expected valid parse for '$input', got $result", result is ScriptureReferenceParser.Result.Valid)
        return (result as ScriptureReferenceParser.Result.Valid).reference
    }

    private fun invalid(input: String): String {
        val result = ScriptureReferenceParser.parse(input)
        assertTrue("expected invalid parse for '$input', got $result", result is ScriptureReferenceParser.Result.Invalid)
        return (result as ScriptureReferenceParser.Result.Invalid).reason
    }

    @Test
    fun book_chapter() {
        assertEquals(ScriptureReference("James", 1, null, null), valid("James 1"))
        assertEquals(ScriptureReference("Job", 26, null, null), valid("Job 26"))
    }

    @Test
    fun book_chapter_verse() {
        assertEquals(ScriptureReference("James", 1, 5, 5), valid("James 1:5"))
        assertEquals(ScriptureReference("Genesis", 17, 11, 11), valid("Genesis 17:11"))
    }

    @Test
    fun book_chapter_verse_range() {
        assertEquals(ScriptureReference("James", 1, 5, 8), valid("James 1:5-8"))
        assertEquals(ScriptureReference("Genesis", 17, 11, 12), valid("Genesis 17:11-12"))
    }

    @Test
    fun bookless_reference_has_null_book() {
        val ref = valid("3:16")
        assertNull(ref.book)
        assertEquals(3, ref.chapter)
        assertEquals(16, ref.verseStart)
        assertEquals(16, ref.verseEnd)
    }

    @Test
    fun case_is_preserved_and_whitespace_is_tolerated() {
        assertEquals(ScriptureReference("JAMES", 1, 5, 5), valid("  JAMES  1:5  "))
        assertEquals(ScriptureReference("James", 1, 5, 5), valid("\tJames 1:5\n"))
    }

    @Test
    fun empty_and_whitespace_only_are_invalid() {
        invalid("")
        invalid("   ")
        invalid("\t\n ")
    }

    @Test
    fun zero_and_negative_numbers_are_invalid() {
        invalid("James 0")
        invalid("James -1")
        invalid("James 1:0")
        invalid("James 1:-5")
        invalid("James 0:5")
    }

    @Test
    fun malformed_colon_and_range_are_invalid() {
        invalid("James 1:")
        invalid("James 1:5:")
        invalid("James 1::5")
        invalid("James :5")
        invalid("James 1:5-")
        invalid("James 1:-8")
        invalid("James 1:5-8-9")
    }

    @Test
    fun reversed_range_is_invalid() {
        val reason = invalid("James 1:8-5")
        assertTrue(reason.contains("Reversed", ignoreCase = true))
    }

    @Test
    fun non_numeric_chapter_or_verse_is_invalid() {
        invalid("James one")
        invalid("James 1:five")
        invalid("Genesis 17:11-twelve")
    }

    @Test
    fun multi_word_and_digit_prefixed_books_parse() {
        // Phase 10 expansion (§7/§33): the last token is the locator, the
        // rest is the book name.
        assertEquals(ScriptureReference("1 Kings", 3, 16, 16), valid("1 Kings 3:16"))
        assertEquals(ScriptureReference("2 Chronicles", 7, 14, 14), valid("2 Chronicles 7:14"))
        assertEquals(ScriptureReference("1 John", 4, 8, 8), valid("1 John 4:8"))
        assertEquals(ScriptureReference("1 Corinthians", 15, 20, 27), valid("1 Corinthians 15:20-27"))
        assertEquals(ScriptureReference("1 Maccabees", 1, 1, 1), valid("1 Maccabees 1:1"))
        assertEquals(ScriptureReference("2 Kings", 5, 14, 14), valid("2 Kings 5:14"))
    }

    @Test
    fun comma_separated_verse_enumerations_map_to_a_span() {
        // Phase 10 expansion: lectionary psalms enumerate verses with commas.
        assertEquals(ScriptureReference("Psalm", 45, 10, 16), valid("Psalm 45:10, 11, 12, 16"))
        assertEquals(ScriptureReference("Psalm", 112, 1, 9), valid("Psalm 112:1-2, 5-6, 7-8, 9"))
        assertEquals(ScriptureReference("Psalm", 118, 1, 24), valid("Psalm 118:1, 24"))
    }

    @Test
    fun verse_letter_suffixes_are_parsed_for_their_numeric_verse() {
        // Track A §F / corrective Issue 6: liturgical suffixes denote a
        // verse portion; the range model represents the numeric verse.
        assertEquals(ScriptureReference("Wisdom", 9, 13, 18), valid("Wisdom 9:13-18b"))
        assertEquals(ScriptureReference("James", 1, 13, 13), valid("James 1:13a"))
        assertEquals(ScriptureReference("Psalm", 90, 3, 17), valid("Psalm 90:3-4b, 5-6a, 12-13, 14 and 17"))
        // uppercase suffixes (corrective Issue 6)
        assertEquals(ScriptureReference("Revelation", 11, 19, 19), valid("Revelation 11:19A"))
        assertEquals(ScriptureReference("Revelation", 11, 19, 19), valid("Revelation 11:19a"))
        assertEquals(ScriptureReference("James", 1, 18, 18), valid("James 1:18B"))
        assertEquals(ScriptureReference("James", 1, 18, 18), valid("James 1:18b"))
        // normal numeric references remain unchanged
        assertEquals(ScriptureReference("Revelation", 11, 19, 19), valid("Revelation 11:19"))
        assertEquals(ScriptureReference("James", 1, 5, 8), valid("James 1:5-8"))
    }

    @Test
    fun and_word_is_an_enumeration_separator() {
        assertEquals(
            ScriptureReference("Psalm", 90, 3, 17),
            valid("Psalm 90:3-4, 5-6, 12-13, 14 and 17")
        )
    }

    @Test
    fun chapter_less_books_parse_no_colon_enumerations_as_verses() {
        // Track A §F: Philemon has a single chapter; "9-10, 12-17" is a
        // verse enumeration of chapter 1.
        assertEquals(ScriptureReference("Philemon", 1, 9, 17), valid("Philemon 9-10, 12-17"))
        assertEquals(ScriptureReference("Philemon", 1, 4, 21), valid("Philemon 4-7, 9, 12-21"))
    }

    @Test
    fun numbered_books_still_parse() {
        assertEquals(ScriptureReference("1 Thessalonians", 5, 16, 18), valid("1 Thessalonians 5:16-18"))
        assertEquals(ScriptureReference("2 Thessalonians", 3, 1, 5), valid("2 Thessalonians 3:1-5"))
        assertEquals(ScriptureReference("1 John", 4, 8, 8), valid("1 John 4:8"))
    }

    @Test
    fun malformed_enumeration_is_invalid() {
        invalid("Psalm 45:10,")
        invalid("Psalm 45:10,,11")
        invalid("Psalm 45:16, 10") // descending enumeration
    }

    @Test
    fun en_and_em_dashes_are_range_separators() {
        // REF-03: lectionary feeds use typographic dashes.
        assertEquals(ScriptureReference("James", 1, 5, 8), valid("James 1:5\u20138"))
        assertEquals(ScriptureReference("James", 1, 5, 8), valid("James 1:5\u20148"))
        assertEquals(ScriptureReference("James", 1, 5, 8), valid("James 1:5\u20108"))
        assertEquals(ScriptureReference("James", 1, 5, 8), valid("James 1:5\u20118"))
    }

    @Test
    fun cross_chapter_reference_is_unsupported() {
        invalid("John 3:16-4:2")
    }
}
