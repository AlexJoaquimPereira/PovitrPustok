package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.core.database.BibleDatabaseProvider
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.SearchResult
import com.biblia.povitrpustok.domain.repository.BibleSearchRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
 *
 * Cancellation is checked before each book (DB-05), so a superseded search
 * stops opening/querying the remaining databases promptly.
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
        val perBook = forEachBook(books) { book ->
            provider.openBook(book.id).searchVerses(trimmed, limit)
        }
        for ((book, hits) in perBook) {
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

    /**
     * Runs [block] for each book with a cancellation checkpoint before every
     * iteration (DB-05). Extracted so the cancellation contract is testable
     * deterministically without an FTS-capable SQLite build.
     */
    internal suspend fun <T> forEachBook(
        books: List<BibleBook>,
        block: suspend (BibleBook) -> T
    ): List<Pair<BibleBook, T>> = buildList {
        for (book in books) {
            currentCoroutineContext().ensureActive()
            add(book to block(book))
        }
    }
}
