package com.biblia.povitrpustok.feature.reader

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.database.BibleAssetSource
import com.biblia.povitrpustok.data.app.AppDatabase
import com.biblia.povitrpustok.data.app.BookmarkRepositoryImpl
import com.biblia.povitrpustok.data.bible.BibleRepositoryImpl
import com.biblia.povitrpustok.domain.model.Bookmark
import com.biblia.povitrpustok.domain.model.ReaderPassageSpec
import com.biblia.povitrpustok.domain.model.VerseAnchor
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import com.biblia.povitrpustok.testutil.FakeBookNameRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * TD-01: a bookmark keyed by [VerseAnchor] must still resolve to the same
 * book + chapter position + verse start after the corpus row ids change.
 *
 * This is a corpus-free regression fixture. Two synthetic SQLite files mirror
 * the subset of the imported schema the reader uses (`books`, `chapters`,
 * `verses`, `headings`, `introduction_blocks`). Corpus A and corpus B differ
 * in every `chapters.id` / `verses.id` while keeping `chapters.position`,
 * `chapters.number`, `verses.verse_start`, and `verses.verse_end` stable.
 *
 * Why this is equivalent to a full per-book corpus rebuild: the production
 * path only reads `books.id`, `chapters.position`, and the verse range; the
 * provider validates the same required tables and `PRAGMA quick_check`, then
 * the real [BibleRepositoryImpl], [ReaderViewModel], and
 * [BookmarkRepositoryImpl] run unchanged. `verses_fts` is a plain table here
 * because the anchor path never searches and FTS5 is absent on some JVM and
 * device SQLite builds; the provider only requires the table to exist.
 *
 * The test deliberately does not use [com.biblia.povitrpustok.testutil.CorpusAvailabilityRule],
 * so it runs under `-PskipCorpusTests`.
 */
@RunWith(RobolectricTestRunner::class)
class RenumberedCorpusAnchorTest {

    private lateinit var context: Context
    private lateinit var appDatabase: AppDatabase
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var corpusA: File
    private lateinit var corpusB: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        appDatabase = AppDatabase.create(context)
        bookmarkRepository = BookmarkRepositoryImpl(appDatabase)

