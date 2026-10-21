package com.biblia.povitrpustok.domain.model

/**
 * Compact route encoding for non-continuous / cross-chapter passages.
 * Format: `chapterPosition:start-end,chapterPosition:start-end`; an omitted
 * side (`1:-8` or `2:5-`) means the open start/end of that chapter.
 */
object ReaderPassageCodec {

    fun encode(passages: List<ReaderPassageSpec>): String =
        passages.joinToString(",") { spec ->
            "${spec.chapterPosition}:${spec.verseStart?.toString().orEmpty()}-${spec.verseEnd?.toString().orEmpty()}"
        }

    fun decode(raw: String?): List<ReaderPassageSpec> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').mapNotNull { part ->
            val colon = part.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val chapter = part.substring(0, colon).toIntOrNull() ?: return@mapNotNull null
            val range = part.substring(colon + 1)
            val dash = range.indexOf('-')
            if (dash < 0) return@mapNotNull null
            val start = range.substring(0, dash).toIntOrNull()
            val end = range.substring(dash + 1).toIntOrNull()
            if (start == null && end == null) return@mapNotNull null
            ReaderPassageSpec(chapter, start, end, openEnd = end == null && start != null)
        }
    }
}
