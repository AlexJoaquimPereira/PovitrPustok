package com.biblia.povitrpustok.domain.repository

import com.biblia.povitrpustok.domain.model.BibleBook
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

    suspend fun getChapters(bookId: String): List<Chapter>

    suspend fun getChapter(
        bookId: String,
        chapterPosition: Int
    ): ChapterContent

    suspend fun getIntroduction(bookId: String): List<IntroductionBlock>

    suspend fun getVerse(
        bookId: String,
        chapterId: Long,
        verseStart: Int
    ): Verse?
}
