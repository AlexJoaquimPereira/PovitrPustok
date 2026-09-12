package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.core.database.BibleDatabaseProvider
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.SearchResult
import com.biblia.povitrpustok.domain.repository.BibleSearchRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-database [BibleSearchRepository] (implementation plan §15, §23).
 *
 * Strategy: the corpus is one SQLite database per book, so a corpus-wide
 * search runs one FTS5 MATCH query per book through the existing lazy
 * [BibleDatabaseProvider] (handles are cached; nothing is opened at
 * startup, and verses are never loaded into Kotlin for string matching).
 * Results are merged and ordered deterministically: BibleCatalog position,
 * then chapter.position, then verseStart, then verseId, then a global
 * limit is applied.
 */
@Singleton
class BibleSearchRepositoryImpl @Inject constructor(
    private val provider: BibleDatabaseProvider
) : BibleSearchRepository {

    override suspend fun search(
        query: String,
        bookIds: List<String>?,
        limit: Int
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext emptyList()
        }

        val books = if (bookIds == null) {
            BibleCatalog.books
        } else {
            val wanted = bookIds.toSet()
            BibleCatalog.books.filter { it.id in wanted }
        }

        val results = ArrayList<SearchResult>(limit * 2)
        for (book in books) {
            val hits = provider.openBook(book.id).searchVerses(trimmed, limit)
            for (hit in hits) {
                results.add(
                    SearchResult(
                        bookId = book.id,
                        chapterId = hit.chapterId,
                        chapterNumber = hit.chapterNumber,
                        chapterPosition = hit.chapterPosition,
                        verseId = hit.verseId,
                        verseStart = hit.verseStart,
                        verseEnd = hit.verseEnd,
                        snippet = SnippetBuilder.build(hit.plainText, trimmed)
                    )
                )
            }
        }

        val bookOrder = books.associate { it.id to it.position }
        results
            .sortedWith(
                compareBy(
                    { bookOrder[it.bookId] ?: Int.MAX_VALUE },
                    { it.chapterPosition },
                    { it.verseStart },
                    { it.verseId }
                )
            )
            .take(limit)
    }
}
