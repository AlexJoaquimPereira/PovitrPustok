package com.biblia.povitrpustok.data.bible

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.result.BibleEditionNotFoundException
import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.BibleSearchRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** F1-16/F1-17: repository and search default to Konkani and reject unknown editions. */
@RunWith(RobolectricTestRunner::class)
class BibleRepositoryEditionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val provider = AssetBibleDatabaseProvider(context)
    private val repository: BibleRepository = BibleRepositoryImpl(provider)
    private val search: BibleSearchRepository = BibleSearchRepositoryImpl(provider)

    @After
    fun tearDown() {
        provider.close()
    }

    @Test
    fun book_metadata_is_edition_scoped() = runBlocking {
        val book = repository.getBook("jakob", BibleEditions.KONKANI_ID)
        assertEquals("James", book.englishName)
        try {
            repository.getBook("jakob", "not-an-edition")
            fail("expected BibleEditionNotFoundException")
        } catch (expected: BibleEditionNotFoundException) {
            // expected
        }
    }

    @Test
    fun search_rejects_an_unknown_edition() = runBlocking {
        try {
            search.searchWithDiagnostics("faith", editionId = "not-an-edition")
            fail("expected BibleEditionNotFoundException")
        } catch (expected: BibleEditionNotFoundException) {
            // expected
        }
    }
}
