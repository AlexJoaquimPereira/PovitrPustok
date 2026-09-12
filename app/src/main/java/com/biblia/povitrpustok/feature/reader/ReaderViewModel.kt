package com.biblia.povitrpustok.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblia.povitrpustok.core.result.BibleDataException
import com.biblia.povitrpustok.domain.model.Chapter
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var bookId: String? = null
    private var loadJob: Job? = null
    private var bookmarkObservation: Job? = null

    /**
     * Loads [chapterPosition] of [bookId]; replaces the current state.
     *
     * A newer request supersedes an older one (Track A §D): the previous
     * in-flight load is cancelled, so stale chapters, errors, or reading
     * state can never overwrite the latest request.
     *
     * @param targetVerseId optional verse target (Phase 8): located by
     *   [Verse.id] in the already-loaded chapter content — never by
     *   verseStart alone, never re-queried from the database, never
     *   searched in other chapters. A missing id sets [ReaderUiState.targetMissing]
     *   and the chapter still renders normally.
     * @param passageStart / @param passageEnd optional requested passage
     *   (corrective phase Issue 5): when both present, the Reader renders
     *   only that verse range (targeted passage mode).
     */
    fun load(
        bookId: String,
        chapterPosition: Int,
        targetVerseId: Long? = null,
        passageStart: Int? = null,
        passageEnd: Int? = null
    ) {
        this.bookId = bookId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val book = repository.getBook(bookId)
                val chapters = repository.getChapters(bookId)
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
                // Reading state: persist the last opened location once per
                // meaningful navigation — never on scroll.
                bookmarkRepository.updateReadingState(bookId, chapterPosition)
            } catch (e: BibleDataException) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load chapter.")
                }
            }
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
    fun toggleBookmark() {        val state = _uiState.value
        val verseId = state.selectedVerseId ?: return
        val chapter = state.chapter ?: return
        val verse = state.verses.firstOrNull { it.id == verseId } ?: return
        val bookId = state.book?.id ?: return
        viewModelScope.launch {
            if (state.bookmarkedVerseIds.contains(verseId)) {
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
