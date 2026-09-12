package com.biblia.povitrpustok.core.html

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Text(
        text = BibleHtmlRenderer.render(document),
        modifier = modifier,
        style = style,
        textAlign = textAlign
    )
}
