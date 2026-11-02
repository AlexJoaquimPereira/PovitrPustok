package com.biblia.povitrpustok.testutil

import com.biblia.povitrpustok.domain.model.AppTheme
import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.domain.model.LineSpacing
import com.biblia.povitrpustok.domain.model.ReadingPreferences
import com.biblia.povitrpustok.domain.model.ScriptureFont
import com.biblia.povitrpustok.domain.model.ScriptureSize
import com.biblia.povitrpustok.domain.repository.ReadingPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory [ReadingPreferencesRepository] for ViewModel tests that only
 * need an edition (or another preference) without Android SharedPreferences.
 * Mirrors the production normalization: unknown editions become Konkani.
 */
class FakeReadingPreferencesRepository(
    initial: ReadingPreferences = ReadingPreferences()
) : ReadingPreferencesRepository {

    private val _preferences = MutableStateFlow(initial)
    override val preferences: StateFlow<ReadingPreferences> = _preferences

    override fun setScriptureSize(size: ScriptureSize) {
        _preferences.value = _preferences.value.copy(scriptureSize = size)
    }

    override fun setLineSpacing(spacing: LineSpacing) {
        _preferences.value = _preferences.value.copy(lineSpacing = spacing)
    }

    override fun setRedLetter(enabled: Boolean) {
        _preferences.value = _preferences.value.copy(redLetter = enabled)
    }

    override fun setTheme(theme: AppTheme) {
        _preferences.value = _preferences.value.copy(theme = theme)
    }

    override fun setScriptureFont(font: ScriptureFont) {
        _preferences.value = _preferences.value.copy(scriptureFont = font)
    }

    override fun setReadingEdition(editionId: String) {
        _preferences.value = _preferences.value.copy(
            readingEditionId = BibleEditions.byId(editionId)?.id ?: BibleEditions.KONKANI_ID
        )
    }
}
