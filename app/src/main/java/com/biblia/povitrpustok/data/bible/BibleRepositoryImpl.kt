package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.core.database.BibleDatabaseProvider
import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import com.biblia.povitrpustok.core.result.BibleChapterNotFoundException
import com.biblia.povitrpustok.core.result.BibleEditionNotFoundException
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.BibleEditions
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
        getBook(bookId, BibleEditions.KONKANI_ID)

    override suspend fun getBook(bookId: String, editionId: String): BibleBook {
        requireEdition(editionId)
        return BibleCatalog.byId(bookId, editionId) ?: throw BibleBookNotFoundException(bookId)
    }

    override suspend fun getChapters(bookId: String): List<Chapter> =
        getChapters(bookId, BibleEditions.KONKANI_ID)

    override suspend fun getChapters(bookId: String, editionId: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            provider.openBook(bookId, editionId).getChapters(bookId)
        }

    override suspend fun getChapter(
        bookId: String,
        chapterPosition: Int
    ): ChapterContent = getChapter(bookId, chapterPosition, BibleEditions.KONKANI_ID)

    override suspend fun getChapter(
        bookId: String,
        chapterPosition: Int,
        editionId: String
    ): ChapterContent = withContext(Dispatchers.IO) {
        val database = provider.openBook(bookId, editionId)
        val chapter = database.getChapter(bookId, chapterPosition)
            ?: throw BibleChapterNotFoundException(bookId, chapterPosition)
        ChapterContent(
            chapter = chapter,
            headings = database.getHeadings(chapter.id),
            verses = database.getVerses(chapter.id)
        )
    }

    override suspend fun getIntroduction(bookId: String): List<IntroductionBlock> =
        getIntroduction(bookId, BibleEditions.KONKANI_ID)

    override suspend fun getIntroduction(
        bookId: String,
        editionId: String
    ): List<IntroductionBlock> =
        withContext(Dispatchers.IO) {
            provider.openBook(bookId, editionId).getIntroductionBlocks(bookId)
        }

    override suspend fun getVerse(
        bookId: String,
        chapterId: Long,
        verseStart: Int
    ): Verse? = getVerse(bookId, chapterId, verseStart, BibleEditions.KONKANI_ID)

    override suspend fun getVerse(
        bookId: String,
        chapterId: Long,
        verseStart: Int,
        editionId: String
    ): Verse? = withContext(Dispatchers.IO) {
        provider.openBook(bookId, editionId).getVerse(chapterId, verseStart)
    }

    override suspend fun getVersesCovering(
        bookId: String,
        chapterId: Long,
        verse: Int
    ): List<Verse> = getVersesCovering(bookId, chapterId, verse, BibleEditions.KONKANI_ID)

    override suspend fun getVersesCovering(
        bookId: String,
        chapterId: Long,
        verse: Int,
        editionId: String
    ): List<Verse> = withContext(Dispatchers.IO) {
        provider.openBook(bookId, editionId).getVersesCovering(chapterId, verse)
    }

    private fun requireEdition(editionId: String) {
        if (BibleEditions.byId(editionId) == null) {
            throw BibleEditionNotFoundException(editionId)
        }
    }
}
