package com.biblia.povitrpustok.testutil

import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.BookDisplayName
import com.biblia.povitrpustok.domain.repository.BookNameRepository

/**
 * Catalog-only [BookNameRepository] fake for ViewModel tests that do not
 * exercise corpus titles (F-12). Mirrors the production fallback order and
 * display formatting: the supplied name, then the Capitalize-Cased catalog
 * display name, then the raw id.
 */
class FakeBookNameRepository(
    private val names: Map<String, String> = emptyMap()
) : BookNameRepository {

    override suspend fun displayName(bookId: String): String =
        names[bookId]
            ?: BibleCatalog.byId(bookId)
                ?.displayName
                ?.let(BookDisplayName::capitalizeDisplayName)
            ?: bookId

    override suspend fun displayNames(bookIds: Collection<String>): Map<String, String> =
        bookIds.distinct().associateWith { displayName(it) }
}
