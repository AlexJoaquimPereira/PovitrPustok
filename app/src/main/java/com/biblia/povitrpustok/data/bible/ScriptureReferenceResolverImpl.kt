package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.ResolvedScriptureReference
import com.biblia.povitrpustok.domain.model.ScriptureResolution
import com.biblia.povitrpustok.domain.reference.BookNameMatcher
import com.biblia.povitrpustok.domain.reference.ScriptureReferenceParser
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.ScriptureReferenceResolver
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Resolver over the real corpus (implementation plan §16, §17, §20).
 *
 * Parsing and database resolution are separate stages: the parser and
 * book matcher are pure and never touch SQL; this class only coordinates
 * them with [BibleRepository]. Resolution opens only the named book's
 * database (getChapters + optional getVerse) — never a 73-book scan.
 *
 * Chapter numbers are never assumed unique: if several chapter records
 * share the requested number (Job 26), the result is
 * [ScriptureResolution.Ambiguous] listing every candidate — never a
 * silent pick.
 */
@Singleton
class ScriptureReferenceResolverImpl @Inject constructor(
    private val bibleRepository: BibleRepository
) : ScriptureReferenceResolver {

    override suspend fun resolve(
        reference: String,
        contextBookId: String?
    ): ScriptureResolution {
        // Stage A — parse (pure, no database).
        val parsed = when (val result = ScriptureReferenceParser.parse(reference)) {
            is ScriptureReferenceParser.Result.Invalid ->
                return ScriptureResolution.Invalid(result.reason)
            is ScriptureReferenceParser.Result.Valid -> result.reference
        }

        // Stage B1 — book resolution.
        val bookId: String = if (parsed.book != null) {
            val book = BookNameMatcher.match(parsed.book)
                ?: return ScriptureResolution.NotFound("Unknown book: '${parsed.book}'.")
            book.id
        } else {
            val context = contextBookId
                ?: return ScriptureResolution.Invalid(
                    "Reference has no book name and no book context was provided."
                )
            if (BibleCatalog.byId(context) == null) {
                return ScriptureResolution.NotFound("Unknown book: '$context'.")
            }
            context
        }

        // Stage B2 — chapter resolution by chapter.number.
        val chapters = bibleRepository.getChapters(bookId)
        val chapterMatches = chapters.filter { it.number == parsed.chapter }
        if (chapterMatches.isEmpty()) {
            return ScriptureResolution.NotFound(
                "No chapter numbered ${parsed.chapter} in book '$bookId'."
            )
        }
        if (chapterMatches.size > 1) {
            return ScriptureResolution.Ambiguous(
                bookId = bookId,
                chapterNumber = parsed.chapter,
                candidates = chapterMatches
            )
        }
        val chapter = chapterMatches.single()

        // Stage B3 — verse resolution (chapter-only references stop here).
        val requestedStart = parsed.verseStart
        if (requestedStart == null) {
            return ScriptureResolution.Resolved(
                ResolvedScriptureReference(
                    bookId = bookId,
                    chapterId = chapter.id,
                    chapterNumber = chapter.number,
                    chapterPosition = chapter.position
                )
            )
        }

        val verse = bibleRepository.getVerse(bookId, chapter.id, requestedStart)
            ?: return ScriptureResolution.NotFound(
                "No verse starting at $requestedStart in chapter ${parsed.chapter} of '$bookId'."
            )

        return ScriptureResolution.Resolved(
            ResolvedScriptureReference(
                bookId = bookId,
                chapterId = chapter.id,
                chapterNumber = chapter.number,
                chapterPosition = chapter.position,
                verseId = verse.id,
                verseStart = verse.verseStart,
                // The resolved extent covers the source verse's own range and
                // any explicitly requested range end (a range is never split
                // into artificial verses; source verses are never invented).
                verseEnd = max(parsed.verseEnd ?: verse.verseEnd, verse.verseEnd)
            )
        )
    }
}
