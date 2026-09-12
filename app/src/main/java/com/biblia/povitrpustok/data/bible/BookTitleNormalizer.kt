package com.biblia.povitrpustok.data.bible

/**
 * Normalization layer for database-derived book display metadata
 * (corrective phase Issue 2).
 *
 * The `introduction_blocks` title records legitimately contain the book
 * title, but their rendered text can carry metadata noise that is not part
 * of the name — in this corpus, runs of multiple whitespace (e.g. Joshua
 * "JOSHUACHEM  PUSTOK", Psalms "STOTRANCHEM  PUSTOK"). The database
 * remains the source of truth: only clearly non-name whitespace noise is
 * collapsed; meaningful punctuation (en-dash in "LEVI – XASTR", commas,
 * apostrophes, diacritics) is preserved verbatim. No broad character
 * stripping is performed.
 */
object BookTitleNormalizer {

    /**
     * Collapses every run of whitespace to a single space and trims the
     * result. Punctuation and diacritics are untouched.
     */
    fun normalizeTitleText(text: String): String =
        text.replace(WHITESPACE_RUN, " ").trim()

    private val WHITESPACE_RUN = Regex("\\s+")
}
