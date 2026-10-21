package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.domain.model.Chapter as DomainChapter
import com.biblia.povitrpustok.domain.model.Heading as DomainHeading
import com.biblia.povitrpustok.domain.model.Verse as DomainVerse

/**
 * The single authoritative displayable-content model for a chapter
 * (corrective phase Issues 3 & 5).
 *
 * One ordered sequence drives rendering, verse-target index calculation,
 * and targeted-passage filtering — so "where the verse is rendered" and
 * "where the app thinks the verse is" can never diverge.
 */
sealed interface ReaderContentItem {

    val key: String

    data class Header(val chapter: DomainChapter) : ReaderContentItem {
        override val key: String = "chapter-header-${chapter.id}"
    }

    data class Heading(val heading: DomainHeading) : ReaderContentItem {
        override val key: String = "heading-${heading.id}"
    }

    data class Verse(val verse: DomainVerse) : ReaderContentItem {
        override val key: String = "verse-${verse.id}"
    }
}

/**
 * A requested passage (corrective phase Issue 5): an inclusive range of
 * verse numbers to display in targeted mode. Distinct from the source
 * verse record — a source record spanning 11-12 still displays when the
 * requested range covers 11.
 */
data class ReaderPassage(
    val verseStart: Int,
    val verseEnd: Int,
    /** Non-continuous ranges; defaults to the single inclusive span. */
    val ranges: List<IntRange> = listOf(verseStart..verseEnd)
)

/** One chapter of a multi-chapter or non-continuous passage. */
data class ReaderChapterPassage(
    val chapter: DomainChapter,
    val headings: List<DomainHeading>,
    val verses: List<DomainVerse>,
    val passage: ReaderPassage
)
