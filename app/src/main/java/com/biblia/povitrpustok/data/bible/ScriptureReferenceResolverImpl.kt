package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ResolvedScriptureReference
import com.biblia.povitrpustok.domain.model.ResolvedScriptureSegment
import com.biblia.povitrpustok.domain.model.ScriptureResolution
import com.biblia.povitrpustok.domain.model.ScriptureSegment
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
 * A reference is resolved as an ordered list of segments:
 *  - non-continuous references keep their gaps ("Psalm 90:3-4, 5-6, 17");
 *  - cross-chapter references carry one segment per chapter
 *    ("John 3:16, 4:2");
 *  - a single chapter/verse reference is one segment.
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

    private sealed interface SegmentOutcome {
        data class Ok(val segment: ResolvedScriptureSegment) : SegmentOutcome
        data class Error(val resolution: ScriptureResolution) : SegmentOutcome
    }

    override suspend fun resolve(
        reference: String,
        contextBookId: String?
    ): ScriptureResolution {
        val parsed = when (val result = ScriptureReferenceParser.parse(reference)) {
            is ScriptureReferenceParser.Result.Invalid ->
                return ScriptureResolution.Invalid(result.reason)
            is ScriptureReferenceParser.Result.Valid -> result.reference
        }

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

        val chapters = bibleRepository.getChapters(bookId)
        val resolvedSegments = mutableListOf<ResolvedScriptureSegment>()

        for (segment in parsed.allSegments) {
            when (val outcome = resolveSegment(bookId, chapters, segment)) {
                is SegmentOutcome.Error -> return outcome.resolution
                is SegmentOutcome.Ok -> resolvedSegments += outcome.segment
            }
        }

        val first = resolvedSegments.first()
        return ScriptureResolution.Resolved(
            ResolvedScriptureReference(
                bookId = bookId,
                chapterId = first.chapterId,
                chapterNumber = first.chapterNumber,
                chapterPosition = first.chapterPosition,
                verseId = first.verseId,
                verseStart = first.verseStart,
                // Compatibility: a multi-segment reference's primary span
                // still reports the last requested endpoint.
                verseEnd = resolvedSegments.last().verseEnd ?: first.verseEnd,
                segments = resolvedSegments
            )
        )
    }

    private suspend fun resolveSegment(
        bookId: String,
        chapters: List<Chapter>,
        segment: ScriptureSegment
    ): SegmentOutcome {
        val chapterMatches = chapters.filter { it.number == segment.chapter }
        if (chapterMatches.isEmpty()) {
            // Single-chapter book: a bare number is a verse ("Jude 5").
            if (chapters.size == 1 && segment.verseStart == null) {
                return resolveVerseSegment(
                    bookId = bookId,
                    chapter = chapters.single(),
                    requestedStart = segment.chapter,
                    requestedEnd = null
                )
            }
            return SegmentOutcome.Error(
                ScriptureResolution.NotFound(
                    "No chapter numbered ${segment.chapter} in book '$bookId'."
                )
            )
        }
        if (chapterMatches.size > 1) {
            return SegmentOutcome.Error(
                ScriptureResolution.Ambiguous(
                    bookId = bookId,
                    chapterNumber = segment.chapter,
                    candidates = chapterMatches,
                    verseStart = segment.verseStart,
                    verseEnd = segment.verseEnd
                )
            )
        }

        val chapter = chapterMatches.single()
        if (segment.verseStart == null) {
            if (segment.verseEnd != null) {
                val endCoverage = bibleRepository.getVersesCovering(bookId, chapter.id, segment.verseEnd)
                if (endCoverage.isEmpty()) {
                    return SegmentOutcome.Error(
                        ScriptureResolution.NotFound(
                            "No verse covering range end ${segment.verseEnd} in chapter ${chapter.number} of '$bookId'."
                        )
                    )
                }
            }
            return SegmentOutcome.Ok(
                ResolvedScriptureSegment(
                    bookId = bookId,
                    chapterId = chapter.id,
                    chapterNumber = chapter.number,
                    chapterPosition = chapter.position,
                    verseEnd = segment.verseEnd
                )
            )
        }
        return resolveVerseSegment(bookId, chapter, segment.verseStart, segment.verseEnd, segment.openEnd)
    }

    private suspend fun resolveVerseSegment(
        bookId: String,
        chapter: Chapter,
        requestedStart: Int,
        requestedEnd: Int?,
        openEnd: Boolean = false
    ): SegmentOutcome {
        val verse = bibleRepository.getVersesCovering(bookId, chapter.id, requestedStart)
        if (verse.isEmpty()) {
            return SegmentOutcome.Error(
                ScriptureResolution.NotFound(
                    "No verse covering $requestedStart in chapter ${chapter.number} of '$bookId'."
                )
            )
        }
        if (verse.size > 1) {
            return SegmentOutcome.Error(
                ScriptureResolution.AmbiguousVerse(
                    bookId = bookId,
                    chapterId = chapter.id,
                    chapterNumber = chapter.number,
                    chapterPosition = chapter.position,
                    verseNumber = requestedStart,
                    candidates = verse
                )
            )
        }
        val sourceVerse = verse.single()

        if (!openEnd && requestedEnd != null && requestedEnd != requestedStart) {
            val endCoverage = bibleRepository.getVersesCovering(bookId, chapter.id, requestedEnd)
            if (endCoverage.isEmpty()) {
                return SegmentOutcome.Error(
                    ScriptureResolution.NotFound(
                        "No verse covering range end $requestedEnd in chapter ${chapter.number} of '$bookId'."
                    )
                )
            }
        }

        return SegmentOutcome.Ok(
            ResolvedScriptureSegment(
                bookId = bookId,
                chapterId = chapter.id,
                chapterNumber = chapter.number,
                chapterPosition = chapter.position,
                verseId = sourceVerse.id,
                verseStart = sourceVerse.verseStart,
                verseEnd = if (openEnd) null else max(requestedEnd ?: sourceVerse.verseEnd, sourceVerse.verseEnd),
                openEnd = openEnd
            )
        )
    }
}
