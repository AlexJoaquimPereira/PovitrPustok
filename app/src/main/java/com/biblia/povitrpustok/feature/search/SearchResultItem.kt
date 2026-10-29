package com.biblia.povitrpustok.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.SearchResult
import com.biblia.povitrpustok.ui.components.englishBookNameVisible

/**
 * One search result row (implementation plan §26): book name, chapter:verse
 * reference, and a plain-text snippet. [bookName] is the resolved name from
 * [com.biblia.povitrpustok.domain.repository.BookNameRepository]; the static
 * [BibleCatalog] is only the fallback (F-12), so the row never waits on a
 * lookup. The snippet is the FTS5 text_plain window — raw HTML is never
 * displayed and canonical verses.text is never touched by search rendering.
 */
@Composable
fun SearchResultItem(
    result: SearchResult,
    query: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bookName: String? = null
) {
    val book = remember(result.bookId) { BibleCatalog.byId(result.bookId) }
    val displayName = bookName ?: book?.displayName ?: result.bookId
    val showEnglish = englishBookNameVisible()
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
    val snippet = remember(result.snippet, query, highlight) {
        highlightTerms(result.snippet, query, highlight)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                // At 2x the book name may need a second line; the reference
                // keeps its own space, so the name wraps instead of vanishing.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            book?.englishName?.takeIf { showEnglish }?.let { english ->
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "($english)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (result.verseStart == result.verseEnd) {
                    stringResource(R.string.search_result_reference, result.chapterNumber, result.verseStart)
                } else {
                    stringResource(R.string.search_result_reference_range, result.chapterNumber, result.verseStart, result.verseEnd)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = snippet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Highlights query tokens in the plain-text snippet (Day 4, F-22). */
private fun highlightTerms(
    text: String,
    query: String,
    style: SpanStyle
): AnnotatedString {
    val terms = query
        .split(Regex("\\s+"))
        .map { it.trim('"', '\'', '*', ',', '.', ':', ';', '(', ')') }
        .filter { it.isNotBlank() }
        .distinct()
    if (terms.isEmpty()) return AnnotatedString(text)

    val regex = Regex(terms.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    val matches = regex.findAll(text).toList()
    if (matches.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        var cursor = 0
        for (match in matches) {
            if (match.range.first < cursor) continue
            append(text.substring(cursor, match.range.first))
            withStyle(style) { append(text.substring(match.range)) }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
}
