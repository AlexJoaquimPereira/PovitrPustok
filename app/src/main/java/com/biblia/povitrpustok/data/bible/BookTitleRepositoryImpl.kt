package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.core.database.BibleDatabaseProvider
import com.biblia.povitrpustok.core.html.BibleHtmlDocument
import com.biblia.povitrpustok.core.html.BibleHtmlNode
import com.biblia.povitrpustok.core.html.BibleHtmlParser
import com.biblia.povitrpustok.core.html.BibleHtmlRenderer
import com.biblia.povitrpustok.core.result.BibleDatabaseAccessException
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.repository.BookTitleRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [BookTitleRepository] over the per-book databases (corrective phase
 * Issue 1; BOOKS-01).
 *
 * The display name comes from the book's own `introduction_blocks` record
 * with type `title` (smallest position) — the HTML title is parsed and
 * rendered to plain text with the existing parser/renderer, so no raw tags
 * are displayed.
 *
 * Caching policy (BOOKS-01): both outcomes are cached — a present title and
 * `ABSENT` (a book with no title block, e.g. 2 Kings). A single [Mutex]
 * serializes corpus scans, so concurrent callers never open the corpus
 * twice and repeat calls open no databases at all.
 */
@Singleton
class BookTitleRepositoryImpl @Inject constructor(
    private val provider: BibleDatabaseProvider
) : BookTitleRepository {

    /** Distinguishes "no title in the corpus" from "not loaded yet". */
    private sealed interface Lookup {
        data class Present(val title: String) : Lookup
        data object Absent : Lookup
    }

    private val cache = ConcurrentHashMap<String, Lookup>()
    private val scanMutex = Mutex()

    override suspend fun getDisplayName(bookId: String): String? {
        cache[bookId]?.let { return it.titleOrNull() }
        return withContext(Dispatchers.IO) {
            scanMutex.withLock {
                cache[bookId]?.let { return@withLock it.titleOrNull() }
                val lookup = loadLookup(bookId)
                cache[bookId] = lookup
                lookup.titleOrNull()
            }
        }
    }

    override suspend fun loadAllDisplayNames(): Map<String, String> = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            // Only scan books that have never been looked up. Absent titles
            // are cached, so this is a true one-time corpus scan (BOOKS-01).
            //
            // F-03: a single corrupt/missing book must not abort the whole
            // scan. Its failure is isolated (the book is simply omitted from
            // the returned map, so the UI falls back to the catalog name)
            // and it is not cached, so Retry can attempt it again. If every
            // attempted book fails, the failure is catastrophic and surfaces
            // as a BibleDataException for the UI banner.
            var attempted = 0
            var failed = 0
            var lastFailure: Exception? = null
            BibleCatalog.books.forEach { book ->
                if (!cache.containsKey(book.id)) {
                    attempted++
                    try {
                        cache[book.id] = loadLookup(book.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                        lastFailure = e
                    }
                }
            }
            if (attempted > 0 && failed == attempted) {
                throw BibleDatabaseAccessException(
                    "Book titles could not be loaded for any book",
                    lastFailure
                )
            }
            cache.mapNotNull { (id, lookup) -> lookup.titleOrNull()?.let { id to it } }.toMap()
        }
    }

    private suspend fun loadLookup(bookId: String): Lookup {
        val blocks = provider.openBook(bookId).getIntroductionBlocks(bookId)
        val title = blocks
            .filter { it.type == TITLE_TYPE }
            .minByOrNull { it.position }
            ?.html
        return if (title == null) Lookup.Absent else Lookup.Present(renderTitle(title))
    }

    private fun Lookup.titleOrNull(): String? = (this as? Lookup.Present)?.title

    private fun renderTitle(html: String): String {
        val document = BibleHtmlParser().parse(html)
        // The title block may contain additional content after the title
        // heading (e.g. Psalms block 1 ends with an <h3> "Prostavna").
        // Only the first heading (the book title) is used as the name;
        // the rest is metadata, not part of the book name (Issue 2).
        val titleNode = document.children.firstOrNull { it is BibleHtmlNode.Heading }
            ?: return BookTitleNormalizer.normalizeTitleText(
                BibleHtmlRenderer.render(document).text
            )
        val titleText = BibleHtmlRenderer.render(
            BibleHtmlDocument(listOf(titleNode), emptyList())
        ).text
        return BookTitleNormalizer.normalizeTitleText(titleText)
    }

    private companion object {
        const val TITLE_TYPE = "title"
    }
}
