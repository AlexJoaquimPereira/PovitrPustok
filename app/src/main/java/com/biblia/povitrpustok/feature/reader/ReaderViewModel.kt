package com.biblia.povitrpustok.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.core.result.BibleDataException
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.model.ReaderPassageSpec
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.BookNameRepository
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reader state holder (implementation plan §17).
 *
 * Loads only the requested chapter (plus the lightweight chapter list of
 * the selected book for the selector/boundaries). All navigation uses
 * [Chapter.position]; chapter numbers are never used for sequential
 * navigation because the corpus contains duplicate source chapter numbers
 * (Job is the mandatory regression case).
 *
 * Exposes immutable [ReaderUiState] via [StateFlow]; never exposes mutable
 * state.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: BibleRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val bookNames: BookNameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /** One-shot confirmations (F-09); the screen shows them as snackbars. */
    private val _events = MutableSharedFlow<ReaderEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ReaderEvent> = _events.asSharedFlow()

    private var bookId: String? = null
    private var loadJob: Job? = null
    private var bookmarkObservation: Job? = null
    private var lastRequest: LoadRequest? = null

    /** READER-01: retry must replay the exact target/passage request. */
    private data class LoadRequest(
        val bookId: String,
        val chapterPosition: Int,
        val targetVerseId: Long?,
        val passageStart: Int?,
        val passageEnd: Int?,
        val passages: List<ReaderPassageSpec>
    )

    /** Serializes bookmark toggles so rapid taps cannot act on a stale snapshot (READER-05). */
    private val bookmarkToggleMutex = Mutex()

    /**
     * Loads [chapterPosition] of [bookId]; replaces the current state.
     *
     * [passages] carries non-continuous / cross-chapter segments. When it is
     * non-empty, every distinct chapter is loaded once and each requested
     * range is kept separate, so gap verses are never rendered.
     */
    fun load(
        bookId: String,
        chapterPosition: Int,
        targetVerseId: Long? = null,
        passageStart: Int? = null,
        passageEnd: Int? = null,
        passages: List<ReaderPassageSpec> = emptyList()
    ) {
        this.bookId = bookId
        lastRequest = LoadRequest(bookId, chapterPosition, targetVerseId, passageStart, passageEnd, passages)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // F-12: never a blank top-bar title, even when a first load
            // fails. Keep the resolved corpus name when the book is the
            // same one already displayed.
            val catalogName = BibleCatalog.byId(bookId)?.displayName.orEmpty()
            _uiState.update { state ->
                if (state.book?.id == bookId) {
                    state.copy(isLoading = true, errorMessage = null)
                } else {
                    state.copy(
                        isLoading = true,
                        errorMessage = null,
                        bookDisplayName = catalogName,
                        // Never show the previous book's English name (or its
                        // chapter list) next to the new local name.
                        book = null,
                        chapters = emptyList()
                    )
                }
            }
            try {
                val book = repository.getBook(bookId)
                val displayName = bookNames.displayName(bookId)
                val chapters = repository.getChapters(bookId)

                if (passages.isNotEmpty()) {
                    val chapterContents = passages
                        .map { it.chapterPosition }
                        .distinct()
                        .associateWith { repository.getChapter(bookId, it) }
                    val firstContent = chapterContents.getValue(passages.first().chapterPosition)
                    val passageChapters = passages
                        .groupBy { it.chapterPosition }
                        .map { (position, specs) ->
                            val content = chapterContents.getValue(position)
                            val firstVerse = content.verses.firstOrNull()?.verseStart ?: 1
                            val lastVerse = content.verses.lastOrNull()?.verseEnd ?: firstVerse
                            val ranges = specs.map { spec ->
                                (spec.verseStart ?: firstVerse)..(spec.verseEnd ?: lastVerse)
                            }
                            ReaderChapterPassage(
                                chapter = content.chapter,
                                headings = content.headings,
                                verses = content.verses,
                                passage = ReaderPassage(
                                    verseStart = ranges.first().first,
                                    verseEnd = ranges.last().last,
                                    ranges = ranges
                                )
                            )
                        }
                    val target = targetVerseId?.let { id ->
                        chapterContents.values.firstNotNullOfOrNull { content ->
                            content.verses.firstOrNull { it.id == id }
                        }
                    }
                    _uiState.value = ReaderUiState(
                        book = book,
                        bookDisplayName = displayName,
                        chapter = firstContent.chapter,
                        chapters = chapters,
                        headings = firstContent.headings,
                        verses = firstContent.verses,
                        isLoading = false,
                        targetVerseId = target?.id,
                        targetMissing = targetVerseId != null && target == null,
                        passageChapters = passageChapters
                    )
                    observeChapterBookmarks(bookId, firstContent.chapter.id)
                    bookmarkRepository.updateReadingState(bookId, firstContent.chapter.position)
                    return@launch
                }

                val content = repository.getChapter(bookId, chapterPosition)
                val target = targetVerseId?.let { id ->
                    content.verses.firstOrNull { it.id == id }
                }
                val passage = if (passageStart != null && passageEnd != null && passageEnd >= passageStart) {
                    ReaderPassage(passageStart, passageEnd)
                } else {
                    null
                }
                _uiState.value = ReaderUiState(
                    book = book,
                    bookDisplayName = displayName,
                    chapter = content.chapter,
                    chapters = chapters,
                    headings = content.headings,
                    verses = content.verses,
                    isLoading = false,
                    targetVerseId = if (target != null) target.id else null,
                    targetMissing = targetVerseId != null && target == null,
                    passage = passage
                )
                observeChapterBookmarks(bookId, content.chapter.id)
                bookmarkRepository.updateReadingState(bookId, chapterPosition)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: BibleDataException) {
                setLoadError(e.message ?: "Failed to load chapter.")
            } catch (e: Exception) {
                setLoadError("Failed to load chapter.")
            }
        }
    }

    /**
     * READER-06: a failed navigation must not leave a target, highlight, or
     * passage from the previous chapter active over the retained content.
     */
    private fun setLoadError(message: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = message,
                targetVerseId = null,
                targetMissing = false,
                selectedVerseId = null,
                highlightedVerseId = null,
                passage = null,
                passageChapters = emptyList(),
                // A failed load of a different book must not keep the old
                // book's English secondary name next to the new title (F-12).
                book = it.book?.takeIf { book -> book.id == bookId }
            )
        }
    }

    /** READER-01: replay the last request (including target/passage) after an error. */
    fun retry() {
        lastRequest?.let { request ->
            load(
                bookId = request.bookId,
                chapterPosition = request.chapterPosition,
                targetVerseId = request.targetVerseId,
                passageStart = request.passageStart,
                passageEnd = request.passageEnd,
                passages = request.passages
            )
        }
    }

    /** Observes the chapter-scoped bookmark set (Phase 9, no N+1). */
    private fun observeChapterBookmarks(bookId: String, chapterId: Long) {
        bookmarkObservation?.cancel()
        bookmarkObservation = viewModelScope.launch {
            bookmarkRepository.observeBookmarksForChapter(bookId, chapterId).collect { bookmarks ->
                _uiState.update { it.copy(bookmarkedVerseIds = bookmarks.map { bookmark -> bookmark.verseId }.toSet()) }
            }
        }
    }

    /**
     * Selects or deselects a verse by tapping it (corrective phase
     * Issue 6). The top-bar bookmark action then applies to this verse.
     * A new chapter load resets the selection.
     */
    fun onVerseSelected(verseId: Long) {
        _uiState.update { state ->
            state.copy(
                selectedVerseId = if (state.selectedVerseId == verseId) null else verseId
            )
        }
    }

    /**
     * Bookmarks or unbookmarks the currently selected (targeted) verse
     * using only ids already present in the loaded chapter.
     */
    fun toggleBookmark() {
        val verseId = _uiState.value.selectedVerseId ?: return
        toggleBookmarkFor(verseId)
    }

    /**
     * Long-press entry point (F-09): bookmarks or unbookmarks [verseId]
     * directly, without requiring a prior selection, and emits one
     * confirmation event for the snackbar. Undo is a separate, targeted
     * operation ([undoBookmarkChange]) that raises no confirmation.
     */
    fun toggleBookmarkFor(verseId: Long) {
        val state = _uiState.value
        val chapter = state.chapter ?: return
        val verse = state.verses.firstOrNull { it.id == verseId } ?: return
        val bookId = state.book?.id ?: return
        viewModelScope.launch {
            // READER-05: decide inside the serialized critical section from
            // the persisted state, not the possibly-stale UI snapshot. Rapid
            // taps therefore alternate add/remove instead of duplicating.
            bookmarkToggleMutex.withLock {
                val bookmarked = bookmarkRepository.isBookmarked(bookId, chapter.id, verseId)
                if (bookmarked) {
                    bookmarkRepository.removeBookmark(bookId, chapter.id, verseId)
                } else {
                    bookmarkRepository.addBookmark(
                        bookId = bookId,
                        chapterId = chapter.id,
                        chapterPosition = chapter.position,
                        chapterNumber = chapter.number,
                        verseId = verse.id,
                        verseStart = verse.verseStart,
                        verseEnd = verse.verseEnd
                    )
                }
                _events.tryEmit(
                    ReaderEvent.BookmarkChanged(
                        bookId = bookId,
                        chapterId = chapter.id,
                        chapterPosition = chapter.position,
                        chapterNumber = chapter.number,
                        verseId = verseId,
                        verseStart = verse.verseStart,
                        verseEnd = verse.verseEnd,
                        added = !bookmarked,
                        bookName = state.bookDisplayName
                    )
                )
            }
        }
    }

    /**
     * F-09 Undo: reverses the exact toggle carried by [event], using the ids
     * captured at that moment. It deliberately does not consult the current
     * chapter, so navigating away before the snackbar action cannot turn
     * Undo into a silent no-op.
     */
    fun undoBookmarkChange(event: ReaderEvent.BookmarkChanged) {
        viewModelScope.launch {
            bookmarkToggleMutex.withLock {
                if (event.added) {
                    bookmarkRepository.removeBookmark(
                        bookId = event.bookId,
                        chapterId = event.chapterId,
                        verseId = event.verseId
                    )
                } else {
                    bookmarkRepository.addBookmark(
                        bookId = event.bookId,
                        chapterId = event.chapterId,
                        chapterPosition = event.chapterPosition,
                        chapterNumber = event.chapterNumber,
                        verseId = event.verseId,
                        verseStart = event.verseStart,
                        verseEnd = event.verseEnd
                    )
                }
            }
        }
    }

    /**
     * Consumes the one-shot target: promotes [ReaderUiState.targetVerseId]
     * to [ReaderUiState.highlightedVerseId] and [ReaderUiState.selectedVerseId]
     * (the verse the bookmark action operates on). Called once by the scroll
     * effect so the reader never re-scrolls on recomposition.
     */
    fun onTargetApplied() {
        _uiState.update { state ->
            val target = state.targetVerseId
            if (target == null) {
                state
            } else {
                state.copy(targetVerseId = null, highlightedVerseId = target, selectedVerseId = target)
            }
        }
    }

    /** Ends the temporary highlight (e.g. after the highlight duration). */
    fun clearHighlight() {
        _uiState.update { it.copy(highlightedVerseId = null) }
    }

    /** Sequential previous chapter by position (stays inside the book). */
    fun previous() {
        val state = _uiState.value
        val current = state.chapter ?: return
        if (!state.canGoPrevious) return
        bookId?.let { load(it, current.position - 1) }
    }

    /** Sequential next chapter by position (stays inside the book). */
    fun next() {
        val state = _uiState.value
        val current = state.chapter ?: return
        if (!state.canGoNext) return
        bookId?.let { load(it, current.position + 1) }
    }

    /** Chapter selector: select a chapter by its source position. */
    fun selectChapter(position: Int) {
        bookId?.let { load(it, position) }
    }
}
