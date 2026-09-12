package com.biblia.povitrpustok.domain.model

/** Reading category (implementation plan §5). Not every day has all four. */
enum class ReadingType {
    FIRST_READING,
    RESPONSORIAL_PSALM,
    SECOND_READING,
    GOSPEL,
    OTHER
}

/**
 * One liturgical reading (implementation plan §5).
 *
 * [reference] is the raw Scripture reference string from the external API
 * (e.g. "James 1:5-8", "Psalm 112:1-2, 5-6, 7-8, 9"). The application's
 * local Bible databases remain the authoritative Bible text source; the
 * reference is converted through ScriptureReferenceResolver only for
 * navigation. [displayLabel] is an optional presentation title.
 */
data class LiturgicalReading(
    val type: ReadingType,
    val reference: String,
    val displayLabel: String? = null
)
