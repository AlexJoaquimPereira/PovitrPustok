package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.core.database.BibleDatabaseProvider
import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import com.biblia.povitrpustok.core.result.BibleChapterNotFoundException
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ChapterContent
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.domain.model.Verse
import com.biblia.povitrpustok.domain.repository.BibleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [BibleRepository] backed by the read-only per-book databases exposed
 * through [BibleDatabaseProvider] (implementation plan §2, §14).
 *
 * All database work runs on [Dispatchers.IO]; chapter retrieval fetches
 * only the requested chapter, its headings, and its verses — never the
 * whole book or corpus.
 */
@Singleton
class BibleRepositoryImpl @Inject constructor(
    private val provider: BibleDatabaseProvider
) : BibleRepository {

    override suspend fun getBook(bookId: String): BibleBook =
        BibleCatalog.byId(bookId) ?: throw BibleBookNotFoundException(bookId)

    override suspend fun getChapters(bookId: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            provider.openBook(bookId).getChapters(bookId)
        }

    override suspend fun getChapter(
        bookId: String,
        chapterPosition: Int
    ): ChapterContent = withContext(Dispatchers.IO) {
        val database = provider.openBook(bookId)
        val chapter = database.getChapter(bookId, chapterPosition)
            ?: throw BibleChapterNotFoundException(bookId, chapterPosition)
        ChapterContent(
            chapter = chapter,
            headings = database.getHeadings(chapter.id),
            verses = database.getVerses(chapter.id)
        )
    }

    override suspend fun getIntroduction(bookId: String): List<IntroductionBlock> =
        withContext(Dispatchers.IO) {
            provider.openBook(bookId).getIntroductionBlocks(bookId)
        }

    override suspend fun getVerse(
        bookId: String,
        chapterId: Long,
        verseStart: Int
    ): Verse? = withContext(Dispatchers.IO) {
        provider.openBook(bookId).getVerse(chapterId, verseStart)
    }

    override suspend fun getVersesCovering(
        bookId: String,
        chapterId: Long,
        verse: Int
    ): List<Verse> = withContext(Dispatchers.IO) {
        provider.openBook(bookId).getVersesCovering(chapterId, verse)
    }
}
