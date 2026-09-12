package com.biblia.povitrpustok.domain.repository

import com.biblia.povitrpustok.domain.model.ScriptureResolution

/**
 * Resolves Scripture reference strings into stable internal Bible
 * locations (implementation plan §16, §21).
 *
 * Pipeline: reference string -> parser (Stage A, pure) -> book resolution
 * (BibleCatalog/BookNameMatcher) -> chapter resolution by chapter.number
 * (never assumed unique) -> verse resolution.
 *
 * Normal user-input conditions are returned as explicit
 * [ScriptureResolution] states (Resolved / Ambiguous / NotFound /
 * Invalid) — never as exceptions or null sentinels. Only actual database
 * failures throw.
 */
interface ScriptureReferenceResolver {

    /**
     * @param reference the raw reference (e.g. "James 1:5", "James 1",
     *   "Genesis 17:11-12", "Job 26:5", bookless "3:16").
     * @param contextBookId optional book context used to resolve a
     *   bookless reference; if null and the reference has no book name,
     *   the result is Invalid.
     */
    suspend fun resolve(
        reference: String,
        contextBookId: String? = null
    ): ScriptureResolution
}
