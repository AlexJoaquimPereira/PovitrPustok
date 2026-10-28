package com.biblia.povitrpustok.domain.model

/**
 * One Bible edition (F1-14).
 *
 * The edition dimension is deliberately small for now: one Konkani (Romi)
 * edition, with the asset pattern and canon declared so an English edition
 * can land in 1.4 without re-keying the provider or routes.
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
 * The editions the app knows about. Only the shipped Konkani edition exists
 * today; [DEFAULT] is the id every existing caller uses implicitly.
 */
object BibleEditions {
    const val KONKANI_ID = "konkani-romi"

    val KONKANI: Edition = Edition(
        id = KONKANI_ID,
        languageTag = "kok-Latn",
        label = "Konkani (Romi)",
        assetPattern = "bible/{bookId}.sqlite",
        canon = BibleCatalog.books.map { it.id }.toSet(),
        licence = "© Archdiocese of Goa and Daman; used with permission"
    )

    val DEFAULT: Edition = KONKANI

    /**
     * Every edition id [byId] can resolve. Rights-register coverage and any
     * future edition picker iterate this set instead of duplicating ids.
     */
    val all: Set<String> = setOf(KONKANI_ID)

    fun byId(id: String?): Edition? = when (id) {
        null, KONKANI_ID -> KONKANI
        else -> null
    }
}
