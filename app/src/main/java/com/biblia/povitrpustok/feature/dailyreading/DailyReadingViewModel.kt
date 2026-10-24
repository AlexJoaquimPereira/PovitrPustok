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
import kotlinx.coroutines.launch

/**
 * A reading reference that could not be opened, mapped to a resource by the
 * screen. Carries the raw reference only, never user-visible prose.
 */
sealed interface DailyNotice {
    data class AmbiguousChapter(val reference: String) : DailyNotice
    data class AmbiguousVerse(val reference: String) : DailyNotice
    data class NotFound(val reference: String) : DailyNotice
    data class Invalid(val reference: String) : DailyNotice
}

/**
 * Daily Reading state holder (Phase 10, plan §12, §14, §27).
 *
 * "Today" uses the user's local date via an injectable [dateProvider]
 * (default [LocalDate.now] — deterministic in tests). Selecting a reading
 * converts the raw API reference through the existing
 * ScriptureReferenceResolver into a [ReaderDestination] — the single
 * Reader is reused; no second reader exists.
 *
 * F-18: the visible date is screen state ([selectedDate]), not a route
 * argument, so yesterday/tomorrow need no navigation and the back stack
 * still means "leave Daily Reading".
 */
@HiltViewModel
class DailyReadingViewModel @Inject constructor(
    private val repository: LiturgicalRepository,
    private val referenceResolver: ScriptureReferenceResolver,
    private val dateProvider: () -> LocalDate
) : ViewModel() {

    private val _uiState = MutableStateFlow<DailyReadingUiState>(DailyReadingUiState.Loading)
    val uiState: StateFlow<DailyReadingUiState> = _uiState.asStateFlow()

    private val _selectedDate = MutableStateFlow(dateProvider())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** The date whose data is currently loaded/being loaded (LITURGY-02). */
    private var loadedDate: LocalDate? = null

    /**
     * Whether the visible day follows the calendar's today. False once the
     * user browses another day, so a resume never yanks them back (F-18).
     */
    private var followsToday = true
    private var loadJob: Job? = null

    private val _pendingNavigation = MutableStateFlow<ReaderDestination?>(null)
    val pendingNavigation: StateFlow<ReaderDestination?> = _pendingNavigation.asStateFlow()

    private val _navigationNotice = MutableStateFlow<DailyNotice?>(null)
    val navigationNotice: StateFlow<DailyNotice?> = _navigationNotice.asStateFlow()

    /**
     * Loads [selectedDate]. [forceRefresh] skips the cache so the user can
     * update offline data; a failed refresh still falls back to the cache.
     */
    fun load(forceRefresh: Boolean = false) {
        val date = _selectedDate.value
        // Record the date synchronously so an immediately-following resume
        // refresh does not reload the same day.
        loadedDate = date
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = DailyReadingUiState.Loading
            when (val result = repository.getLiturgicalDay(date, forceRefresh)) {
                is LiturgicalDayResult.Success ->
                    _uiState.value = DailyReadingUiState.Success(
                        day = result.day,
                        cachedAt = result.cachedAt
                    )
                is LiturgicalDayResult.Error ->
                    _uiState.value = DailyReadingUiState.Error(result.kind)
            }
        }
    }

    fun previousDay() = selectDay(_selectedDate.value.minusDays(1))

    fun nextDay() = selectDay(_selectedDate.value.plusDays(1))

    /** Returns to the real today (the "Today" affordance). */
    fun goToToday() = selectDay(dateProvider())

    fun isToday(date: LocalDate = _selectedDate.value): Boolean = date == dateProvider()

    /**
     * Re-fetches the current day from the network, bypassing the cache
     * (the "Update" action on the offline notice).
     */
    fun refresh() = load(forceRefresh = true)

    /**
     * LITURGY-02: called when the screen resumes. If the user is still on
     * "today" and the local calendar date has changed (e.g. the app was left
     * open across midnight), follow the new day; a resume on a past/future
     * day, or on the same day, makes no request.
     */
    fun refreshIfDateChanged() {
        val today = dateProvider()
        if (loadedDate == null) {
            load()
            return
        }
        if (followsToday && loadedDate != today) {
            _selectedDate.value = today
            followsToday = true
            load()
        }
    }

    private fun selectDay(date: LocalDate) {
        if (date == _selectedDate.value && _uiState.value is DailyReadingUiState.Success) return
        _selectedDate.value = date
        followsToday = date == dateProvider()
        load()
    }

    /** Converts a reading reference and requests Reader navigation. */
    fun openReading(reading: LiturgicalReading) {
        viewModelScope.launch {
            when (val resolution = referenceResolver.resolve(reading.reference)) {
                is ScriptureResolution.Resolved ->
                    _pendingNavigation.value = resolution.reference.toReaderDestination()
                is ScriptureResolution.Ambiguous ->
                    _navigationNotice.value = DailyNotice.AmbiguousChapter(reading.reference)
                is ScriptureResolution.AmbiguousVerse ->
                    _navigationNotice.value = DailyNotice.AmbiguousVerse(reading.reference)
                is ScriptureResolution.NotFound ->
                    _navigationNotice.value = DailyNotice.NotFound(reading.reference)
                is ScriptureResolution.Invalid ->
                    _navigationNotice.value = DailyNotice.Invalid(reading.reference)
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
