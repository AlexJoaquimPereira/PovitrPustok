package com.biblia.povitrpustok.domain.model

/**
 * One Bible edition (F1-14).
 *
 * The edition dimension stays deliberately small: the shipped Konkani (Romi)
 * edition plus two bundled English public-domain editions. The asset pattern
 * and canon are declared per edition so the database provider and the routes
 * never key on a language.
 */
data class Edition(
    val id: String,
    val languageTag: String,
    val label: String,
    val assetPattern: String,
    val canon: Set<String>,
    val licence: String
) {
    /** Resolve a book id to its APK asset path for this edition. */
    fun assetFor(bookId: String): String =
        assetPattern.replace(BOOK_ID_PLACEHOLDER, bookId)

    private companion object {
        const val BOOK_ID_PLACEHOLDER = "{bookId}"
    }
}

/**
 * The editions the app knows about. [DEFAULT] is the id every existing
 * caller uses implicitly; the user picks the reading edition in Settings.
 */
object BibleEditions {
    const val KONKANI_ID = "konkani-romi"
    const val WEBC_ID = "webc"
    const val DRA_ID = "dra"

    val KONKANI: Edition = Edition(
        id = KONKANI_ID,
        languageTag = "kok-Latn",
        label = "Konkani (Romi)",
        assetPattern = "bible/{bookId}.sqlite",
        canon = BibleCatalog.books.map { it.id }.toSet(),
        licence = "© Archdiocese of Goa and Daman; used with permission"
    )

    /** World English Bible (Catholic), 73-book canon, public domain. */
    val WEBC: Edition = Edition(
        id = WEBC_ID,
        languageTag = "en",
        label = "World English Bible (Catholic)",
        assetPattern = "bible/webc/{bookId}.sqlite",
        canon = BibleCatalog.books.map { it.id }.toSet(),
        licence = "Public domain"
    )

    /**
     * Douay-Rheims American Edition (1899), 73-book canon, public domain.
     */
    val DRA: Edition = Edition(
        id = DRA_ID,
        languageTag = "en",
        label = "Douay-Rheims 1899",
        assetPattern = "bible/dra/{bookId}.sqlite",
        canon = BibleCatalog.books.map { it.id }.toSet(),
        licence = "Public domain"
    )

    val DEFAULT: Edition = KONKANI

    /**
     * Every edition id [byId] can resolve. Rights-register coverage and any
     * edition picker iterate this set instead of duplicating ids.
     */
    val all: Set<String> = setOf(KONKANI_ID, WEBC_ID, DRA_ID)

    fun byId(id: String?): Edition? = when (id) {
        null, KONKANI_ID -> KONKANI
        WEBC_ID -> WEBC
        DRA_ID -> DRA
        else -> null
    }

    /**
     * True for an edition whose text is English. Unknown ids are treated as
     * not English, so callers fall back to the shipped Konkani behaviour.
     */
    fun isEnglish(editionId: String): Boolean =
        byId(editionId)?.languageTag?.startsWith("en") == true
}
