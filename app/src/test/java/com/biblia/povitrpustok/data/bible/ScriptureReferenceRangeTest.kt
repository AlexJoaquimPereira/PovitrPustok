package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.domain.model.ScriptureResolution
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.ScriptureReferenceResolver
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * REF-01: references whose verse number is covered by a combined source
 * record must resolve to that record, and duplicate source verse numbers
 * must surface explicit ambiguity instead of an arbitrary pick.
 *
 * Corpus facts (verified):
 *  - Genesis 16 has one source record covering verses 11-12;
 *  - Genesis 48 has one covering 15-16;
 *  - Esther 3 has two distinct records numbered 2 (Hebrew + Greek addition);
 *  - James 1 has no row for verse 7 (the number is only a textual marker).
 */
@RunWith(RobolectricTestRunner::class)
class ScriptureReferenceRangeTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private lateinit var provider: AssetBibleDatabaseProvider
    private lateinit var resolver: ScriptureReferenceResolver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        provider = AssetBibleDatabaseProvider(context)
        val repository: BibleRepository = BibleRepositoryImpl(provider)
        resolver = ScriptureReferenceResolverImpl(repository)
    }

    @After
    fun tearDown() {
        provider.close()
    }

    @Test
    fun verse_inside_a_combined_source_range_resolves_to_that_record() = runBlocking {
        // Genesis 16:11-12 is one source record; requesting its second verse
        // must resolve to the same record, not NotFound.
        val resolution = resolver.resolve("Genesis 16:12")
        assertTrue("expected Resolved, got $resolution", resolution is ScriptureResolution.Resolved)
        val reference = (resolution as ScriptureResolution.Resolved).reference

        assertEquals("Utpoti", reference.bookId)
        assertEquals(16, reference.chapterNumber)
        assertEquals(16, reference.chapterPosition)
        assertEquals(11, reference.verseStart)
        assertEquals(12, reference.verseEnd)
        assertTrue("the source record id must be preserved", (reference.verseId ?: 0L) > 0L)
    }

    @Test
    fun explicit_range_covering_reference_resolves_to_the_same_source_record() = runBlocking {
        val resolution = resolver.resolve("Genesis 16:11-12")
        assertTrue(resolution is ScriptureResolution.Resolved)
        val reference = (resolution as ScriptureResolution.Resolved).reference
        assertEquals(11, reference.verseStart)
        assertEquals(12, reference.verseEnd)
    }

    @Test
    fun second_combined_range_resolves() = runBlocking {
        val resolution = resolver.resolve("Genesis 48:16")
        assertTrue("expected Resolved, got $resolution", resolution is ScriptureResolution.Resolved)
        val reference = (resolution as ScriptureResolution.Resolved).reference
        assertEquals(48, reference.chapterNumber)
        assertEquals(15, reference.verseStart)
        assertEquals(16, reference.verseEnd)
    }

    @Test
    fun end_of_a_wide_range_resolves_to_its_source_record() = runBlocking {
        // Mark 1 has one source record covering 2-3; Matthew 15 one covering 7-9.
        val mark = resolver.resolve("Mark 1:3")
        assertTrue("expected Resolved for Mark 1:3, got $mark", mark is ScriptureResolution.Resolved)
        val markReference = (mark as ScriptureResolution.Resolved).reference
        assertEquals("mark", markReference.bookId)
        assertEquals(2, markReference.verseStart)
        assertEquals(3, markReference.verseEnd)

        val matthew = resolver.resolve("Matthew 15:9")
        assertTrue("expected Resolved for Matthew 15:9, got $matthew", matthew is ScriptureResolution.Resolved)
        val matthewReference = (matthew as ScriptureResolution.Resolved).reference
        assertEquals("matev", matthewReference.bookId)
        assertEquals(7, matthewReference.verseStart)
        assertEquals(9, matthewReference.verseEnd)
    }

    @Test
    fun plain_verse_still_resolves_to_itself() = runBlocking {
        val resolution = resolver.resolve("James 1:5")
        assertTrue(resolution is ScriptureResolution.Resolved)
        val reference = (resolution as ScriptureResolution.Resolved).reference
        assertEquals(5, reference.verseStart)
        assertEquals(5, reference.verseEnd)
    }

    @Test
    fun verse_absorbed_only_in_text_is_a_controlled_not_found() = runBlocking {
        // James 1 has no verse-7 row and verse 6's range metadata ends at 6;
        // the combined text must not be guessed from HTML.
        val resolution = resolver.resolve("James 1:7")
        assertTrue("expected NotFound, got $resolution", resolution is ScriptureResolution.NotFound)
    }

    @Test
    fun duplicate_source_verse_numbers_surface_explicit_ambiguity() = runBlocking {
        // Esther 3 has two distinct records numbered 2 (Hebrew body and the
        // Greek-addition letter). The resolver must return both, not pick one.
        val resolution = resolver.resolve("Esther 3:2")
        assertTrue("expected AmbiguousVerse, got $resolution", resolution is ScriptureResolution.AmbiguousVerse)
        val ambiguous = resolution as ScriptureResolution.AmbiguousVerse

        assertEquals("Esther", ambiguous.bookId)
        assertEquals(3, ambiguous.chapterNumber)
        assertEquals(2, ambiguous.verseNumber)
        assertTrue("expected at least two candidates, got ${ambiguous.candidates.size}", ambiguous.candidates.size >= 2)
        assertEquals(
            "candidate ids must be distinct",
            ambiguous.candidates.size,
            ambiguous.candidates.map { it.id }.toSet().size
        )
        assertTrue("every candidate must cover verse 2", ambiguous.candidates.all { it.verseStart <= 2 && it.verseEnd >= 2 })
        assertNotEquals(
            "the duplicate records must be distinguishable",
            ambiguous.candidates.first().id,
            ambiguous.candidates.last().id
        )
    }

    @Test
    fun duplicate_chapter_numbers_still_take_precedence_over_verse_lookup() = runBlocking {
        // Job has two chapter-26 records: chapter ambiguity is reported before
        // any verse lookup, unchanged by REF-01.
        val resolution = resolver.resolve("Job 26:5")
        assertTrue("expected chapter-level Ambiguous, got $resolution", resolution is ScriptureResolution.Ambiguous)
        val ambiguous = resolution as ScriptureResolution.Ambiguous
        // REF-04: the requested verse is carried through the choice.
        assertEquals(5, ambiguous.verseStart)
        assertEquals(5, ambiguous.verseEnd)
    }

    @Test
    fun range_end_beyond_the_corpus_is_not_found() = runBlocking {
        val resolution = resolver.resolve("James 1:5-999")
        assertTrue("expected NotFound, got $resolution", resolution is ScriptureResolution.NotFound)
    }

    @Test
    fun range_end_in_a_corpus_gap_is_not_found() = runBlocking {
        // James 1 has no record for verse 7 (only an inline text marker).
        val resolution = resolver.resolve("James 1:6-7")
        assertTrue("expected NotFound, got $resolution", resolution is ScriptureResolution.NotFound)
    }

    @Test
    fun range_with_a_supported_end_resolves() = runBlocking {
        val resolution = resolver.resolve("James 1:5-8")
        assertTrue("expected Resolved, got $resolution", resolution is ScriptureResolution.Resolved)
        val reference = (resolution as ScriptureResolution.Resolved).reference
        assertEquals(5, reference.verseStart)
        assertEquals(8, reference.verseEnd)
    }

    @Test
    fun range_whose_end_equals_its_start_resolves() = runBlocking {
        val resolution = resolver.resolve("James 1:5-5")
        assertTrue("expected Resolved, got $resolution", resolution is ScriptureResolution.Resolved)
        val reference = (resolution as ScriptureResolution.Resolved).reference
        assertEquals(5, reference.verseStart)
        assertEquals(5, reference.verseEnd)
    }

    @Test
    fun reversed_range_is_invalid() = runBlocking {
        val resolution = resolver.resolve("James 1:8-5")
        assertTrue("expected Invalid, got $resolution", resolution is ScriptureResolution.Invalid)
    }

    @Test
    fun range_end_inside_a_combined_record_is_valid() = runBlocking {
        // End 12 is covered by the single 11-12 source record.
        val resolution = resolver.resolve("Genesis 16:11-12")
        assertTrue("expected Resolved, got $resolution", resolution is ScriptureResolution.Resolved)
        val reference = (resolution as ScriptureResolution.Resolved).reference
        assertEquals(11, reference.verseStart)
        assertEquals(12, reference.verseEnd)
    }

    @Test
    fun range_end_with_duplicate_coverage_resolves() = runBlocking {
        // Esther 3:12 is a unique start; verse 13 is covered by two records.
        // End validation only requires coverage, so the start record anchors
        // the result and the end duplicates do not create ambiguity.
        val resolution = resolver.resolve("Esther 3:12-13")
        assertTrue("expected Resolved, got $resolution", resolution is ScriptureResolution.Resolved)
        val reference = (resolution as ScriptureResolution.Resolved).reference
        assertEquals(12, reference.verseStart)
        assertEquals(13, reference.verseEnd)
    }

    @Test
    fun start_ambiguity_is_reported_before_range_end_validation() = runBlocking {
        // Esther 3:2 is ambiguous; that must win over the impossible end 999.
        val resolution = resolver.resolve("Esther 3:2-999")
        assertTrue("expected AmbiguousVerse, got $resolution", resolution is ScriptureResolution.AmbiguousVerse)
    }
}
