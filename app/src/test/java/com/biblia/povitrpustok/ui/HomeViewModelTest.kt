package com.biblia.povitrpustok.ui

import com.biblia.povitrpustok.feature.reader.FakeBookmarkRepository
import com.biblia.povitrpustok.testutil.FakeBookNameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Home "Continue reading" state (F-11): the card must carry a resolved book
 * name (F-12) so it says where it goes, and a stale book must be cleared
 * rather than crashing Home.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun no_stored_reading_state_hides_the_card() = runTest(dispatcher.scheduler) {
        val viewModel = HomeViewModel(
            bookmarkRepository = FakeBookmarkRepository(),
            bookNameRepository = FakeBookNameRepository()
        )

        advanceUntilIdle()

        assertNull(viewModel.uiState.value.continueReading)
    }

    @Test
    fun a_stored_location_exposes_its_resolved_book_name() = runTest(dispatcher.scheduler) {
        val bookmarks = FakeBookmarkRepository()
        bookmarks.updateReadingState(bookId = "Utpoti", chapterPosition = 3)
        val viewModel = HomeViewModel(
            bookmarkRepository = bookmarks,
            bookNameRepository = FakeBookNameRepository(mapOf("Utpoti" to "Utpoti Custom"))
        )

        advanceUntilIdle()

        val card = viewModel.uiState.value.continueReading
        assertNotNull(card)
        assertEquals("Utpoti Custom", card?.bookName)
        assertEquals("Utpoti", card?.reading?.bookId)
        assertEquals(3, card?.reading?.chapterPosition ?: -1)
    }

    @Test
    fun a_book_outside_the_catalog_is_cleared_and_hidden() = runTest(dispatcher.scheduler) {
        val bookmarks = FakeBookmarkRepository()
        bookmarks.updateReadingState(bookId = "not-a-book", chapterPosition = 1)
        val viewModel = HomeViewModel(
            bookmarkRepository = bookmarks,
            bookNameRepository = FakeBookNameRepository()
        )

        advanceUntilIdle()

        assertNull(viewModel.uiState.value.continueReading)
    }
}
