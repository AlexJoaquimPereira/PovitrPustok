package com.biblia.povitrpustok.core.html

/**
 * HTML entity decoding for the corpus vocabulary (pure Kotlin).
 *
 * Covers the entities actually observed in the Bible databases (Latin-1
 * accented letters, quotes, dashes, non-breaking space) plus the standard
 * set and numeric references. Unknown entities are left verbatim and
 * reported by the parser.
 */
object BibleHtmlEntities {

    private val NAMED: Map<String, String> = buildMap {
        put("amp", "&")
        put("lt", "<")
        put("gt", ">")
        put("quot", "\"")
        put("apos", "'")
        put("nbsp", "\u00A0")
        put("lsquo", "\u2018")
        put("rsquo", "\u2019")
        put("ldquo", "\u201C")
        put("rdquo", "\u201D")
        put("ndash", "\u2013")
        put("mdash", "\u2014")
        // Latin-1 accented letters present in the Konkani corpus
        put("ecirc", "\u00EA")
        put("Ecirc", "\u00CA")
        put("ocirc", "\u00F4")
        put("Ocirc", "\u00D4")
        put("atilde", "\u00E3")
        put("Atilde", "\u00C3")
        put("ograve", "\u00F2")
        put("Ograve", "\u00D2")
        put("egrave", "\u00E8")
        put("Egrave", "\u00C8")
        put("aacute", "\u00E1")
        put("Aacute", "\u00C1")
        put("eacute", "\u00E9")
        put("Eacute", "\u00C9")
        put("iacute", "\u00ED")
        put("Iacute", "\u00CD")
        put("oacute", "\u00F3")
        put("Oacute", "\u00D3")
        put("uacute", "\u00FA")
        put("Uacute", "\u00DA")
        put("ucirc", "\u00FB")
        put("Ucirc", "\u00DB")
        put("auml", "\u00E4")
        put("ouml", "\u00F6")
        put("uuml", "\u00FC")
        put("agrave", "\u00E0")
        put("ccedil", "\u00E7")
    }

    /**
     * Decodes the entity starting at [html].[offset] (which must point at
     * `&`). Returns the replacement string, or null when the sequence is
     * not a decodable entity (the caller keeps it literally and may emit
     * an [BibleHtmlDiagnosticKind.UNKNOWN_ENTITY] diagnostic).
     */
    fun decode(html: String, offset: Int): String? {
        val semi = html.indexOf(';', offset)
        if (semi < 0 || semi - offset > 12) return null
        val body = html.substring(offset + 1, semi)
        if (body.isEmpty()) return null
        if (body[0] == '#') {
            val code = body.drop(1)
            val value = if (code.startsWith("x", ignoreCase = true)) {
                code.drop(1).toIntOrNull(16)
            } else {
                code.toIntOrNull(10)
            }
            val resolved = value
            return if (resolved != null && resolved > 0 && resolved <= 0x10FFFF) {
                String(Character.toChars(resolved))
            } else {
                null
            }
        }
        return NAMED[body]
    }
}
