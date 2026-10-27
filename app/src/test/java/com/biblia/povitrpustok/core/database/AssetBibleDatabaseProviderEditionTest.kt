package com.biblia.povitrpustok.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import com.biblia.povitrpustok.core.result.BibleEditionNotFoundException
import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** F1-17: provider keys by edition and rejects unknown editions/books. */
@RunWith(RobolectricTestRunner::class)
class AssetBibleDatabaseProviderEditionTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        File(context.filesDir, "bible").deleteRecursively()
    }

    @Test
    fun default_edition_opens_and_unknown_edition_or_book_is_rejected() = runBlocking {
        val provider = AssetBibleDatabaseProvider(context)
        try {
            val database = provider.openBook("jakob", BibleEditions.KONKANI_ID)
            assertTrue(database.hasFtsTable())

            try {
                provider.openBook("jakob", "not-an-edition")
                fail("expected BibleEditionNotFoundException")
            } catch (expected: BibleEditionNotFoundException) {
                // expected
            }

            try {
                provider.openBook("not-a-book", BibleEditions.KONKANI_ID)
                fail("expected BibleBookNotFoundException")
            } catch (expected: BibleBookNotFoundException) {
                // expected
            }
        } finally {
            provider.close()
        }
    }
}
