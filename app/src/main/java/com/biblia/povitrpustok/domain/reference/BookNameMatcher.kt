package com.biblia.povitrpustok.domain.reference

import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCatalog

/**
 * Case-insensitive book-name matching against the canonical [BibleCatalog]
 * (implementation plan §16, §18).
 *
 * Matching order:
 *  1. the small explicit abbreviation map (only the abbreviations the
 *     project actually supports — no comprehensive system is invented);
 *  2. exact match against id, englishName, or displayName.
 *
 * Returns null for unknown names; an unknown abbreviation is never
 * silently interpreted as another book.
 */
object BookNameMatcher {

    /** Abbreviation/alias -> BibleBook.id. Spec-listed + lectionary aliases. */
    private val ABBREVIATIONS: Map<String, String> = mapOf(
        "jas" to "jakob", // James
        "jm" to "jakob", // James
        "jn" to "juanv", // John
        "gen" to "Utpoti", // Genesis
        "psalm" to "Stotranchem_Pustok", // Psalms (lectionary singular form)
        "psalms" to "Stotranchem_Pustok",
        // REF-03: the catalog uses the Hebrew name "Qoheleth"; the common
        // lectionary form is Ecclesiastes.
        "ecclesiastes" to "Upodexok",
        "eccles" to "Upodexok",
        "eccl" to "Upodexok",
        "ecc" to "Upodexok",
        // Corpus metadata for these books lacks proper English names, so the
        // lectionary forms are provided as aliases (catalog data untouched).
        "1 thessalonians" to "tesalonikarank1",
        "2 thessalonians" to "tesalonikarank2",
        // F-12: the catalog now carries the full corpus title, so the short
        // forms shown before that change stay valid reference input.
        "poilem" to "korintkarant1",
        "dusrem" to "korintkarant2",
        "romkarant" to "romkarank",
        "tesalonikarank poilem" to "tesalonikarank1",
        "tesalonikarank dusrem" to "tesalonikarank2",
        "timotak poilem" to "timotak1",
        "timotak dusrem" to "timotak2"
    )

    fun match(rawName: String): BibleBook? {
        val normalized = WHITESPACE.replace(rawName.trim(), " ").trim().lowercase()
        if (normalized.isEmpty()) return null

        ABBREVIATIONS[normalized]?.let { id ->
            return BibleCatalog.byId(id)
        }

        return BibleCatalog.books.firstOrNull { book ->
            book.id.lowercase() == normalized ||
                book.englishName.lowercase() == normalized ||
                book.displayName.lowercase() == normalized
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
