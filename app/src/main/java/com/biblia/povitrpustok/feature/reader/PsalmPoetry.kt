package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.core.html.BibleHtmlDocument
import com.biblia.povitrpustok.core.html.BibleHtmlNode

/**
 * Psalm poetic rendering mode (corrective phase Issue 8).
 *
 * In this corpus each Psalm's `<p align=center >` (with a space before
 * `>`) wraps the whole psalm in the source, but the importer stored the
 * attribute only on the psalm's first verse record; subsequent verse
 * records are bare `<br/>`-separated poetic lines with no alignment of
 * their own. The database's `<br>` structure stays intact and no verse
 * HTML is modified — instead, at presentation time, a Psalms verse whose
 * own HTML carries no explicit alignment is centered as a poetic block.
 * Verses that DO carry their own alignment keep it. Limited to the Psalms
 * book; no other poetry is centered.
 */
object PsalmPoetry {

    const val PSALMS_BOOK_ID = "Stotranchem_Pustok"

    /**
     * True when [bookId] is Psalms and [document] carries no explicit
     * alignment of its own — the verse is then centered as a poetic block.
     */
    fun isPoeticVerse(bookId: String?, document: BibleHtmlDocument): Boolean =
        bookId == PSALMS_BOOK_ID && !hasExplicitAlignment(document.children)

    private fun hasExplicitAlignment(nodes: List<BibleHtmlNode>): Boolean =
        nodes.any { node ->
            when (node) {
                is BibleHtmlNode.Paragraph -> node.alignment != null || hasExplicitAlignment(node.children)
                is BibleHtmlNode.Heading -> node.alignment != null || hasExplicitAlignment(node.children)
                is BibleHtmlNode.Italic -> hasExplicitAlignment(node.children)
                is BibleHtmlNode.Bold -> hasExplicitAlignment(node.children)
                is BibleHtmlNode.Font -> hasExplicitAlignment(node.children)
                is BibleHtmlNode.Unknown -> hasExplicitAlignment(node.children)
                is BibleHtmlNode.Text -> false
                BibleHtmlNode.Break -> false
            }
        }
}
