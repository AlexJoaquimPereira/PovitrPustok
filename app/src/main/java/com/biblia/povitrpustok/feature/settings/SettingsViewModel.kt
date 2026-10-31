package com.biblia.povitrpustok.feature.settings

import androidx.lifecycle.ViewModel
import com.biblia.povitrpustok.domain.model.AppTheme
import com.biblia.povitrpustok.domain.model.LineSpacing
import com.biblia.povitrpustok.domain.model.ReadingPreferences
import com.biblia.povitrpustok.domain.model.ScriptureFont
import com.biblia.povitrpustok.domain.model.ScriptureSize
import com.biblia.povitrpustok.domain.repository.ReadingPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Settings state (F-15): the persisted preference set plus its writers.
 *
 * Every setter writes through immediately, so a control change applies to
 * the next composition without a restart or a Save button.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ReadingPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<ReadingPreferences> = repository.preferences

    fun setScriptureSize(size: ScriptureSize) = repository.setScriptureSize(size)

    fun setLineSpacing(spacing: LineSpacing) = repository.setLineSpacing(spacing)

    fun setRedLetter(enabled: Boolean) = repository.setRedLetter(enabled)

    fun setTheme(theme: AppTheme) = repository.setTheme(theme)

    fun setScriptureFont(font: ScriptureFont) = repository.setScriptureFont(font)
}
