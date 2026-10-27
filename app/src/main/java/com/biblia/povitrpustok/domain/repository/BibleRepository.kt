package com.biblia.povitrpustok.domain.repository

import com.biblia.povitrpustok.core.result.BibleEditionNotFoundException
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ChapterContent
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.domain.model.Verse

/**
 * Domain-facing Bible repository (implementation plan §14).
 *
 * The UI must not query SQLite directly and must not know whether Bible
 * content comes from one database, many databases, Room, or raw SQLite;
 * this interface is the boundary that hides that detail.
 *
 * Sequential reader navigation uses [getChapter] with a source-order
 * [Chapter.position]; chapter numbers are never used for lookup because
 * the corpus contains duplicate source chapter numbers (e.g. Job).
 */
interface BibleRepository {

    suspend fun getBook(bookId: String): BibleBook

    suspend fun getBook(
        bookId: String,
        editionId: String
    ): BibleBook = defaultEdition(editionId).let { getBook(bookId) }

    suspend fun getChapters(bookId: String): List<Chapter>

    suspend fun getChapters(
        bookId: String,
        editionId: String
    ): List<Chapter> = defaultEdition(editionId).let { getChapters(bookId) }

    suspend fun getChapter(
        bookId: String,
        chapterPosition: Int
    ): ChapterContent

    suspend fun getChapter(
        bookId: String,
        chapterPosition: Int,
        editionId: String
    ): ChapterContent = defaultEdition(editionId).let { getChapter(bookId, chapterPosition) }

    suspend fun getIntroduction(bookId: String): List<IntroductionBlock>

    suspend fun getIntroduction(
        bookId: String,
        editionId: String
    ): List<IntroductionBlock> = defaultEdition(editionId).let { getIntroduction(bookId) }

    suspend fun getVerse(
        bookId: String,
        chapterId: Long,
        verseStart: Int
    ): Verse?

    suspend fun getVerse(
        bookId: String,
        chapterId: Long,
        verseStart: Int,
        editionId: String
    ): Verse? = defaultEdition(editionId).let { getVerse(bookId, chapterId, verseStart) }

    /**
     * Source records whose effective verse range covers [verse] (REF-01).
     *
     * Production resolves combined source ranges (e.g. a record starting at
     * 11 covering verse 12). More than one result means duplicate source
     * verse numbers: callers must surface ambiguity rather than pick one.
     */
    suspend fun getVersesCovering(
        bookId: String,
        chapterId: Long,
        verse: Int
    ): List<Verse>

    suspend fun getVersesCovering(
        bookId: String,
        chapterId: Long,
        verse: Int,
        editionId: String
    ): List<Verse> = defaultEdition(editionId).let {
        getVersesCovering(bookId, chapterId, verse)
    }

    private fun defaultEdition(editionId: String) {
        if (editionId != BibleEditions.KONKANI_ID) {
            throw BibleEditionNotFoundException(editionId)
        }
    }
}
