package com.biblia.povitrpustok.core.html

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * Minimal Compose surface for parsed Bible HTML (implementation plan §18).
 *
 * Infrastructure for the future reader: renders a [BibleHtmlDocument]
 * through [BibleHtmlRenderer]. The reader phase will apply its dedicated
 * Bible text style and wrap this in scrollable layout; no reader screen
 * exists yet.
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
    val annotated = remember(document) { BibleHtmlRenderer.render(document) }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        textAlign = textAlign
    )
}
