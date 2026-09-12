package com.biblia.povitrpustok.domain.model

/**
 * Result of requesting a liturgical day (Phase 10, plan §16).
 *
 * Expected conditions (network unavailable, HTTP failure, malformed
 * response, no data) are represented as explicit [Error] states, never
 * thrown or collapsed into a generic message.
 */
sealed interface LiturgicalDayResult {

    data class Success(val day: LiturgicalDay) : LiturgicalDayResult

    data class Error(val kind: LiturgicalErrorKind, val message: String) : LiturgicalDayResult
}

enum class LiturgicalErrorKind {
    /** No network / connection failure / timeout. */
    NETWORK,

    /** HTTP error status. */
    HTTP,

    /** Malformed JSON or missing required fields. */
    MALFORMED_RESPONSE,

    /** The day exists but has no usable reading/calendar data. */
    NO_DATA
}
