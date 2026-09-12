package com.biblia.povitrpustok.core.database

/**
 * One full-text search hit within a single book database (Phase 5;
 * corrective phase Issue 1).
 *
 * Carries the stable internal identifiers needed to navigate to the exact
 * verse; [bookId] is attached by the search repository (the database row
 * itself does not contain the catalog book id). [plainText] is the raw
 * `verses.text_plain` content — never HTML — from which the repository
 * derives the compact result snippet (FTS5 `snippet()` is deliberately not
 * used: the external-content `verses_fts` schema does not match the
 * `verses` content table, and FTS5 auxiliary functions over mismatched
 * external-content tables are not portable across SQLite builds).
 */
data class BibleVerseHit(
    val verseId: Long,
    val chapterId: Long,
    val chapterPosition: Int,
    val chapterNumber: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val plainText: String
)
