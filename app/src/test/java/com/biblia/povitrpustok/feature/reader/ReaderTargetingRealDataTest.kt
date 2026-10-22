package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.testutil.FakeBookNameRepository

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.data.bible.BibleRepositoryImpl
import com.biblia.povitrpustok.domain.repository.BibleRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Reader targeting over the real corpus (Phase 8 §19): a real verse id from
 * James 1 targets that exact Verse; a bogus id never crashes.
 */
@RunWith(RobolectricTestRunner::class)
class ReaderTargetingRealDataTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var provider: AssetBibleDatabaseProvider
    private lateinit var repository: BibleRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        provider = AssetBibleDatabaseProvider(context)
        repository = BibleRepositoryImpl(provider)
    }

    @After
    fun tearDown() {
        provider.close()
    }

    private fun settled(viewModel: ReaderViewModel): ReaderUiState {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val state = viewModel.uiState.value
            if (!state.isLoading && (state.chapter != null || state.errorMessage != null)) {
                return state
            }
            Thread.sleep(10)
        }
        error("timed out waiting for reader state")
    }

    @Test
    fun real_verse_id_targets_the_exact_verse() {
        val firstVerse = runBlocking { repository.getChapter("jakob", 1).verses.first() }

        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())
        viewModel.load("jakob", 1, targetVerseId = firstVerse.id)
        val state = settled(viewModel)

        assertEquals(firstVerse.id, state.targetVerseId)
        val target = state.verses.first { it.id == state.targetVerseId }
        assertEquals(firstVerse.id, target.id)
    }

    @Test
    fun bogus_verse_id_never_crashes_and_renders_normally() {
        val viewModel = ReaderViewModel(repository, FakeBookmarkRepository(), FakeBookNameRepository())
        viewModel.load("jakob", 1, targetVerseId = 9_999_999L)
        val state = settled(viewModel)

        assertTrue("bogus target must be flagged", state.targetMissing)
        assertNull(state.targetVerseId)
        assertTrue("chapter must still render", state.verses.isNotEmpty())
        assertNull(state.errorMessage)
    }
}
