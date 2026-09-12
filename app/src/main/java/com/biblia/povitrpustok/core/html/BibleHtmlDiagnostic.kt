package com.biblia.povitrpustok.core.html

/**
 * Parser diagnostics — every deviation from the supported vocabulary is
 * reported instead of being silently discarded (implementation plan §39,
 * Part C of Phase 3). [offset] is the character offset in the source.
 */
enum class BibleHtmlDiagnosticKind {
    /** A well-formed tag outside the supported vocabulary. */
    UNKNOWN_TAG,

    /** An opening tag left unclosed at end of input. */
    UNCLOSED_TAG,

    /** A closing tag with no matching open tag. */
    UNBALANCED_CLOSE,

    /** A `<` sequence that is not a valid tag (kept as literal text). */
    MALFORMED_TAG,

    /** An HTML entity that could not be decoded (kept literally). */
    UNKNOWN_ENTITY
}

data class BibleHtmlDiagnostic(
    val kind: BibleHtmlDiagnosticKind,
    val message: String,
    val offset: Int
)
