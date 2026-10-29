package com.biblia.povitrpustok.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.biblia.povitrpustok.ui.adaptive.AppBreakpoints

/**
 * Shared book-name presentation (F-12): one local-language name everywhere,
 * with the canonical English name as a secondary label.
 *
 * The English label is hidden from 2x font scale onward so the primary name
 * always keeps room, and every label is single-line with an ellipsis.
 */
enum class BookNameLayout { STACKED, INLINE }

/**
 * True when there is room for the secondary English name (below 2x font
 * scale). Both [BookName] and callers that compose their own secondary line
 * use this predicate so the threshold cannot drift.
 */
@Composable
fun englishBookNameVisible(): Boolean =
    LocalDensity.current.fontScale < AppBreakpoints.ExtraLargeFontScale

/**
 * Renders [localName] as the primary label and [englishName] as a secondary
 * one when [englishVisible] is true.
 */
@Composable
fun BookName(
    localName: String,
    englishName: String?,
    modifier: Modifier = Modifier,
    layout: BookNameLayout = BookNameLayout.STACKED,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    englishStyle: TextStyle = MaterialTheme.typography.bodySmall,
    englishVisible: Boolean = englishBookNameVisible()
) {
    val showEnglish = englishVisible && !englishName.isNullOrBlank()
    when (layout) {
        BookNameLayout.STACKED -> Column(modifier = modifier) {
            Text(
                text = localName,
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showEnglish) {
                Text(
                    text = "($englishName)",
                    style = englishStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        BookNameLayout.INLINE -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = localName,
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (showEnglish) {
                Text(
                    text = " ($englishName)",
                    style = englishStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
