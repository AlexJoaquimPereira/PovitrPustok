package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Chapter
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
 *
 * Verse resolution uses the source range (REF-01): a requested number may
 * be covered by a combined record, and duplicate source numbers surface as
 * [ScriptureResolution.AmbiguousVerse]. A requested range end must also be
 * covered by chapter data (REF-02), otherwise the result is `NotFound`.
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
            // REF-03: in a single-chapter book a bare number is a verse
            // ("Jude 5"), not a chapter locator.
            if (chapters.size == 1 && parsed.verseStart == null) {
                return resolveVerse(
                    bookId = bookId,
                    chapter = chapters.single(),
                    requestedStart = parsed.chapter,
                    requestedEnd = null
                )
            }
            return ScriptureResolution.NotFound(
                "No chapter numbered ${parsed.chapter} in book '$bookId'."
            )
        }
        if (chapterMatches.size > 1) {
            return ScriptureResolution.Ambiguous(
                bookId = bookId,
                chapterNumber = parsed.chapter,
                candidates = chapterMatches,
                // REF-04: keep the requested verse/range so choosing a
                // duplicate chapter does not silently drop the target.
                verseStart = parsed.verseStart,
                verseEnd = parsed.verseEnd
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

        return resolveVerse(bookId, chapter, requestedStart, parsed.verseEnd)
    }

    /** Resolves a verse/range within one known chapter (REF-01/02, REF-03). */
    private suspend fun resolveVerse(
        bookId: String,
        chapter: Chapter,
        requestedStart: Int,
        requestedEnd: Int?
    ): ScriptureResolution {
        val verse = bibleRepository.getVersesCovering(bookId, chapter.id, requestedStart)
        if (verse.isEmpty()) {
            return ScriptureResolution.NotFound(
                "No verse covering $requestedStart in chapter ${chapter.number} of '$bookId'."
            )
        }
        if (verse.size > 1) {
            return ScriptureResolution.AmbiguousVerse(
                bookId = bookId,
                chapterId = chapter.id,
                chapterNumber = chapter.number,
                chapterPosition = chapter.position,
                verseNumber = requestedStart,
                candidates = verse
            )
        }
        val sourceVerse = verse.single()

        // REF-02: a requested range end must be supported by chapter data.
        if (requestedEnd != null && requestedEnd != requestedStart) {
            val endCoverage = bibleRepository.getVersesCovering(bookId, chapter.id, requestedEnd)
            if (endCoverage.isEmpty()) {
                return ScriptureResolution.NotFound(
                    "No verse covering range end $requestedEnd in chapter ${chapter.number} of '$bookId'."
                )
            }
        }

        return ScriptureResolution.Resolved(
            ResolvedScriptureReference(
                bookId = bookId,
                chapterId = chapter.id,
                chapterNumber = chapter.number,
                chapterPosition = chapter.position,
                verseId = sourceVerse.id,
                verseStart = sourceVerse.verseStart,
                // The resolved extent covers the source verse's own range and
                // any explicitly requested range end (a range is never split
                // into artificial verses; source verses are never invented).
                verseEnd = max(requestedEnd ?: sourceVerse.verseEnd, sourceVerse.verseEnd)
            )
        )
    }
}
