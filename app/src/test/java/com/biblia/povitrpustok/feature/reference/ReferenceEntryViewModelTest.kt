package com.biblia.povitrpustok.feature.reference

import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ResolvedScriptureReference
import com.biblia.povitrpustok.domain.model.ScriptureResolution
import com.biblia.povitrpustok.domain.repository.ScriptureReferenceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Reference entry ViewModel tests (Phase 8 §18) with a fake resolver:
 * the UI forwards input verbatim and presents each resolution state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReferenceEntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeResolver : ScriptureReferenceResolver {
        val queries = mutableListOf<Pair<String, String?>>()
        var result: ScriptureResolution = ScriptureResolution.Invalid("unconfigured")

        override suspend fun resolve(reference: String, contextBookId: String?): ScriptureResolution {
            queries += reference to contextBookId
            return result
        }
    }

    private fun resolved(verseId: Long? = 5L) = ScriptureResolution.Resolved(
        ResolvedScriptureReference(
            bookId = "jakob",
            chapterId = 1,
            chapterNumber = 1,
            chapterPosition = 1,
            verseId = verseId,
            verseStart = 5,
            verseEnd = 5
        )
    )

    @Test
    fun normal_reference_resolves_to_destination_with_verse_target() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver().apply { result = resolved(verseId = 123L) }
        val viewModel = ReferenceEntryViewModel(resolver)

        viewModel.onQueryChange("James 1:5")
        viewModel.resolve()
        advanceUntilIdle()

        val destination = viewModel.pendingNavigation.value
        assertNotNull(destination)
        assertEquals("jakob", destination!!.bookId)
        assertEquals(1, destination.chapterPosition)
        assertEquals(123L, destination.verseId)
        assertEquals(listOf("James 1:5" to null), resolver.queries)
    }

    @Test
    fun verse_range_resolves_to_the_single_source_verse() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver().apply {
            result = ScriptureResolution.Resolved(
                ResolvedScriptureReference(
                    bookId = "Utpoti", chapterId = 17, chapterNumber = 17,
                    chapterPosition = 17, verseId = 999L, verseStart = 11, verseEnd = 12
                )
            )
        }
        val viewModel = ReferenceEntryViewModel(resolver)

        viewModel.onQueryChange("Genesis 17:11-12")
        viewModel.resolve()
        advanceUntilIdle()

        val destination = viewModel.pendingNavigation.value
        assertEquals("Utpoti", destination!!.bookId)
        assertEquals(17, destination.chapterPosition)
        assertEquals(999L, destination.verseId)
    }

    @Test
    fun book_only_reference_has_no_verse_target() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver().apply { result = resolved(verseId = null) }
        val viewModel = ReferenceEntryViewModel(resolver)

        viewModel.onQueryChange("James 1")
        viewModel.resolve()
        advanceUntilIdle()

        val destination = viewModel.pendingNavigation.value
        assertEquals("jakob", destination!!.bookId)
        assertEquals(1, destination.chapterPosition)
        assertNull(destination.verseId)
    }

    @Test
    fun invalid_reference_presents_invalid_state() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver().apply { result = ScriptureResolution.Invalid("Reference is empty.") }
        val viewModel = ReferenceEntryViewModel(resolver)

        viewModel.onQueryChange("")
        viewModel.resolve()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.resolution is ScriptureResolution.Invalid)
    }

    @Test
    fun unknown_reference_presents_not_found_state() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver().apply { result = ScriptureResolution.NotFound("Unknown book: 'NotABook'.") }
        val viewModel = ReferenceEntryViewModel(resolver)

        viewModel.onQueryChange("NotABook 1")
        viewModel.resolve()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.resolution is ScriptureResolution.NotFound)
    }

    @Test
    fun job_ambiguity_surfaces_both_candidates() = runTest(dispatcher.scheduler) {
        val candidates = listOf(
            Chapter(id = 26, bookId = "Jobachem_Pustok", number = 26, position = 26),
            Chapter(id = 27, bookId = "Jobachem_Pustok", number = 26, position = 27)
        )
        val resolver = FakeResolver().apply {
            result = ScriptureResolution.Ambiguous("Jobachem_Pustok", 26, candidates)
        }
        val viewModel = ReferenceEntryViewModel(resolver)

        viewModel.onQueryChange("Job 26")
        viewModel.resolve()
        advanceUntilIdle()

        val resolution = viewModel.uiState.value.resolution
        assertTrue("must not silently pick", resolution is ScriptureResolution.Ambiguous)
        val ambiguous = resolution as ScriptureResolution.Ambiguous
        assertEquals(2, ambiguous.candidates.size)
        assertEquals(setOf(26, 27), ambiguous.candidates.map { it.position }.toSet())
        assertEquals(2, ambiguous.candidates.map { it.id }.toSet().size)
    }

    @Test
    fun input_is_forwarded_verbatim_and_case_preserved() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver().apply { result = resolved(verseId = 123L) }
        val viewModel = ReferenceEntryViewModel(resolver)

        viewModel.onQueryChange("JAMES 1:5")
        viewModel.resolve()
        advanceUntilIdle()

        // the ViewModel must not transform the query — the resolver is authoritative
        assertEquals(listOf("JAMES 1:5" to null), resolver.queries)
        assertEquals("jakob", viewModel.pendingNavigation.value!!.bookId)
    }

    @Test
    fun editing_query_clears_previous_resolution() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver().apply { result = resolved(verseId = 123L) }
        val viewModel = ReferenceEntryViewModel(resolver)

        viewModel.onQueryChange("James 1:5")
        viewModel.resolve()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.resolution)

        viewModel.onQueryChange("James 1:6")
        runCurrent()
        assertNull("changing the query must clear the stale resolution", viewModel.uiState.value.resolution)
    }

    @Test
    fun navigation_is_one_shot_and_consumable() = runTest(dispatcher.scheduler) {
        val resolver = FakeResolver().apply { result = resolved(verseId = 123L) }
        val viewModel = ReferenceEntryViewModel(resolver)

        viewModel.onQueryChange("James 1:5")
        viewModel.resolve()
        advanceUntilIdle()

        // successful resolution produces exactly one navigation event
        assertNotNull(viewModel.pendingNavigation.value)

        // consuming clears it
        viewModel.consumeNavigation()
        assertNull(viewModel.pendingNavigation.value)

        // the persisted resolution stays Resolved, but returning to the
        // screen cannot re-emit navigation (the event, not the resolution,
        // drives navigation)
        assertTrue(viewModel.uiState.value.resolution is ScriptureResolution.Resolved)
        assertNull(viewModel.pendingNavigation.value)

        // a fresh resolution produces a fresh event (no stale suppression)
        viewModel.onQueryChange("James 1:6")
        viewModel.resolve()
        advanceUntilIdle()
        assertNotNull(viewModel.pendingNavigation.value)
    }
}
