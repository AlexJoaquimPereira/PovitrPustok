package com.biblia.povitrpustok.domain.model

/**
 * A Bible book as application metadata (implementation plan §12).
 *
 * The canonical book list lives in [BibleCatalog]; rendering it never
 * opens a database. [databaseAsset] is the APK asset path of the book's
 * read-only SQLite database.
 */
data class BibleBook(
    val id: String,
    val displayName: String,
    val englishName: String,
    val testament: Testament,
    val position: Int,
    val databaseAsset: String
)