        corpusA = File(context.cacheDir, "renumbered-a.sqlite")
        corpusB = File(context.cacheDir, "renumbered-b.sqlite")
        writeCorpus(
            file = corpusA,
            chapterId = { position -> 10L + position },
            verseId = { position, verseStart -> 1_000L + position * 100 + verseStart }
        )
        writeCorpus(
            file = corpusB,
            chapterId = { position -> 900L + position },
            verseId = { position, verseStart -> 9_000L + position * 100 + verseStart }
        )
    }

    @After
    fun tearDown() {
        appDatabase.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        File(context.filesDir, BIBLE_DIRECTORY).deleteRecursively()
        corpusA.delete()
        corpusB.delete()
    }

    @Test
    fun verse_anchor_resolves_to_the_same_location_after_row_ids_change() {
        val providerA = providerFor(corpusA)
        val readerA = readerFor(providerA)
        // Corpus A: position 1, verse_start 2, row id 1102.
        readerA.load(BOOK_ID, chapterPosition = 1, targetVerseId = 1102L)
        awaitLoaded(readerA)
        readerA.toggleBookmarkFor(1102L)
        awaitCondition { readerA.uiState.value.bookmarkedVerseIds.contains(1102L) }
        providerA.close()
        clearPreparedCorpus()

        val saved = singleBookmark()
        assertEquals(VerseAnchor(BOOK_ID, chapterPosition = 1, verseStart = 2, verseEnd = 2), saved.anchor)

        val providerB = providerFor(corpusB)
        try {
            val readerB = readerFor(providerB)
            // The UI opens a bookmark through Bookmark.toReaderDestination():
            // book + stable chapter position + stable verse number.
            val destination = saved.toReaderDestination()
            readerB.load(
                bookId = destination.bookId,
                chapterPosition = destination.chapterPosition,
                targetVerseNumber = destination.targetVerseNumber
            )
            awaitCondition {
                val state = readerB.uiState.value
                !state.isLoading && state.chapter != null && state.bookmarkedVerseIds.isNotEmpty()
            }

            val state = readerB.uiState.value
            assertEquals(BOOK_ID, state.book?.id)
            assertEquals("chapter position is the navigation identity", 1, state.chapter?.position)
            assertFalse("the stable verse target must resolve", state.targetMissing)
            assertEquals("the anchor resolves to the new corpus row id", 9102L, state.targetVerseId)
            assertTrue("corpus B really renumbered the rows", state.verses.none { it.id == 1102L })
            val target = state.verses.first { it.id == state.targetVerseId }
            assertEquals(2, target.verseStart)
            assertEquals(2, target.verseEnd)
            assertTrue(
                "the bookmark must highlight the renumbered verse",
                state.bookmarkedVerseIds.contains(9102L)
            )
            assertTrue(
                "the stored anchor must still match without any corpus lookup",
                runBlocking { bookmarkRepository.isBookmarked(saved.anchor) }
            )
        } finally {
            providerB.close()
        }
    }

    @Test
    fun range_bookmark_resolves_after_row_ids_change() {
        val providerA = providerFor(corpusA)
        val readerA = readerFor(providerA)
        // Corpus A: position 1, source range 11-12, row id 1111.
        readerA.load(BOOK_ID, chapterPosition = 1, targetVerseId = 1111L)
        awaitLoaded(readerA)
        readerA.toggleBookmarkFor(1111L)
        awaitCondition { readerA.uiState.value.bookmarkedVerseIds.contains(1111L) }
        providerA.close()
        clearPreparedCorpus()

        val saved = singleBookmark()
        assertEquals(VerseAnchor(BOOK_ID, chapterPosition = 1, verseStart = 11, verseEnd = 12), saved.anchor)

        val providerB = providerFor(corpusB)
        try {
            val readerB = readerFor(providerB)
            val destination = saved.toReaderDestination()
            readerB.load(
                bookId = destination.bookId,
                chapterPosition = destination.chapterPosition,
                targetVerseNumber = destination.targetVerseNumber
            )
            awaitCondition {
                val state = readerB.uiState.value
                !state.isLoading && state.chapter != null && state.bookmarkedVerseIds.isNotEmpty()
            }

            val state = readerB.uiState.value
            assertFalse(state.targetMissing)
            assertEquals(9111L, state.targetVerseId)
            val target = state.verses.first { it.id == state.targetVerseId }
            assertEquals("a real source range keeps its end", 11, target.verseStart)
            assertEquals(12, target.verseEnd)
            assertTrue(state.bookmarkedVerseIds.contains(9111L))
        } finally {
            providerB.close()
        }
    }

    @Test
    fun duplicate_source_chapter_numbers_and_cross_chapter_passage_stay_distinct() {
        val providerA = providerFor(corpusA)
        val readerA = readerFor(providerA)
        // Positions 3 and 4 both carry source number 7 (Job-style duplicate).
        readerA.load(BOOK_ID, chapterPosition = 3, targetVerseId = 1301L)
        awaitLoaded(readerA)
        readerA.toggleBookmarkFor(1301L)
        awaitCondition { readerA.uiState.value.bookmarkedVerseIds.contains(1301L) }

        readerA.load(BOOK_ID, chapterPosition = 4, targetVerseId = 1401L)
        awaitLoaded(readerA)
        readerA.toggleBookmarkFor(1401L)
        awaitCondition { readerA.uiState.value.bookmarkedVerseIds.contains(1401L) }
        providerA.close()
        clearPreparedCorpus()

        val saved = runBlocking { bookmarkRepository.observeBookmarks().first() }
        assertEquals(2, saved.size)
        assertEquals("both rows display number 7", setOf(7), saved.map { it.chapterNumber }.toSet())
        assertEquals("positions keep them apart", setOf(3, 4), saved.map { it.anchor.chapterPosition }.toSet())

        val providerB = providerFor(corpusB)
        try {
            val readerB = readerFor(providerB)

            readerB.load(BOOK_ID, chapterPosition = 3, targetVerseNumber = 1)
            awaitCondition {
                val state = readerB.uiState.value
                !state.isLoading && state.chapter != null && state.bookmarkedVerseIds.isNotEmpty()
            }
            assertEquals("only the position-3 bookmark may highlight", setOf(9301L), readerB.uiState.value.bookmarkedVerseIds)

            readerB.load(BOOK_ID, chapterPosition = 4, targetVerseNumber = 1)
            awaitCondition {
                val state = readerB.uiState.value
                !state.isLoading && state.chapter != null && state.bookmarkedVerseIds.isNotEmpty()
            }
            assertEquals("only the position-4 bookmark may highlight", setOf(9401L), readerB.uiState.value.bookmarkedVerseIds)

            // A cross-chapter passage observes both chapters; both anchors
            // must resolve to their own renumbered rows.
            readerB.load(
                bookId = BOOK_ID,
                chapterPosition = 3,
                passages = listOf(
                    ReaderPassageSpec(chapterPosition = 3, verseStart = 1, verseEnd = 1),
                    ReaderPassageSpec(chapterPosition = 4, verseStart = 1, verseEnd = 1)
                )
            )
            awaitCondition {
                val state = readerB.uiState.value
                !state.isLoading &&
                    state.passageChapters.size == 2 &&
                    state.bookmarkedVerseIds.containsAll(listOf(9301L, 9401L))
            }
            assertEquals(2, readerB.uiState.value.passageChapters.size)
        } finally {
            providerB.close()
        }
    }

    private fun providerFor(corpus: File): AssetBibleDatabaseProvider =
        AssetBibleDatabaseProvider(context, BibleAssetSource { corpus.inputStream() })

    private fun readerFor(provider: AssetBibleDatabaseProvider): ReaderViewModel =
        ReaderViewModel(BibleRepositoryImpl(provider), bookmarkRepository, FakeBookNameRepository())

    private fun singleBookmark(): Bookmark =
        runBlocking { bookmarkRepository.observeBookmarks().first() }.single()

    private fun clearPreparedCorpus() {
        File(context.filesDir, BIBLE_DIRECTORY).deleteRecursively()
    }

    private fun awaitLoaded(viewModel: ReaderViewModel) {
        awaitCondition { !viewModel.uiState.value.isLoading && viewModel.uiState.value.chapter != null }
    }

    private fun awaitCondition(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("timed out waiting for condition")
    }

    /**
     * Writes the minimal imported-corpus schema. The ids are supplied by the
     * caller so the two corpora share every stable field and differ only in
     * row identity.
     */
    private fun writeCorpus(
        file: File,
        chapterId: (position: Int) -> Long,
        verseId: (position: Int, verseStart: Int) -> Long
    ) {
        file.parentFile?.mkdirs()
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE books (id TEXT PRIMARY KEY NOT NULL, name TEXT)")
            db.execSQL(
                "CREATE TABLE chapters (" +
                    "id INTEGER PRIMARY KEY NOT NULL, book_id TEXT NOT NULL, " +
                    "number INTEGER NOT NULL, position INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE verses (" +
                    "id INTEGER PRIMARY KEY NOT NULL, chapter_id INTEGER NOT NULL, " +
                    "chapter_number INTEGER NOT NULL, verse_start INTEGER NOT NULL, " +
                    "verse_end INTEGER, text TEXT, text_plain TEXT, position INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE headings (" +
                    "id INTEGER PRIMARY KEY NOT NULL, chapter_id INTEGER NOT NULL, " +
                    "position INTEGER NOT NULL, before_verse INTEGER NOT NULL, html TEXT)"
            )
            db.execSQL(
                "CREATE TABLE introduction_blocks (" +
                    "id INTEGER PRIMARY KEY NOT NULL, book_id TEXT NOT NULL, " +
                    "position INTEGER NOT NULL, type TEXT NOT NULL, html TEXT)"
            )
            // The anchor path never searches; a plain table keeps the fixture
            // independent of FTS5 availability while satisfying the provider.
            db.execSQL("CREATE TABLE verses_fts (text_plain TEXT)")

            db.execSQL("INSERT INTO books (id, name) VALUES ('$BOOK_ID', 'Jakobachem')")
            CHAPTERS.forEach { chapter ->
                db.execSQL(
                    "INSERT INTO chapters (id, book_id, number, position) VALUES " +
                        "(${chapterId(chapter.position)}, '$BOOK_ID', ${chapter.number}, ${chapter.position})"
                )
                VERSES.getValue(chapter.position).forEachIndexed { index, verse ->
                    db.execSQL(
                        "INSERT INTO verses " +
                            "(id, chapter_id, chapter_number, verse_start, verse_end, text, text_plain, position) " +
                            "VALUES (${verseId(chapter.position, verse.start)}, " +
                            "${chapterId(chapter.position)}, ${chapter.number}, ${verse.start}, " +
                            "${verse.end}, 'v${verse.start}', 'v${verse.start}', $index)"
                    )
                }
            }
        }
    }

    private data class ChapterRow(val position: Int, val number: Int)

    private data class VerseRow(val start: Int, val end: Int)

    private companion object {
        const val BOOK_ID = "jakob"
        const val BIBLE_DIRECTORY = "bible"

        val CHAPTERS = listOf(
            ChapterRow(position = 1, number = 1),
            ChapterRow(position = 2, number = 2),
            // Job-style duplicate source chapter number at distinct positions.
            ChapterRow(position = 3, number = 7),
            ChapterRow(position = 4, number = 7)
        )

        val VERSES = mapOf(
            1 to listOf(VerseRow(start = 1, end = 1), VerseRow(start = 2, end = 2), VerseRow(start = 11, end = 12)),
            2 to listOf(VerseRow(start = 1, end = 1)),
            3 to listOf(VerseRow(start = 1, end = 1)),
            4 to listOf(VerseRow(start = 1, end = 1))
        )
    }
}
