package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.core.database.BibleDatabaseProvider
import com.biblia.povitrpustok.core.html.BibleHtmlDocument
import com.biblia.povitrpustok.core.html.BibleHtmlNode
import com.biblia.povitrpustok.core.html.BibleHtmlParser
import com.biblia.povitrpustok.core.html.BibleHtmlRenderer
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.repository.BookTitleRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [BookTitleRepository] over the per-book databases (corrective phase
 * Issue 1).
 *
 * The display name comes from the book's own `introduction_blocks` record
 * with type `title` (smallest position) — the HTML title is parsed and
 * rendered to plain text with the existing parser/renderer, so no raw tags
 * are displayed. Titles are loaded once per process and cached; the Books
 * screen never opens all 73 databases per recomposition, and chapters/
 * verses are never loaded for metadata.
 */
@Singleton
class BookTitleRepositoryImpl @Inject constructor(
    private val provider: BibleDatabaseProvider
) : BookTitleRepository {

    private val cache = ConcurrentHashMap<String, String>()

    override suspend fun getDisplayName(bookId: String): String? =
        cache[bookId] ?: withContext(Dispatchers.IO) {
            loadTitle(bookId)?.also { cache[bookId] = it }
        }

    override suspend fun loadAllDisplayNames(): Map<String, String> = withContext(Dispatchers.IO) {
        if (cache.size < BibleCatalog.books.size) {
            BibleCatalog.books.forEach { book ->
                loadTitle(book.id)?.let { cache[book.id] = it }
            }
        }
        cache.toMap()
    }

    private suspend fun loadTitle(bookId: String): String? {
        val blocks = provider.openBook(bookId).getIntroductionBlocks(bookId)
        val title = blocks
            .filter { it.type == TITLE_TYPE }
            .minByOrNull { it.position }
            ?.html
            ?: return null
        return renderTitle(title)
    }

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
