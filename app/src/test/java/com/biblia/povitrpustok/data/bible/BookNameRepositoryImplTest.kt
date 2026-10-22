package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.domain.repository.BookTitleRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [BookNameRepositoryImpl] resolution order and caching (F-12): corpus title
 * first, catalog display name fallback, id last — with failures retried
 * instead of cached.
 */
class BookNameRepositoryImplTest {

    private class FakeBookTitleRepository(
        private val titles: MutableMap<String, String?> = mutableMapOf(),
        var failure: Exception? = null
    ) : BookTitleRepository {

        val calls = mutableMapOf<String, Int>()

        override suspend fun getDisplayName(bookId: String): String? {
            calls[bookId] = (calls[bookId] ?: 0) + 1
            failure?.let { throw it }
            return titles[bookId]
        }

        override suspend fun loadAllDisplayNames(): Map<String, String> = titles
            .mapNotNull { (id, title) -> title?.let { id to it } }
            .toMap()
    }

    @Test
    fun corpusTitleWinsOverCatalogAndIsCached() = runBlocking {
        val titles = FakeBookTitleRepository(mutableMapOf("Utpoti" to "Berexitt (corpus)"))
        val repository = BookNameRepositoryImpl(titles)

        assertEquals("Berexitt (corpus)", repository.displayName("Utpoti"))
        assertEquals("Berexitt (corpus)", repository.displayName("Utpoti"))
        assertEquals(1, titles.calls["Utpoti"])
    }

    @Test
    fun corpusTitleIsReturnedDisplayReady() = runBlocking {
        val repository = BookNameRepositoryImpl(
            FakeBookTitleRepository(mutableMapOf("Utpoti" to "  UTPOTI  "))
        )

        assertEquals("Utpoti", repository.displayName("Utpoti"))
    }

    @Test
    fun absentCorpusTitleFallsBackToCatalogDisplayName() = runBlocking {
        val repository = BookNameRepositoryImpl(FakeBookTitleRepository())

        assertEquals("Levi Xastr", repository.displayName("Levi_Xastr"))
    }

    @Test
    fun unknownIdFallsBackToItself() = runBlocking {
        val repository = BookNameRepositoryImpl(FakeBookTitleRepository())

        assertEquals("nope", repository.displayName("nope"))
    }

    @Test
    fun failedLookupUsesCatalogFallbackWithoutCaching() = runBlocking {
        val titles = FakeBookTitleRepository(
            titles = mutableMapOf("Utpoti" to "Corpus Genesis"),
            failure = IllegalStateException("corrupt book")
        )
        val repository = BookNameRepositoryImpl(titles)

        assertEquals("Utpoti", repository.displayName("Utpoti"))

        // The failure must not poison the cache: a later call retries.
        titles.failure = null
        assertEquals("Corpus Genesis", repository.displayName("Utpoti"))
        assertEquals(2, titles.calls["Utpoti"])
    }

    @Test
    fun displayNamesDeduplicatesAndCoversEveryRequestedId() = runBlocking {
        val titles = FakeBookTitleRepository(
            mutableMapOf("Utpoti" to "Corpus Genesis")
        )
        val repository = BookNameRepositoryImpl(titles)

        val names = repository.displayNames(
            listOf("Utpoti", "Utpoti", "Levi_Xastr", "nope")
        )

        assertEquals(
            mapOf(
                "Utpoti" to "Corpus Genesis",
                "Levi_Xastr" to "Levi Xastr",
                "nope" to "nope"
            ),
            names
        )
        assertEquals(1, titles.calls["Utpoti"])
        assertEquals(1, titles.calls["Levi_Xastr"])
        assertEquals(1, titles.calls["nope"])
    }
}
