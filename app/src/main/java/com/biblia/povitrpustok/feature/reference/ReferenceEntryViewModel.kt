package com.biblia.povitrpustok.feature.reference

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.core.result.BibleDataException
import com.biblia.povitrpustok.domain.model.ReaderDestination
import com.biblia.povitrpustok.domain.model.ScriptureResolution
import com.biblia.povitrpustok.domain.repository.ScriptureReferenceResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reference-entry state holder (Phase 8, plan §9–§11, §17; Track A §B).
 *
 * The existing domain [ScriptureReferenceResolver] is authoritative: the
 * UI only forwards the input and presents Resolved / Ambiguous / NotFound /
 * Invalid distinctly.
 *
 * Reader navigation uses a consumable [pendingNavigation] event: when a
 * resolution succeeds, the destination is set exactly once, the screen
 * consumes it, and returning to this screen cannot re-trigger navigation
 * (the persisted resolution never drives navigation).
 */
@HiltViewModel
class ReferenceEntryViewModel @Inject constructor(
    private val resolver: ScriptureReferenceResolver,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // DIST-01: the typed reference survives process recreation.
    private val _uiState = MutableStateFlow(
        ReferenceEntryUiState(query = savedStateHandle[KEY_QUERY] ?: "")
    )
    val uiState: StateFlow<ReferenceEntryUiState> = _uiState.asStateFlow()

    private var resolveJob: Job? = null

    private val _pendingNavigation = MutableStateFlow<ReaderDestination?>(null)
    val pendingNavigation: StateFlow<ReaderDestination?> = _pendingNavigation.asStateFlow()

    fun onQueryChange(query: String) {
        savedStateHandle[KEY_QUERY] = query
        _uiState.update { it.copy(query = query, resolution = null) }
    }

    fun resolve() {
        val query = _uiState.value.query
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true) }
            try {
                val resolution = resolver.resolve(query)
                _uiState.value = ReferenceEntryUiState(query = query, isResolving = false, resolution = resolution)
                if (resolution is ScriptureResolution.Resolved) {
                    _pendingNavigation.value = resolution.reference.toReaderDestination()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: BibleDataException) {
                _uiState.update {
                    it.copy(isResolving = false, resolution = ScriptureResolution.Invalid(e.message ?: "Failed to resolve reference."))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isResolving = false, resolution = ScriptureResolution.Invalid("Failed to resolve reference."))
                }
            }
        }
    }

    /** Consumes the one-shot navigation event (called by the screen after navigating). */
    fun consumeNavigation() {
        _pendingNavigation.value = null
    }

    private companion object {
        const val KEY_QUERY = "reference_query"
    }
}
