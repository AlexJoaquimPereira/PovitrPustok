package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.core.database.BibleDatabaseProvider
import com.biblia.povitrpustok.core.database.BibleVerseHit
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCanonicalOrder
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
 *
 * Ranking contract (SEARCH-01):
 *  - within a book, hits are ordered by FTS5 relevance (`rank`/BM25), then
 *    deterministically by chapter position, verse start, verse id;
 *  - per-book scores are not comparable across independent indexes, so the
 *    books are merged by *rank position*: all rank-1 hits first, then all
 *    rank-2 hits, and so on, with books in canonical order;
 *  - the global limit is applied after that merge, so the alphabetically
 *    first book can no longer monopolize the result set;
 *  - snippets are built only for the returned rows.
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
        if (trimmed.isEmpty() || limit <= 0) {
            return@withContext emptyList()
        }

        val requested = if (bookIds == null) {
            BibleCatalog.books
        } else {
            val wanted = bookIds.toSet()
            BibleCatalog.books.filter { it.id in wanted }
        }
        // Canonical book order is the deterministic tie-breaker for equal ranks.
        val books = requested.sortedBy { BibleCanonicalOrder.position(it.id) ?: Int.MAX_VALUE }

        val perBook = forEachBook(books) { book ->
            provider.openBook(book.id).searchVerses(trimmed, limit)
        }

        mergeByRank(perBook, limit).map { (book, hit) ->
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
        }
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

/**
 * Merges per-book hits (each already relevance-ordered) by rank position
 * (SEARCH-01). [perBook] must be in canonical book order; the merged list is
 * bounded by [limit]. Pure and FTS-independent so it is unit-testable.
 */
internal fun mergeByRank(
    perBook: List<Pair<BibleBook, List<BibleVerseHit>>>,
    limit: Int
): List<Pair<BibleBook, BibleVerseHit>> {
    if (limit <= 0) return emptyList()
    val merged = ArrayList<Pair<BibleBook, BibleVerseHit>>(limit)
    val maxRank = perBook.maxOfOrNull { it.second.size } ?: 0
    for (rank in 0 until maxRank) {
        for ((book, hits) in perBook) {
            val hit = hits.getOrNull(rank) ?: continue
            merged += book to hit
            if (merged.size == limit) return merged
        }
    }
    return merged
}
