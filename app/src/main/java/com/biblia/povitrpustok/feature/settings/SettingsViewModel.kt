package com.biblia.povitrpustok.feature.settings

import androidx.lifecycle.ViewModel
import com.biblia.povitrpustok.core.locale.AppLocale
import com.biblia.povitrpustok.domain.model.AppTheme
import com.biblia.povitrpustok.domain.model.LineSpacing
import com.biblia.povitrpustok.domain.model.ReadingPreferences
import com.biblia.povitrpustok.domain.model.ScriptureFont
import com.biblia.povitrpustok.domain.model.ScriptureSize
import com.biblia.povitrpustok.domain.repository.ReadingPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings state (F-15): the persisted preference set plus its writers.
 *
 * Every setter writes through immediately, so a control change applies to
 * the next composition without a restart or a Save button.
 *
 * F2-14: [appLocale] is the app's own language choice. It is not part of the
 * reading preferences — it is owned by the AppCompat delegate and persisted by
 * the manifest's locale holder — so it is exposed here only so the control can
 * show the current selection and update it on tap.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ReadingPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<ReadingPreferences> = repository.preferences

    private val _appLocale = MutableStateFlow(AppLocale.currentTag())

    /** `en` or `kok-Latn`; English when no app-specific locale is set. */
    val appLocale: StateFlow<String> = _appLocale.asStateFlow()

    fun setScriptureSize(size: ScriptureSize) = repository.setScriptureSize(size)

    fun setLineSpacing(spacing: LineSpacing) = repository.setLineSpacing(spacing)

    fun setRedLetter(enabled: Boolean) = repository.setRedLetter(enabled)

    fun setTheme(theme: AppTheme) = repository.setTheme(theme)

    fun setScriptureFont(font: ScriptureFont) = repository.setScriptureFont(font)

    fun setAppLocale(tag: String) {
        AppLocale.setTag(tag)
        // Re-read rather than echo the request: the delegate normalises the tag
        // and is the single source of truth for what was actually applied.
        _appLocale.value = AppLocale.currentTag()
    }
}
