package com.biblia.povitrpustok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F2-09: the stored value is the enum name, so the round trip is a string and
 * a downgrade (or a hand-edited preferences file) must never crash the reader.
 */
class ScriptureFontTest {

    @Test
    fun every_enum_name_reads_back_as_itself() {
        ScriptureFont.entries.forEach { font ->
            assertEquals(font, ScriptureFont.fromNameOrSystem(font.name))
        }
    }

    @Test
    fun an_unknown_or_missing_name_keeps_the_system_font() {
        assertEquals(ScriptureFont.SYSTEM, ScriptureFont.fromNameOrSystem(null))
        assertEquals(ScriptureFont.SYSTEM, ScriptureFont.fromNameOrSystem(""))
        assertEquals(ScriptureFont.SYSTEM, ScriptureFont.fromNameOrSystem("comic_sans"))
    }

    @Test
    fun the_system_font_is_the_default_preference() {
        // The optional typeface must be opt-in: an upgrade changes nothing.
        assertEquals(ScriptureFont.SYSTEM, ReadingPreferences().scriptureFont)
    }
}
