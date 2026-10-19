package com.biblia.povitrpustok.feature.dailyreading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.domain.model.LiturgicalDayResult
import com.biblia.povitrpustok.domain.model.LiturgicalReading
import com.biblia.povitrpustok.domain.model.ReaderDestination
import com.biblia.povitrpustok.domain.model.ScriptureResolution
import com.biblia.povitrpustok.domain.repository.LiturgicalRepository
import com.biblia.povitrpustok.domain.repository.ScriptureReferenceResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Daily Reading state holder (Phase 10, plan §12, §14, §27).
 *
 * "Today" uses the user's local date via an injectable [dateProvider]
 * (default [LocalDate.now] — deterministic in tests). Selecting a reading
 * converts the raw API reference through the existing
 * ScriptureReferenceResolver into a [ReaderDestination] — the single
 * Reader is reused; no second reader exists.
 */
@HiltViewModel
class DailyReadingViewModel @Inject constructor(
    private val repository: LiturgicalRepository,
    private val referenceResolver: ScriptureReferenceResolver,
    private val dateProvider: () -> LocalDate
) : ViewModel() {

    private val _uiState = MutableStateFlow<DailyReadingUiState>(DailyReadingUiState.Loading)
    val uiState: StateFlow<DailyReadingUiState> = _uiState.asStateFlow()

    /** The local date whose data is currently loaded/being loaded (LITURGY-02). */
    private var loadedDate: LocalDate? = null
    private var loadJob: Job? = null

    private val _pendingNavigation = MutableStateFlow<ReaderDestination?>(null)
    val pendingNavigation: StateFlow<ReaderDestination?> = _pendingNavigation.asStateFlow()

    private val _navigationNotice = MutableStateFlow<String?>(null)
    val navigationNotice: StateFlow<String?> = _navigationNotice.asStateFlow()

    fun load() {
        val date = dateProvider()
        // Record the date synchronously so an immediately-following resume
        // refresh does not reload the same day.
        loadedDate = date
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = DailyReadingUiState.Loading
            when (val result = repository.getLiturgicalDay(date)) {
                is LiturgicalDayResult.Success ->
                    _uiState.value = DailyReadingUiState.Success(result.day)
                is LiturgicalDayResult.Error ->
                    _uiState.value = DailyReadingUiState.Error(result.message)
            }
        }
    }

    /**
     * LITURGY-02: called when the screen resumes. Reloads only when the local
     * calendar date has changed (e.g. the app was left open across midnight);
     * a same-day resume keeps the current state and makes no network call.
     */
    fun refreshIfDateChanged() {
        if (loadedDate != dateProvider()) load()
    }

    /** Converts a reading reference and requests Reader navigation. */
    fun openReading(reading: LiturgicalReading) {
        viewModelScope.launch {
            when (val resolution = referenceResolver.resolve(reading.reference)) {
                is ScriptureResolution.Resolved ->
                    _pendingNavigation.value = resolution.reference.toReaderDestination()
                is ScriptureResolution.Ambiguous ->
                    _navigationNotice.value = "Multiple chapters match '${reading.reference}'."
                is ScriptureResolution.AmbiguousVerse ->
                    _navigationNotice.value = "Multiple source verses match '${reading.reference}'."
                is ScriptureResolution.NotFound ->
                    _navigationNotice.value = "Could not find '${reading.reference}' in the Bible."
                is ScriptureResolution.Invalid ->
                    _navigationNotice.value = "Unsupported reference '${reading.reference}'."
            }
        }
    }

    fun consumeNavigation() {
        _pendingNavigation.value = null
    }

    fun consumeNotice() {
        _navigationNotice.value = null
    }
}
