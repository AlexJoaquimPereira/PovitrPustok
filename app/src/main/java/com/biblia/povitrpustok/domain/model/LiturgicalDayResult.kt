package com.biblia.povitrpustok.domain.model

/**
 * Result of requesting a liturgical day (Phase 10, plan §16).
 *
 * Expected conditions (network unavailable, HTTP failure, malformed
 * response, no data) are represented as explicit [Error] states, never
 * thrown or collapsed into a generic message.
 */
sealed interface LiturgicalDayResult {

    /**
     * [cachedAt] is the epoch millis at which this day was first fetched
     * from the network, or `null` when the payload was just fetched (F-18).
     * The UI uses it to say that offline data is saved rather than fresh.
     *
     * [refreshFailed] is true only when a *forced* refresh could not reach
     * the network and the saved payload was served instead (P1-2), so the UI
     * can distinguish "already current" from "update failed" without losing
     * the offline data.
     */
    data class Success(
        val day: LiturgicalDay,
        val cachedAt: Long? = null,
        val refreshFailed: Boolean = false
    ) : LiturgicalDayResult

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
