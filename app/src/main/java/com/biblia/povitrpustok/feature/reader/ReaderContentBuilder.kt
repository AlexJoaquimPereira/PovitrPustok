package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.Heading
import com.biblia.povitrpustok.domain.model.Verse

/**
 * Builds the authoritative Reader display sequence (corrective phase
 * Issues 3 & 5; READER-03).
 *
 * Normal mode: ChapterHeader, then headings interleaved by `before_verse` —
 * a heading with before_verse=N appears immediately before the first verse
 * with verse_start >= N; headings with the same before_verse keep their
 * source order; headings with no usable before_verse are placed
 * deterministically (before the first verse / appended at the end) and never
 * discarded.
 *
 * Introductions are intentionally **not** part of the Reader: they are
 * shown by the separate book-introduction screen (`BookIntroductionScreen`),
 * which is the sole entry point (implementation plan §11).
 *
 * Targeted mode: the same sequence filtered to the requested passage —
 * only verses whose verse_start lies in the inclusive range, plus headings
 * that immediately precede a kept verse (section titles for the passage).
 */
object ReaderContentBuilder {

    fun build(
        chapter: Chapter,
        headings: List<Heading>,
        verses: List<Verse>,
        passage: ReaderPassage? = null
    ): List<ReaderContentItem> {
        val full = buildNormal(chapter, headings, verses)
        val requested = passage ?: return full

        val keptVerseIds = verses
            .filter { it.verseStart in requested.verseStart..requested.verseEnd }
            .map { it.id }
            .toSet()

        return buildList {
            for ((index, item) in full.withIndex()) {
                when (item) {
                    is ReaderContentItem.Header -> add(item)
                    is ReaderContentItem.Verse ->
                        if (item.verse.id in keptVerseIds) add(item)
                    is ReaderContentItem.Heading -> {
                        val next = full.getOrNull(index + 1)
                        if (next is ReaderContentItem.Verse && next.verse.id in keptVerseIds) add(item)
                    }
                }
            }
        }
    }

    private fun buildNormal(
        chapter: Chapter,
        headings: List<Heading>,
        verses: List<Verse>
    ): List<ReaderContentItem> {
        val items = ArrayList<ReaderContentItem>(1 + headings.size + verses.size)
        items.add(ReaderContentItem.Header(chapter))

        val sortedHeadings = headings.sortedWith(compareBy({ it.beforeVerse }, { it.position }))
        var headingIndex = 0
        for (verse in verses) {
            while (headingIndex < sortedHeadings.size && sortedHeadings[headingIndex].beforeVerse <= verse.verseStart) {
                items.add(ReaderContentItem.Heading(sortedHeadings[headingIndex]))
                headingIndex++
            }
            items.add(ReaderContentItem.Verse(verse))
        }
        // any remaining headings (before_verse beyond the last verse) go last
        while (headingIndex < sortedHeadings.size) {
            items.add(ReaderContentItem.Heading(sortedHeadings[headingIndex]))
            headingIndex++
        }
        return items
    }
}
