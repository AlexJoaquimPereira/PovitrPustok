package com.biblia.povitrpustok.data.bible

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.domain.model.ScriptureResolution
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.ScriptureReferenceResolver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real-corpus resolution of references that occur in Catholic daily
 * readings (Phase 10 §25): the API reference -> resolver -> local Bible
 * record -> ReaderDestination pipeline against the actual databases.
 */
@RunWith(RobolectricTestRunner::class)
class ScriptureReferenceLiturgyResolutionTest {

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

    private suspend fun resolved(reference: String) =
        (resolver.resolve(reference) as ScriptureResolution.Resolved).reference

    @Test
    fun first_corinthians_reference_resolves() = runBlocking {
        val r = resolved("1 Corinthians 15:20-27")
        assertEquals("korintkarant1", r.bookId)
        assertEquals(15, r.chapterNumber)
        assertEquals(20, r.verseStart)
        assertEquals(27, r.verseEnd)
        assertNotNull(r.verseId)
        // ReaderDestination carries the stable navigation ids
        assertEquals("korintkarant1", r.toReaderDestination().bookId)
        assertNotNull(r.toReaderDestination().chapterPosition)
    }

    @Test
    fun second_corinthians_reference_resolves() = runBlocking {
        val r = resolved("2 Corinthians 9:6-10")
        assertEquals("korintkarant2", r.bookId)
        assertEquals(9, r.chapterNumber)
        assertEquals(6, r.verseStart)
        assertEquals(10, r.verseEnd)
    }

    @Test
    fun psalm_enumeration_resolves_to_span() = runBlocking {
        val r = resolved("Psalm 45:10, 11, 12, 16")
        assertEquals("Stotranchem_Pustok", r.bookId)
        assertEquals(10, r.verseStart)
        assertEquals(16, r.verseEnd)
        assertNotNull(r.verseId)
    }

    @Test
    fun john_gospel_reference_resolves() = runBlocking {
        val r = resolved("John 3:16")
        assertEquals("juanv", r.bookId)
        assertEquals(3, r.chapterNumber)
        assertEquals(16, r.verseStart)
        assertEquals(16, r.verseEnd)
    }

    @Test
    fun john_twelve_verse_range_resolves() = runBlocking {
        // Current corpus fact: John 12 contains verses 24-26, so the
        // lectionary reference must resolve to that source range (it was
        // previously asserted as a "gap" against an older corpus).
        val r = resolved("John 12:24-26")
        assertEquals("juanv", r.bookId)
        assertEquals(12, r.chapterNumber)
        assertEquals(24, r.verseStart)
        assertEquals(26, r.verseEnd)
        assertNotNull(r.verseId)
    }

    @Test
    fun luke_gospel_reference_resolves() = runBlocking {
        val r = resolved("Luke 1:39-56")
        assertEquals("lukas", r.bookId)
        assertEquals(1, r.chapterNumber)
        assertEquals(39, r.verseStart)
        assertEquals(56, r.verseEnd)
    }

    @Test
    fun kings_reference_resolves_after_parser_expansion() = runBlocking {
        val r = resolved("1 Kings 3:16")
        assertEquals("Razanchem_Poilem_Pustok", r.bookId)
        assertEquals(3, r.chapterNumber)
        assertEquals(16, r.verseStart)
    }

    @Test
    fun wisdom_suffix_reference_resolves() = runBlocking {
        val r = resolved("Wisdom 9:13-18b")
        assertEquals("Zannvaiechem_Pustok", r.bookId)
        assertEquals(9, r.chapterNumber)
        assertEquals(13, r.verseStart)
        assertEquals(18, r.verseEnd)
        assertNotNull(r.verseId)
    }

    @Test
    fun philemon_chapterless_reference_resolves() = runBlocking {
        val r = resolved("Philemon 9-10, 12-17")
        assertEquals("filemonak", r.bookId)
        assertEquals(1, r.chapterNumber)
        assertEquals(9, r.verseStart)
        assertEquals(17, r.verseEnd)
        assertNotNull(r.verseId)
    }

    @Test
    fun thessalonians_references_resolve_via_aliases() = runBlocking {
        val one = resolved("1 Thessalonians 5:16-18")
        assertEquals("tesalonikarank1", one.bookId)
        assertEquals(5, one.chapterNumber)
        assertEquals(16, one.verseStart)
        assertEquals(18, one.verseEnd)

        val two = resolved("2 Thessalonians 3:1-5")
        assertEquals("tesalonikarank2", two.bookId)
        assertEquals(3, two.chapterNumber)
        assertEquals(1, two.verseStart)
    }

    @Test
    fun revelation_suffix_reference_resolves_against_the_real_corpus() = runBlocking {
        // corrective Issue 6: 19A normalizes to verse 19 for resolution
        val r = resolved("Revelation 11:19A")
        assertEquals("juanvak_prokasovnni", r.bookId)
        assertEquals(11, r.chapterNumber)
        assertEquals(19, r.verseStart)
        assertEquals(19, r.verseEnd)
        assertNotNull(r.verseId)
        val lower = resolved("Revelation 11:19a")
        assertEquals("juanvak_prokasovnni", lower.bookId)
        assertEquals(19, lower.verseStart)
    }

    @Test
    fun unsupported_reference_returns_a_controlled_state() = runBlocking {
        val result = resolver.resolve("1 Kings 99:1")
        assertTrue("must not crash", result is ScriptureResolution.NotFound)
    }
}
