package com.biblia.povitrpustok.feature.reader

/**
 * Clipboard/share text for one verse (F-23).
 *
 * Uses the verse's `plainText` — never HTML — and prefixes the reference so a
 * pasted verse still says where it came from. A source range keeps its real
 * range (`1:11–12`); ordinary verses stay single.
 */
fun verseClipboardText(
    bookName: String,
    chapterNumber: Int,
    verseStart: Int,
    verseEnd: Int,
    plainText: String
): String {
    val reference = if (verseEnd > verseStart) {
        "$chapterNumber:$verseStart–$verseEnd"
    } else {
        "$chapterNumber:$verseStart"
    }
    val body = plainText.trim()
    return if (body.isEmpty()) "$bookName $reference" else "$bookName $reference\n$body"
}
