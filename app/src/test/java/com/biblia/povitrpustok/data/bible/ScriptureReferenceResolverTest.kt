package com.biblia.povitrpustok.data.bible

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.domain.model.ResolvedScriptureReference
import com.biblia.povitrpustok.domain.model.ScriptureResolution
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.ScriptureReferenceResolver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Resolver tests against the real corpus (Phase 6 §16, §15, §8).
 * No FTS5 involved — chapter/verse resolution only — so Robolectric works.
 */
@RunWith(RobolectricTestRunner::class)
class ScriptureReferenceResolverTest {

    private lateinit var provider: AssetBibleDatabaseProvider
    private lateinit var repository: BibleRepository
    private lateinit var resolver: ScriptureReferenceResolver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        provider = AssetBibleDatabaseProvider(context)
        repository = BibleRepositoryImpl(provider)
        resolver = ScriptureReferenceResolverImpl(repository)
    }

    @After
    fun tearDown() {
        provider.close()
    }

    private suspend fun resolved(ref: String, contextBookId: String? = null): ResolvedScriptureReference {
        val result = resolver.resolve(ref, contextBookId)
        assertTrue("expected Resolved for '$ref', got $result", result is ScriptureResolution.Resolved)
        return (result as ScriptureResolution.Resolved).reference
    }

    @Test
    fun james_1_5_resolves() = runBlocking {
        val r = resolved("James 1:5")
        assertEquals("jakob", r.bookId)
        assertEquals(1, r.chapterNumber)
        assertEquals(1, r.chapterPosition)
        assertEquals(5, r.verseStart)
        assertEquals(5, r.verseEnd)
        assertNotNull(r.verseId)
        assertNotNull(r.chapterId)
    }

    @Test
    fun james_1_resolves_to_chapter() = runBlocking {
        val r = resolved("James 1")
        assertEquals("jakob", r.bookId)
        assertEquals(1, r.chapterPosition)
        assertNull(r.verseId)
        assertNull(r.verseStart)
    }

    @Test
    fun james_1_5_8_requested_range_is_preserved() = runBlocking {
        val r = resolved("James 1:5-8")
        assertEquals(5, r.verseStart)
        assertEquals(8, r.verseEnd)
        assertNotNull(r.verseId)
    }

    @Test
    fun genesis_17_11_12_is_one_source_range_verse() = runBlocking {
        val r = resolved("Genesis 17:11-12")
        assertEquals("Utpoti", r.bookId)
        assertEquals(17, r.chapterNumber)
        assertEquals(11, r.verseStart)
        assertEquals(12, r.verseEnd)
        assertNotNull("must reference the single source verse", r.verseId)

        // the corpus really has one Verse record 11-12
        val verse = repository.getVerse("Utpoti", r.chapterId, 11)
        assertNotNull(verse)
        assertEquals(12, verse!!.verseEnd)
        assertEquals(r.verseId, verse.id)
    }

    @Test
    fun genesis_17_11_single_request_keeps_source_range() = runBlocking {
        val r = resolved("Genesis 17:11")
        assertEquals(11, r.verseStart)
        assertEquals("source verse 11-12 keeps its extent", 12, r.verseEnd)
    }

    @Test
    fun esther_1_1_resolves() = runBlocking {
        val r = resolved("Esther 1:1")
        assertEquals("Esther", r.bookId)
        assertEquals(1, r.verseStart)
        assertNotNull(r.verseId)
    }

    @Test
    fun job_1_1_resolves() = runBlocking {
        val r = resolved("Job 1:1")
        assertEquals("Jobachem_Pustok", r.bookId)
        assertEquals(1, r.chapterPosition)
        assertNotNull(r.verseId)
    }

    @Test
    fun job_26_is_ambiguous() = runBlocking {
        val result = resolver.resolve("Job 26")
        assertTrue("expected Ambiguous, got $result", result is ScriptureResolution.Ambiguous)
        val ambiguous = result as ScriptureResolution.Ambiguous
        assertEquals("Jobachem_Pustok", ambiguous.bookId)
        assertEquals(26, ambiguous.chapterNumber)
        assertEquals(2, ambiguous.candidates.size)
        assertEquals(setOf(26, 27), ambiguous.candidates.map { it.position }.toSet())
        assertEquals(2, ambiguous.candidates.map { it.id }.toSet().size)
    }

    @Test
    fun job_26_5_is_ambiguous_not_silently_resolved() = runBlocking {
        val result = resolver.resolve("Job 26:5")
        assertTrue("must not silently pick a duplicate chapter", result is ScriptureResolution.Ambiguous)
    }

    @Test
    fun abbreviations_and_case_insensitivity() = runBlocking {
        val viaAbbreviation = resolved("Jas 1:5")
        val viaFullName = resolved("JAMES 1:5")
        assertEquals("jakob", viaAbbreviation.bookId)
        assertEquals(viaAbbreviation.chapterId, viaFullName.chapterId)
        assertEquals("Utpoti", resolved("Gen 17:11-12").bookId)
    }

    @Test
    fun bookless_reference_needs_context() = runBlocking {
        val result = resolver.resolve("3:16")
        assertTrue("bookless without context must be Invalid", result is ScriptureResolution.Invalid)

        val r = resolved("3:16", contextBookId = "juanv")
        assertEquals("juanv", r.bookId)
        assertEquals(3, r.chapterNumber)
        assertEquals(16, r.verseStart)
        assertEquals(16, r.verseEnd)
    }

    @Test
    fun not_found_cases() = runBlocking {
        assertTrue(resolver.resolve("NotABook 1") is ScriptureResolution.NotFound)
        assertTrue(resolver.resolve("James 99") is ScriptureResolution.NotFound)
        assertTrue(resolver.resolve("James 1:99") is ScriptureResolution.NotFound)
    }

    @Test
    fun invalid_cases() = runBlocking {
        assertTrue(resolver.resolve("") is ScriptureResolution.Invalid)
        assertTrue(resolver.resolve("   ") is ScriptureResolution.Invalid)
        assertTrue(resolver.resolve("James 1:8-5") is ScriptureResolution.Invalid)
        assertTrue(resolver.resolve("James 0") is ScriptureResolution.Invalid)
        assertTrue(resolver.resolve("James 1:0") is ScriptureResolution.Invalid)
        assertTrue(resolver.resolve("John 3:16-4:2") is ScriptureResolution.Invalid)
    }

    @Test
    fun multi_word_books_resolve_after_phase10_expansion() = runBlocking {
        val r = resolved("1 Kings 3:16")
        assertEquals("Razanchem_Poilem_Pustok", r.bookId)
        assertEquals(3, r.chapterNumber)
        assertEquals(16, r.verseStart)

        val psalm = resolved("Psalm 45:10, 11, 12, 16")
        assertEquals("Stotranchem_Pustok", psalm.bookId)
        assertEquals(10, psalm.verseStart)
        assertEquals(16, psalm.verseEnd)
    }

    @Test
    fun reader_destination_generation() = runBlocking {
        val r = resolved("James 1:5")
        val destination = r.toReaderDestination()
        assertEquals("jakob", destination.bookId)
        assertEquals(1, destination.chapterPosition)
        assertEquals(r.verseId, destination.verseId)
        // navigation identity is chapter position, never verse or chapter number
        assertEquals(r.chapterPosition, destination.chapterPosition)
    }
}
