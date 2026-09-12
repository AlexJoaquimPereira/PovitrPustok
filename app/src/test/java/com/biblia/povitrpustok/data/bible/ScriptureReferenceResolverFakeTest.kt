package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ChapterContent
import com.biblia.povitrpustok.domain.model.IntroductionBlock
import com.biblia.povitrpustok.domain.model.ScriptureResolution
import com.biblia.povitrpustok.domain.model.Verse
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resolver behavior with a fake repository (Phase 6 §17, §20):
 * controlled results and proof that only the named book is opened.
 */
class ScriptureReferenceResolverFakeTest {

    private class FakeBibleRepository(
        private val chaptersByBook: Map<String, List<Chapter>>,
        private val versesByChapter: Map<Long, List<Verse>> = emptyMap()
    ) : BibleRepository {
        val openedChapters = mutableListOf<String>()

        override suspend fun getBook(bookId: String): BibleBook =
            BibleCatalog.byId(bookId) ?: throw BibleBookNotFoundException(bookId)

        override suspend fun getChapters(bookId: String): List<Chapter> {
            openedChapters += bookId
            return chaptersByBook[bookId] ?: emptyList()
        }

        override suspend fun getChapter(bookId: String, chapterPosition: Int): ChapterContent =
            error("not used by the resolver")

        override suspend fun getIntroduction(bookId: String): List<IntroductionBlock> = emptyList()

        override suspend fun getVerse(bookId: String, chapterId: Long, verseStart: Int): Verse? =
            versesByChapter[chapterId]?.firstOrNull { it.verseStart == verseStart }
    }

    private fun chapter(bookId: String, number: Int, position: Int) =
        Chapter(id = position.toLong(), bookId = bookId, number = number, position = position)

    private fun jamesChapters() = listOf(
        chapter("jakob", 1, 1),
        chapter("jakob", 2, 2)
    )

    private fun jamesVerses(chapterId: Long) = listOf(
        Verse(
            id = chapterId * 1000 + 5, chapterId = chapterId, chapterNumber = 1,
            verseStart = 5, verseEnd = 5, html = "v", plainText = "v"
        )
    )

    private fun jobChaptersWithDuplicates() = listOf(
        chapter("Jobachem_Pustok", 26, 26),
        chapter("Jobachem_Pustok", 26, 27),
        chapter("Jobachem_Pustok", 27, 28)
    )

    @Test
    fun named_reference_opens_only_the_named_book() = runBlocking {
        val repository = FakeBibleRepository(mapOf("jakob" to jamesChapters()))
        val resolver = ScriptureReferenceResolverImpl(repository)

        val result = resolver.resolve("James 1")

        assertTrue(result is ScriptureResolution.Resolved)
        assertEquals(
            "resolution must not scan other books",
            listOf("jakob"),
            repository.openedChapters
        )
    }

    @Test
    fun unknown_chapter_is_not_found() = runBlocking {
        val resolver = ScriptureReferenceResolverImpl(FakeBibleRepository(mapOf("jakob" to jamesChapters())))
        val result = resolver.resolve("James 99")
        assertTrue("expected NotFound, got $result", result is ScriptureResolution.NotFound)
    }

    @Test
    fun unknown_verse_is_not_found() = runBlocking {
        val chapters = jamesChapters()
        val repository = FakeBibleRepository(
            chaptersByBook = mapOf("jakob" to chapters),
            versesByChapter = mapOf(chapters.first().id to jamesVerses(chapters.first().id))
        )
        val resolver = ScriptureReferenceResolverImpl(repository)
        val result = resolver.resolve("James 1:99")
        assertTrue("expected NotFound, got $result", result is ScriptureResolution.NotFound)
    }

    @Test
    fun unknown_book_is_not_found() = runBlocking {
        val resolver = ScriptureReferenceResolverImpl(FakeBibleRepository(emptyMap()))
        assertTrue(resolver.resolve("NotABook 1") is ScriptureResolution.NotFound)
    }

    @Test
    fun invalid_and_bookless_cases() = runBlocking {
        val resolver = ScriptureReferenceResolverImpl(FakeBibleRepository(emptyMap()))
        assertTrue(resolver.resolve("") is ScriptureResolution.Invalid)
        assertTrue(resolver.resolve("James 1:8-5") is ScriptureResolution.Invalid)
        assertTrue(resolver.resolve("3:16") is ScriptureResolution.Invalid)
        assertTrue(resolver.resolve("James 1:5:6") is ScriptureResolution.Invalid)
    }

    @Test
    fun bookless_reference_with_context_uses_the_context_book() = runBlocking {
        val chapters = jamesChapters()
        val repository = FakeBibleRepository(
            chaptersByBook = mapOf("jakob" to chapters),
            versesByChapter = mapOf(chapters.first().id to jamesVerses(chapters.first().id))
        )
        val resolver = ScriptureReferenceResolverImpl(repository)

        val result = resolver.resolve("1:5", contextBookId = "jakob")

        assertTrue("expected Resolved, got $result", result is ScriptureResolution.Resolved)
        assertEquals(listOf("jakob"), repository.openedChapters)
    }

    @Test
    fun duplicate_chapters_are_ambiguous() = runBlocking {
        val repository = FakeBibleRepository(mapOf("Jobachem_Pustok" to jobChaptersWithDuplicates()))
        val resolver = ScriptureReferenceResolverImpl(repository)

        val result = resolver.resolve("Job 26")

        assertTrue("expected Ambiguous, got $result", result is ScriptureResolution.Ambiguous)
        val ambiguous = result as ScriptureResolution.Ambiguous
        assertEquals(2, ambiguous.candidates.size)
        assertEquals(setOf(26, 27), ambiguous.candidates.map { it.position }.toSet())
    }

    @Test
    fun unique_chapter_resolves_even_when_duplicates_exist_elsewhere() = runBlocking {
        val repository = FakeBibleRepository(mapOf("Jobachem_Pustok" to jobChaptersWithDuplicates()))
        val resolver = ScriptureReferenceResolverImpl(repository)

        val result = resolver.resolve("Job 27")

        assertTrue("chapter 27 is unique, expected Resolved, got $result", result is ScriptureResolution.Resolved)
    }
}
