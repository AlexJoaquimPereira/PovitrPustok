package com.biblia.povitrpustok.domain.repository

import com.biblia.povitrpustok.domain.model.AppTheme
import com.biblia.povitrpustok.domain.model.LineSpacing
import com.biblia.povitrpustok.domain.model.ReadingPreferences
import com.biblia.povitrpustok.domain.model.ScriptureFont
import com.biblia.povitrpustok.domain.model.ScriptureSize
import kotlinx.coroutines.flow.StateFlow

/**
 * Persistent reading preferences (F-15).
 *
 * Writes are synchronous (SharedPreferences) and publish a new state
 * immediately, so a toggle applies without a restart.
 */
interface ReadingPreferencesRepository {

    val preferences: StateFlow<ReadingPreferences>

    fun setScriptureSize(size: ScriptureSize)

    fun setLineSpacing(spacing: LineSpacing)

    fun setRedLetter(enabled: Boolean)

    fun setTheme(theme: AppTheme)

    fun setScriptureFont(font: ScriptureFont)
}
