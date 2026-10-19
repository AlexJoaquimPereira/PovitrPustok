package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import com.biblia.povitrpustok.core.result.BibleChapterNotFoundException
import com.biblia.povitrpustok.domain.model.Testament
import com.biblia.povitrpustok.domain.repository.BibleRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End-to-end Robolectric tests for BibleRepository over the real
 * staged databases (implementation plan §14).
 */
@RunWith(RobolectricTestRunner::class)
class BibleRepositoryTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var repository: BibleRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = BibleRepositoryImpl(AssetBibleDatabaseProvider(context))
    }

    @Test
    fun getBook_returns_catalog_metadata() = runBlocking {
        val book = repository.getBook("jakob")
        assertEquals("James", book.englishName)
        assertEquals("Jakobachem", book.displayName)
        assertEquals("bible/jakob.sqlite", book.databaseAsset)
        assertEquals(Testament.NEW_TESTAMENT, book.testament)
        assertEquals(53, book.position)
    }

    @Test
    fun getBook_with_unknown_id_throws() = runBlocking {
        try {
            repository.getBook("not-a-book")
            fail("expected BibleBookNotFoundException")
        } catch (expected: BibleBookNotFoundException) {
            // expected
        }
    }

    @Test
    fun getChapter_assembles_ChapterContent() = runBlocking {
        val content = repository.getChapter("jakob", 1)
        assertEquals(1, content.chapter.position)
        assertEquals("jakob", content.chapter.bookId)
        assertTrue(content.verses.isNotEmpty())
        assertEquals(content.headings.size, content.headings.map { it.id }.toSet().size)
    }

    @Test
    fun getChapter_with_missing_position_throws() = runBlocking {
        try {
            repository.getChapter("jakob", 999)
            fail("expected BibleChapterNotFoundException")
        } catch (expected: BibleChapterNotFoundException) {
            // expected
        }
    }

    @Test
    fun getChapters_returns_source_order() = runBlocking {
        val chapters = repository.getChapters("jakob")
        assertEquals(listOf(1, 2, 3, 4, 5), chapters.map { it.position })
        assertEquals(5, chapters.map { it.id }.toSet().size)
    }

    @Test
    fun getVerse_returns_verse_by_chapter_id_and_verse_start() = runBlocking {
        val content = repository.getChapter("jakob", 1)
        val verse = content.verses.first()
        val found = repository.getVerse("jakob", content.chapter.id, verse.verseStart)
        assertNotNull(found)
        assertEquals(verse.id, found!!.id)
        assertEquals(verse.html, found.html)
    }

    @Test
    fun getVerse_with_unknown_verse_start_returns_null() = runBlocking {
        val content = repository.getChapter("jakob", 1)
        val missing = repository.getVerse("jakob", content.chapter.id, 9999)
        assertEquals(null, missing)
    }

    @Test
    fun getIntroduction_returns_ordered_blocks() = runBlocking {
        val blocks = repository.getIntroduction("jakob")
        assertTrue(blocks.isNotEmpty())
        assertEquals(blocks.map { it.position }.sorted(), blocks.map { it.position })
        assertEquals(blocks.size, blocks.map { it.id }.toSet().size)
    }

    @Test
    fun job_duplicate_positions_load_through_repository() = runBlocking {
        val chapters = repository.getChapters("Jobachem_Pustok")
        val duplicated = chapters.groupBy { it.number }.filterValues { it.size > 1 }
        assertTrue(duplicated.isNotEmpty())
        duplicated.values.flatten().forEach { record ->
            val content = repository.getChapter("Jobachem_Pustok", record.position)
            assertEquals(record.id, content.chapter.id)
            assertEquals(record.position, content.chapter.position)
            assertTrue(content.verses.isNotEmpty())
        }
    }
}
