package com.biblia.povitrpustok.domain.model

import java.util.Locale

/**
 * Deterministic Capitalize-Case for the displayed local-language book name
 * (corrective phase Issue 3).
 *
 * Applied ONLY to the local-language display name at presentation time —
 * never to the underlying database data, ids, or English names. The
 * English names in [com.biblia.povitrpustok.domain.model.BibleCatalog] are
 * already correct ("1 Samuel", "2 Corinthians", "Song of Songs",
 * "Revelation") and are never passed through this function.
 *
 * Rules:
 *  - each whitespace-separated word is title-cased (first letter upper,
 *    rest lower);
 *  - meaningful punctuation and digits inside a word are preserved
 *    ("LEVI – XASTR" -> "Levi – Xastr", "NIRÔP" -> "Nirôp");
 *  - a leading digit keeps the rest of the word lowercased ("1 SAMUEL" ->
 *    "1 Samuel");
 *  - deterministic: Locale.ROOT casing (no device-locale dependence).
 */
object BookDisplayName {

    fun capitalizeDisplayName(name: String): String =
        name.split(WHITESPACE)
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word -> capitalizeWord(word) }

    private fun capitalizeWord(word: String): String =
        word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

    private val WHITESPACE = Regex("\\s+")
}
