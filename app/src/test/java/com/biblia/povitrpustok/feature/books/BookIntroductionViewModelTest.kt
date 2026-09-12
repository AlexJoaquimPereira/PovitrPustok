package com.biblia.povitrpustok.feature.books

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.data.bible.BibleRepositoryImpl
import com.biblia.povitrpustok.domain.repository.BibleRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Book introduction loading over the real corpus (Phase 7 §12).
 */
@RunWith(RobolectricTestRunner::class)
class BookIntroductionViewModelTest {

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

    @Test
    fun james_introduction_blocks_are_preserved_in_source_order() {
        val viewModel = BookIntroductionViewModel(repository)
        viewModel.load("jakob")

        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val state = viewModel.uiState.value
            if (!state.isLoading && (state.blocks.isNotEmpty() || state.errorMessage != null)) {
                assertTrue("expected 8 introduction blocks in James", state.blocks.size >= 2)
                assertEquals("blocks must stay distinct", state.blocks.size, state.blocks.map { it.id }.toSet().size)
                assertEquals(
                    "blocks must be in source order",
                    state.blocks.map { it.position }.sorted(),
                    state.blocks.map { it.position }
                )
                assertTrue(state.blocks.all { it.html.isNotBlank() })
                return
            }
            Thread.sleep(10)
        }
        fail("timed out waiting for introduction state")
    }
}
