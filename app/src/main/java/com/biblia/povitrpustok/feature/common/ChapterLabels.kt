package com.biblia.povitrpustok.feature.common

import com.biblia.povitrpustok.domain.model.Chapter

/**
 * Presentation-only chapter label (READER-02 / implementation plan §10):
 * the source chapter number; when several records share a number (Job), a
 * source-order occurrence suffix "(1)", "(2)" disambiguates them. Never
 * persisted, never treated as the source number.
 *
 * Shared by the Books chapter grid and the Reader chapter selector so both
 * surfaces present duplicate source numbers identically.
 */
fun chapterDisplayLabel(chapter: Chapter, chapters: List<Chapter>): String {
    // Sort internally so callers need not guarantee order (READER-02).
    val siblings = chapters
        .filter { it.number == chapter.number }
        .sortedBy { it.position }
    val occurrence = siblings.indexOfFirst { it.id == chapter.id } + 1
    return if (siblings.size <= 1 || occurrence !in 1..siblings.size) {
        chapter.number.toString()
    } else {
        "${chapter.number} ($occurrence)"
    }
}
