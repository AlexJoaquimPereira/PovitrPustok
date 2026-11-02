package com.biblia.povitrpustok.core.html

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle

/**
 * Rendered-coordinate overlays (S1-02).
 *
 * Highlights and links are applied on top of [BibleHtmlRenderer] output
 * without changing typography or re-parsing the source. Ranges are character
 * offsets into [RenderedText.text]; a caller that starts from a source
 * position resolves it with [RenderedText.sourceToRendered] first.
 *
 * The type is deliberately data-only, so applying an overlay is a pure
 * operation that can be tested on the JVM and memoised per composition.
 *
 * Highlight colours are the caller's responsibility: use a palette surface
 * token (`target`, `selection` or `bookmarked`), whose text contrast is
 * verified for both themes by `ScripturePaletteTest`. The overlay does not
 * re-check contrast.
 */
data class RenderedOverlay(
    val highlights: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
    val links: List<AnnotatedString.Range<LinkAnnotation>> = emptyList()
) {

    val isEmpty: Boolean get() = highlights.isEmpty() && links.isEmpty()

    /**
     * Returns [base] with the overlay applied. The base is copied wholesale
     * (text and every annotation), then highlights, then links, so an overlay
     * never drops the renderer's italic/bold/color/alignment work or an
     * earlier link. An empty overlay returns [base] unchanged.
     */
    fun applyTo(base: AnnotatedString): AnnotatedString {
        if (isEmpty) return base
        val builder = AnnotatedString.Builder(base)
        highlights.forEach { builder.addStyle(it.item, it.start, it.end) }
        links.forEach { range ->
            // Compose exposes addLink for the concrete link types only; an
            // unknown annotation would have no click behaviour, so dropping it
            // is safer than inventing one.
            when (val link = range.item) {
                is LinkAnnotation.Url -> builder.addLink(link, range.start, range.end)
                is LinkAnnotation.Clickable -> builder.addLink(link, range.start, range.end)
                else -> Unit
            }
        }
        return builder.toAnnotatedString()
    }
}
