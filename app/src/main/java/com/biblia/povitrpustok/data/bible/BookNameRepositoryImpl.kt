package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.BookDisplayName
import com.biblia.povitrpustok.domain.repository.BookNameRepository
import com.biblia.povitrpustok.domain.repository.BookTitleRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [BookNameRepository] over [BookTitleRepository] plus the static catalog
 * (F-12).
 *
 * One display-name source for every screen: the corpus title wins, the
 * catalog display name is the fallback, and the raw id is the last resort.
 * Names are returned display-ready (Capitalize-Case), exactly as the Books
 * screen has always shown them, so no screen re-formats a name itself.
 * Fallbacks never block the UI and never throw, so no screen has to handle
 * a missing or corrupt book's title itself.
 *
 * Only successfully resolved corpus titles are cached here (the corpus
 * repository also caches its own lookups). A lookup failure returns the
 * catalog/id fallback *without* caching it, so a later call can retry the
 * corpus after a transient failure.
 *
 * Edition seam: this cache is book-id keyed and therefore currently scoped
 * to the default Konkani edition. A second edition must extend this boundary
 * with `editionId` and the cache key.
 */
@Singleton
class BookNameRepositoryImpl @Inject constructor(
    private val titles: BookTitleRepository
) : BookNameRepository {

    private val cache = ConcurrentHashMap<String, String>()

    override suspend fun displayName(bookId: String): String {
        cache[bookId]?.let { return it }
        return try {
            val corpusTitle = withContext(Dispatchers.IO) { titles.getDisplayName(bookId) }
            if (!corpusTitle.isNullOrBlank()) {
                val display = BookDisplayName.capitalizeDisplayName(corpusTitle)
                cache[bookId] = display
                display
            } else {
                fallbackName(bookId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not cached: a later call may still resolve the corpus title.
            fallbackName(bookId)
        }
    }

    override suspend fun displayNames(bookIds: Collection<String>): Map<String, String> =
        bookIds.distinct().associateWith { displayName(it) }

    private fun fallbackName(bookId: String): String =
        BibleCatalog.byId(bookId)
            ?.displayName
            ?.let(BookDisplayName::capitalizeDisplayName)
            ?: bookId
}
