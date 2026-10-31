package com.biblia.povitrpustok.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.domain.model.AppTheme
import com.biblia.povitrpustok.domain.model.LineSpacing
import com.biblia.povitrpustok.domain.model.ReadingPreferences
import com.biblia.povitrpustok.domain.model.ScriptureFont
import com.biblia.povitrpustok.domain.model.ScriptureSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F-15: preferences persist across repository instances and apply through a
 * StateFlow immediately.
 */
@RunWith(RobolectricTestRunner::class)
class SharedPreferencesReadingPreferencesRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("reading_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun defaults_match_the_pre_settings_behaviour() {
        val repository = SharedPreferencesReadingPreferencesRepository(context)

        assertEquals(
            ReadingPreferences(
                scriptureSize = ScriptureSize.MEDIUM,
                lineSpacing = LineSpacing.NORMAL,
                redLetter = true,
                theme = AppTheme.SYSTEM,
                scriptureFont = ScriptureFont.SYSTEM
            ),
            repository.preferences.value
        )
    }

    @Test
    fun the_optional_typeface_is_off_by_default() {
        // F2-09: an existing install must not change appearance on upgrade.
        val repository = SharedPreferencesReadingPreferencesRepository(context)

        assertEquals(ScriptureFont.SYSTEM, repository.preferences.value.scriptureFont)
    }

    @Test
    fun the_typeface_choice_round_trips_through_a_new_instance() {
        val repository = SharedPreferencesReadingPreferencesRepository(context)

        repository.setScriptureFont(ScriptureFont.ATKINSON_HYPERLEGIBLE)
        assertEquals(
            ScriptureFont.ATKINSON_HYPERLEGIBLE,
            repository.preferences.value.scriptureFont
        )

        val reopened = SharedPreferencesReadingPreferencesRepository(context)
        assertEquals(
            ScriptureFont.ATKINSON_HYPERLEGIBLE,
            reopened.preferences.value.scriptureFont
        )

        reopened.setScriptureFont(ScriptureFont.SYSTEM)
        assertEquals(ScriptureFont.SYSTEM, reopened.preferences.value.scriptureFont)
    }

    @Test
    fun writes_publish_new_state_and_survive_a_new_instance() {
        val repository = SharedPreferencesReadingPreferencesRepository(context)

        repository.setScriptureSize(ScriptureSize.LARGE)
        repository.setLineSpacing(LineSpacing.RELAXED)
        repository.setRedLetter(false)
        repository.setTheme(AppTheme.DARK)
        repository.setScriptureFont(ScriptureFont.ATKINSON_HYPERLEGIBLE)

        val updated = repository.preferences.value
        assertEquals(ScriptureSize.LARGE, updated.scriptureSize)
        assertEquals(LineSpacing.RELAXED, updated.lineSpacing)
        assertFalse(updated.redLetter)
        assertEquals(AppTheme.DARK, updated.theme)
        assertEquals(ScriptureFont.ATKINSON_HYPERLEGIBLE, updated.scriptureFont)

        // A fresh instance (process restart) reads the same values back.
        val reopened = SharedPreferencesReadingPreferencesRepository(context)
        assertEquals(updated, reopened.preferences.value)
    }

    @Test
    fun an_unknown_stored_name_falls_back_to_the_default() {
        context.getSharedPreferences("reading_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("scripture_size", "GIGANTIC")
            .putString("line_spacing", "ULTRA")
            .putString("theme", "SEPIA")
            .putString("scripture_font", "COMIC_SANS")
            .commit()

        val repository = SharedPreferencesReadingPreferencesRepository(context)

        assertEquals(ScriptureSize.MEDIUM, repository.preferences.value.scriptureSize)
        assertEquals(LineSpacing.NORMAL, repository.preferences.value.lineSpacing)
        assertEquals(AppTheme.SYSTEM, repository.preferences.value.theme)
        assertEquals(ScriptureFont.SYSTEM, repository.preferences.value.scriptureFont)
    }

    @Test
    fun red_letter_can_be_turned_back_on() {
        val repository = SharedPreferencesReadingPreferencesRepository(context)

        repository.setRedLetter(false)
        assertFalse(repository.preferences.value.redLetter)

        repository.setRedLetter(true)
        assertTrue(repository.preferences.value.redLetter)
    }
}
