package com.biblia.povitrpustok.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.biblia.povitrpustok.domain.model.AppTheme
import com.biblia.povitrpustok.domain.model.LineSpacing
import com.biblia.povitrpustok.domain.model.ReadingPreferences
import com.biblia.povitrpustok.domain.model.ScriptureSize
import com.biblia.povitrpustok.domain.repository.ReadingPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [ReadingPreferencesRepository] over a private [SharedPreferences] file
 * (F-15: framework-only, no Room migration, no DB change).
 *
 * Unknown stored names fall back to the defaults rather than throwing, so a
 * downgrade or a hand-edited file cannot crash the reader.
 */
@Singleton
class SharedPreferencesReadingPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) : ReadingPreferencesRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(read())
    override val preferences: StateFlow<ReadingPreferences> = _preferences.asStateFlow()

    override fun setScriptureSize(size: ScriptureSize) {
        prefs.edit().putString(KEY_SCRIPTURE_SIZE, size.name).apply()
        _preferences.value = _preferences.value.copy(scriptureSize = size)
    }

    override fun setLineSpacing(spacing: LineSpacing) {
        prefs.edit().putString(KEY_LINE_SPACING, spacing.name).apply()
        _preferences.value = _preferences.value.copy(lineSpacing = spacing)
    }

    override fun setRedLetter(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RED_LETTER, enabled).apply()
        _preferences.value = _preferences.value.copy(redLetter = enabled)
    }

    override fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _preferences.value = _preferences.value.copy(theme = theme)
    }

    private fun read(): ReadingPreferences = ReadingPreferences(
        scriptureSize = ScriptureSize.fromNameOrMedium(
            prefs.getString(KEY_SCRIPTURE_SIZE, null)
        ),
        lineSpacing = LineSpacing.fromNameOrNormal(
            prefs.getString(KEY_LINE_SPACING, null)
        ),
        redLetter = prefs.getBoolean(KEY_RED_LETTER, true),
        theme = AppTheme.fromNameOrSystem(prefs.getString(KEY_THEME, null))
    )

    private companion object {
        const val FILE_NAME = "reading_preferences"
        const val KEY_SCRIPTURE_SIZE = "scripture_size"
        const val KEY_LINE_SPACING = "line_spacing"
        const val KEY_RED_LETTER = "red_letter"
        const val KEY_THEME = "theme"
    }
}
