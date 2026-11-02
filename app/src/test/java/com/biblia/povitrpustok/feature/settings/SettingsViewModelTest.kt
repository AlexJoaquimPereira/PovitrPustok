package com.biblia.povitrpustok.feature.settings

import com.biblia.povitrpustok.domain.model.AppTheme
import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.domain.model.ScriptureSize
import com.biblia.povitrpustok.testutil.FakeReadingPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Settings writers, exercised through the ViewModel rather than the screen:
 * every control writes through to the repository, and the edition writer is a
 * reading preference like the others.
 *
 * Robolectric is needed for the constructor alone: the app-language control
 * reads the AppCompat delegate, which is an Android API.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @Test
    fun reading_edition_is_written_through_for_every_shipped_edition() {
        val repository = FakeReadingPreferencesRepository()
        val viewModel = SettingsViewModel(repository)

        assertEquals(BibleEditions.KONKANI_ID, viewModel.preferences.value.readingEditionId)

        viewModel.setReadingEdition(BibleEditions.WEBC_ID)
        assertEquals(BibleEditions.WEBC_ID, repository.preferences.value.readingEditionId)
        assertEquals(BibleEditions.WEBC_ID, viewModel.preferences.value.readingEditionId)

        viewModel.setReadingEdition(BibleEditions.DRA_ID)
        assertEquals(BibleEditions.DRA_ID, viewModel.preferences.value.readingEditionId)

        viewModel.setReadingEdition(BibleEditions.KONKANI_ID)
        assertEquals(BibleEditions.KONKANI_ID, viewModel.preferences.value.readingEditionId)
    }

    @Test
    fun unknown_reading_edition_falls_back_to_konkani() {
        val viewModel = SettingsViewModel(FakeReadingPreferencesRepository())

        viewModel.setReadingEdition("not-a-shipped-edition")

        // The repository owns the normalisation; a stale id can never leave the
        // control with nothing selected.
        assertEquals(BibleEditions.KONKANI_ID, viewModel.preferences.value.readingEditionId)
    }

    @Test
    fun changing_the_edition_leaves_the_other_reading_preferences_alone() {
        val viewModel = SettingsViewModel(FakeReadingPreferencesRepository())

        viewModel.setScriptureSize(ScriptureSize.LARGE)
        viewModel.setTheme(AppTheme.LIGHT)
        viewModel.setReadingEdition(BibleEditions.DRA_ID)

        val preferences = viewModel.preferences.value
        assertEquals(BibleEditions.DRA_ID, preferences.readingEditionId)
        assertEquals(ScriptureSize.LARGE, preferences.scriptureSize)
        assertEquals(AppTheme.LIGHT, preferences.theme)
    }
}
