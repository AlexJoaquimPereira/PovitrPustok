package com.biblia.povitrpustok.core.html

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.biblia.povitrpustok.ui.theme.LocalScripturePalette
import com.biblia.povitrpustok.ui.theme.MIN_TEXT_CONTRAST_RATIO
import com.biblia.povitrpustok.ui.theme.ScripturePalette
import com.biblia.povitrpustok.ui.theme.contrastRatio

/**
 * Minimal Compose surface for parsed Bible HTML (implementation plan §18).
 *
 * Infrastructure for the future reader: renders a [BibleHtmlDocument]
 * through [BibleHtmlRenderer], then translates the renderer's literal corpus
 * colours into the active [ScripturePalette] (F-02). The reader phase applies
 * its dedicated Bible text style and wraps this in scrollable layout.
 */
@Composable
fun BibleHtmlText(
    document: BibleHtmlDocument,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    textAlign: TextAlign? = null
) {
    // HTML-02: rendering builds an AnnotatedString and walks the node tree;
    // cache it per document so recompositions (scroll, selection, theme
    // changes) do not rebuild unchanged formatted text.
    val rendered = remember(document) { BibleHtmlRenderer.render(document) }

    // F-02: the palette is part of the key so a dark/light switch is not
    // served from the previous mapping.
    val palette = LocalScripturePalette.current
    val annotated = remember(rendered, palette) { mapScriptureColors(rendered, palette) }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        textAlign = textAlign
    )
}

// Corpus `font color` values, exactly as emitted by BibleHtmlRenderer.
private val CORPUS_RED = 0xFFFF0000.toInt()
private val CORPUS_GREEN = 0xFF00B050.toInt()
private val CORPUS_BLUE = 0xFF0000FF.toInt()
private val CORPUS_NAVY = 0xFF021A56.toInt()
private val CORPUS_MID_BLUE = 0xFF0070C0.toInt()
private val CORPUS_BLACK = 0xFF000000.toInt()

/**
 * Resolves one corpus colour to the palette (F-02). Pure and testable.
 *
 * Known corpus colours map by meaning (red letters, quoted speech,
 * cross-references); literal black becomes the body text colour so dark mode
 * never draws black on black. Any other colour survives only when it already
 * clears WCAG AA against the palette background, otherwise it falls back to
 * body text rather than risking an unreadable span.
 */
internal fun resolveScriptureColor(color: Color, palette: ScripturePalette): Color =
    when (color.toArgb()) {
        CORPUS_RED -> if (palette.redLetterEnabled) palette.red else palette.onBackground
        CORPUS_GREEN -> palette.green
        CORPUS_BLUE -> palette.blue
        CORPUS_NAVY -> palette.navy
        CORPUS_MID_BLUE -> palette.midBlue
        CORPUS_BLACK -> palette.onBackground
        else -> if (contrastRatio(color, palette.background) >= MIN_TEXT_CONTRAST_RATIO) {
            color
        } else {
            palette.onBackground
        }
    }

/**
 * Returns a copy of [rendered] whose colours are resolved through
 * [palette] (F-02). The renderer's output is never mutated: text, span
 * ranges, italics/bold and paragraph alignment are carried over untouched.
 */
internal fun mapScriptureColors(
    rendered: AnnotatedString,
    palette: ScripturePalette
): AnnotatedString {
    if (rendered.spanStyles.none { it.item.color != Color.Unspecified }) return rendered

    val builder = AnnotatedString.Builder(rendered.text)
    rendered.paragraphStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
    rendered.spanStyles.forEach { range ->
        val span = range.item
        val mapped = if (span.color == Color.Unspecified) {
            span
        } else {
            span.copy(color = resolveScriptureColor(span.color, palette))
        }
        builder.addStyle(mapped, range.start, range.end)
    }
    return builder.toAnnotatedString()
}
