package com.biblia.povitrpustok.feature.bookmarks

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.data.app.AppDatabase
import com.biblia.povitrpustok.data.app.BookmarkRepositoryImpl
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Bookmarks screen ViewModel (Phase 9 §17): observes the domain bookmark
 * list and deletes through the repository.
 */
@RunWith(RobolectricTestRunner::class)
class BookmarksViewModelTest {

    private lateinit var context: Context
    private lateinit var repository: BookmarkRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = BookmarkRepositoryImpl(AppDatabase.create(context))
    }

    @After
    fun tearDown() {
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    private fun awaitCondition(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("timed out waiting for condition")
    }

    @Test
    fun view_model_observes_and_deletes_bookmarks() = runBlocking {
        repository.addBookmark(
            bookId = "jakob", chapterId = 1, chapterPosition = 1, chapterNumber = 1,
            verseId = 5, verseStart = 5, verseEnd = 5
        )

        val viewModel = BookmarksViewModel(repository)
        awaitCondition { viewModel.uiState.value.bookmarks.size == 1 }

        val state = viewModel.uiState.value
        assertEquals(1, state.bookmarks.size)
        assertEquals("jakob", state.bookmarks[0].bookId)
        assertEquals(5L, state.bookmarks[0].verseId)
        assertNotNull(state.bookmarks[0].toReaderDestination())

        viewModel.removeBookmark(state.bookmarks[0].id)
        awaitCondition { viewModel.uiState.value.bookmarks.isEmpty() }
        assertEquals(0, repository.observeBookmarks().first().size)
    }
}
