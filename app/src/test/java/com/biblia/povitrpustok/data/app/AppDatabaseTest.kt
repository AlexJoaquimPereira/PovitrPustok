package com.biblia.povitrpustok.data.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room application-database tests (Phase 9 §23): insert, duplicate
 * suppression, delete, Flow observation, persistence across database
 * recreation, and reading-state persistence. The mutable database is a
 * separate file — Bible assets are never touched here.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    private fun entity(
        id: String = UUID.randomUUID().toString(),
        verseStart: Int = 5,
        chapterPosition: Int = 1,
        bookId: String = "jakob"
    ) =
        BookmarkEntity(
            id = id,
            bookId = bookId,
            chapterPosition = chapterPosition,
            verseStart = verseStart,
            verseEnd = verseStart,
            startOffset = 0,
            endOffset = 0,
            chapterNumber = 1,
            createdAt = 1000L,
            updatedAt = 1000L
        )

    @Test
    fun insert_persists_a_bookmark() = runBlocking {
        val db = AppDatabase.create(context)
        val dao = db.bookmarkDao()
        val id = dao.insert(entity())
        assertTrue("insert must return a positive row id", id > 0)
        val bookmarks = dao.observeAll().first()
        assertEquals(1, bookmarks.size)
        assertEquals("jakob", bookmarks[0].bookId)
        assertEquals(5, bookmarks[0].verseStart)
        db.close()
    }

    @Test
    fun duplicate_same_location_is_ignored() = runBlocking {
        val db = AppDatabase.create(context)
        val dao = db.bookmarkDao()
        val first = dao.insert(entity())
        val second = dao.insert(entity(id = UUID.randomUUID().toString()))
        assertTrue(first > 0)
        assertEquals("duplicate insert must be ignored", -1L, second)
        assertEquals(1, dao.observeAll().first().size)
        db.close()
    }

    @Test
    fun different_chapter_position_is_a_distinct_bookmark() = runBlocking {
        val db = AppDatabase.create(context)
        val dao = db.bookmarkDao()
        // Job duplicate chapter records: same chapterNumber, distinct position
        dao.insert(entity(chapterPosition = 26))
        dao.insert(entity(chapterPosition = 27))
        assertEquals(2, dao.observeAll().first().size)
        db.close()
    }

    @Test
    fun delete_removes_a_bookmark() = runBlocking {
        val db = AppDatabase.create(context)
        val dao = db.bookmarkDao()
        val entity = entity()
        dao.insert(entity)
        dao.deleteById(entity.id)
        assertTrue(dao.observeAll().first().isEmpty())
        db.close()
    }

    @Test
    fun observe_flow_emits_changes() = runBlocking {
        val db = AppDatabase.create(context)
        val dao = db.bookmarkDao()
        assertEquals(0, dao.observeAll().first().size)

        dao.insert(entity(verseStart = 1))
        assertEquals(1, dao.observeAll().first().size)

        dao.insert(entity(verseStart = 2))
        assertEquals(2, dao.observeAll().first().size)

        dao.deleteByAnchor(
            bookId = "jakob",
            chapterPosition = 1,
            verseStart = 1,
            verseEnd = 1,
            startOffset = 0,
            endOffset = 0
        )
        assertEquals(1, dao.observeAll().first().size)
        db.close()
    }

    @Test
    fun bookmark_persists_across_database_recreation() = runBlocking {
        val first = AppDatabase.create(context)
        first.bookmarkDao().insert(entity(verseStart = 42))
        first.close()

        // Reopen the same file — the bookmark must survive.
        val second = AppDatabase.create(context)
        val bookmarks = second.bookmarkDao().observeAll().first()
        assertEquals(1, bookmarks.size)
        assertEquals(42, bookmarks[0].verseStart)
        second.close()
    }

    @Test
    fun reading_state_persists_and_is_single_row() = runBlocking {
        val db = AppDatabase.create(context)
        val dao = db.readingStateDao()
        assertNull(dao.get())

        dao.upsert(ReadingStateEntity(bookId = "jakob", chapterPosition = 3, updatedAt = 1L))
        val state = dao.get()
        assertNotNull(state)
        assertEquals("jakob", state!!.bookId)
        assertEquals(3, state.chapterPosition)

        dao.upsert(ReadingStateEntity(bookId = "Utpoti", chapterPosition = 17, updatedAt = 2L))
        val updated = dao.get()
        assertEquals("single row: last location wins", "Utpoti", updated!!.bookId)
        assertEquals(17, updated.chapterPosition)
        db.close()
    }

    @Test
    fun reading_state_persists_across_database_recreation() = runBlocking {
        val first = AppDatabase.create(context)
        first.readingStateDao().upsert(ReadingStateEntity(bookId = "Esther", chapterPosition = 5, updatedAt = 1L))
        first.close()

        val second = AppDatabase.create(context)
        val state = second.readingStateDao().get()
        assertEquals("Esther", state!!.bookId)
        assertEquals(5, state.chapterPosition)
        second.close()
    }
}
