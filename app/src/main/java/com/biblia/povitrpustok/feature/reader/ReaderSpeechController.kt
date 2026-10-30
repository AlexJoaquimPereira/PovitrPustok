package com.biblia.povitrpustok.feature.reader

import com.biblia.povitrpustok.core.tts.AudioFocusController
import com.biblia.povitrpustok.core.tts.EnglishTextToSpeech
import com.biblia.povitrpustok.core.tts.TextToSpeechEngineFactory
import com.biblia.povitrpustok.core.tts.TtsStatus
import com.biblia.povitrpustok.core.tts.TtsUtteranceEvent
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the reader's speech bar is doing (F2-11). */
enum class ReaderSpeechPhase {
    /** Nothing is playing and no notice is shown. */
    Idle,

    /** A request was made; the engine is being created or the first word is pending. */
    Preparing,

    Speaking,
    Paused,

    /**
     * A request could not be spoken and the reason is shown instead. Nothing
     * was sent to any voice engine in this phase.
     */
    Unavailable
}

/**
 * Why [ReaderSpeechPhase.Unavailable] is showing. The screen turns these into
 * words; the controller never carries user-facing English (F-24).
 */
enum class ReaderSpeechIssue {
    /**
     * The loaded edition is not English. 1.3.1 ships only Romi Konkani, and
     * the app has no Konkani voice path: the text is never sent to the
     * English engine (F2-12).
     */
    NoKonkaniVoice,

    /** `LANG_MISSING_DATA`: the English voice data is not installed. */
    EnglishVoiceDataMissing,

    /** `LANG_NOT_SUPPORTED`: the engine has no English voice. */
    EnglishVoiceNotSupported,

    /** The engine failed to initialize, or refused the utterance. */
    EngineFailed,

    /** Another app held the audio and focus was denied. */
    AudioBusy
}

/**
 * Everything the speech bar renders. Immutable; [spokenSegment] is corpus text
 * (the word the engine is on), never UI copy.
 */
data class ReaderSpeechState(
    val phase: ReaderSpeechPhase = ReaderSpeechPhase.Idle,
    val verseId: Long? = null,
    /** The word/phrase the engine last reported via `onRangeStart`. */
    val spokenSegment: String? = null,
    /** 0f..1f through the verse when the engine reports ranges; null otherwise. */
    val progress: Float? = null,
    val issue: ReaderSpeechIssue? = null
) {
    /** True while a verse is requested, speaking, or paused on purpose. */
    val isActive: Boolean
        get() = phase == ReaderSpeechPhase.Preparing ||
            phase == ReaderSpeechPhase.Speaking ||
            phase == ReaderSpeechPhase.Paused

    /** True when the bar has something to show. */
    val isVisible: Boolean
        get() = phase != ReaderSpeechPhase.Idle
}

/**
 * Reader playback state machine (F2-11, F2-12).
 *
 * Owns exactly one [EnglishTextToSpeech] and one audio-focus request for the
 * lifetime of its Reader ViewModel. It never starts a service, never speaks in
 * the background, and releases both the engine and focus when shut down.
 *
 * Language rule (F2-12): only an English edition may reach the voice. Any
 * other edition — today Romi Konkani — produces
 * [ReaderSpeechIssue.NoKonkaniVoice] and **nothing is initialized, spoken, or
 * sent**, so there is no path where Romi Konkani is pronounced by an English
 * voice. There is no SSML language switching.
 *
 * Range rule: `onRangeStart` drives [ReaderSpeechState.spokenSegment] and
 * [ReaderSpeechState.progress], and the reader scrolls the verse into view.
 * Engines that do not report ranges still work; the verse stays at
 * verse-level position and pause/resume restarts the verse.
 *
 * Threading: [state] is a [StateFlow] and may be updated from the engine's
 * callback thread. [attach] must be called once by the owner so status changes
 * (initializing -> ready) can be observed.
 */
class ReaderSpeechController @Inject constructor(
    engineFactory: TextToSpeechEngineFactory,
    private val audioFocus: AudioFocusController
) {

    private val _state = MutableStateFlow(ReaderSpeechState())
    val state: StateFlow<ReaderSpeechState> = _state.asStateFlow()

    private val tts = EnglishTextToSpeech(engineFactory)

    private var scope: CoroutineScope? = null
    private var closed = false

    /** Full text of the verse being read, used for resume offsets and segments. */
    private var utteranceText: String = ""
    private var utteranceId: String? = null

    /** Characters of [utteranceText] spoken before the current utterance. */
    private var utteranceBase: Int = 0

    /** Characters of [utteranceText] already spoken (absolute). */
    private var spokenChars: Int = 0

    private var focusHeld = false

    /** True while a speak/resume is waiting for the engine to finish init. */
    private var pendingSpeak = false

    init {
        tts.onUtteranceEvent = ::onUtteranceEvent
    }

    /**
     * Binds the controller to the owner's scope so engine initialization can
     * complete asynchronously. Safe to call more than once; later calls are
     * ignored.
     */
    fun attach(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        scope.launch {
            tts.status.collect { status -> onStatus(status) }
        }
    }

    /**
     * Reads [text] for [verseId], but only when [languageTag] is English.
     * Starting a new verse replaces the previous one (the engine flushes its
     * queue).
     */
    fun speak(verseId: Long, text: String, languageTag: String) {
        if (closed) return
        val body = text.trim()
        if (body.isEmpty()) return
        if (!isEnglish(languageTag)) {
            // F2-12: never touch the engine for a non-English edition. Stop
            // anything still playing so the notice is not read over.
            silence()
            _state.value = ReaderSpeechState(
                phase = ReaderSpeechPhase.Unavailable,
                verseId = verseId,
                issue = ReaderSpeechIssue.NoKonkaniVoice
            )
            return
        }
        silence()
        utteranceText = body
        utteranceId = UTTERANCE_PREFIX + verseId
        utteranceBase = 0
        spokenChars = 0
        // Progress stays null until the engine reports a range: null means
        // "this engine cannot tell us where it is".
        _state.value = ReaderSpeechState(
            phase = ReaderSpeechPhase.Preparing,
            verseId = verseId
        )
        startPlayback()
    }

    /** Pause: stops the engine and gives focus back. Safe when not speaking. */
    fun pause() {
        if (_state.value.phase != ReaderSpeechPhase.Speaking) return
        tts.stop()
        releaseFocus()
        _state.value = _state.value.copy(phase = ReaderSpeechPhase.Paused, spokenSegment = null)
    }

    /** Resume from the last reported range, or from the start of the verse. */
    fun resume() {
        if (_state.value.phase != ReaderSpeechPhase.Paused) return
        // The engine has no pause: the remainder is spoken as a new utterance
        // whose offset is added back to every range it reports.
        utteranceBase = spokenChars.coerceIn(0, utteranceText.length)
        _state.value = _state.value.copy(phase = ReaderSpeechPhase.Preparing, spokenSegment = null)
        startPlayback()
    }

    /** Pause when speaking, resume when paused; no-op otherwise. */
    fun togglePause() {
        when (_state.value.phase) {
            ReaderSpeechPhase.Speaking -> pause()
            ReaderSpeechPhase.Paused -> resume()
            else -> Unit
        }
    }

    /** Stop: silences the engine, gives focus back, and hides the bar. */
    fun stop() {
        pendingSpeak = false
        silence()
        _state.value = ReaderSpeechState()
    }

    /** Dismisses an [ReaderSpeechPhase.Unavailable] notice. */
    fun dismissIssue() {
        if (_state.value.phase == ReaderSpeechPhase.Unavailable) {
            _state.value = ReaderSpeechState()
        }
    }

    /**
     * Releases the engine and focus. The controller is single-use afterwards;
     * the owning ViewModel calls this from `onCleared`.
     */
    fun shutdown() {
        if (closed) return
        closed = true
        pendingSpeak = false
        silence()
        tts.shutdown()
        _state.value = ReaderSpeechState()
    }

    private fun startPlayback() {
        when (tts.status.value) {
            TtsStatus.Ready -> beginUtterance()
            TtsStatus.Idle, TtsStatus.Initializing -> {
                pendingSpeak = true
                tts.initialize()
            }
            else -> unavailable(issueFor(tts.status.value))
        }
    }

    private fun beginUtterance() {
        if (closed) return
        val id = utteranceId ?: return
        val offset = utteranceBase.coerceIn(0, utteranceText.length)
        val remaining = utteranceText.substring(offset)
        if (remaining.isBlank()) {
            stop()
            return
        }
        if (!audioFocus.requestFocus(::onFocusLost)) {
            pendingSpeak = false
            unavailable(ReaderSpeechIssue.AudioBusy)
            return
        }
        focusHeld = true
        if (!tts.speak(remaining, id)) {
            pendingSpeak = false
            releaseFocus()
            unavailable(ReaderSpeechIssue.EngineFailed)
            return
        }
        pendingSpeak = false
        _state.value = _state.value.copy(
            phase = ReaderSpeechPhase.Speaking,
            issue = null,
            spokenSegment = null
        )
    }

    private fun onStatus(status: TtsStatus) {
        if (closed) return
        when (status) {
            TtsStatus.Ready -> if (pendingSpeak) beginUtterance()
            TtsStatus.MissingLanguageData -> unavailable(ReaderSpeechIssue.EnglishVoiceDataMissing)
            TtsStatus.LanguageNotSupported -> unavailable(ReaderSpeechIssue.EnglishVoiceNotSupported)
            is TtsStatus.InitializationFailed -> unavailable(ReaderSpeechIssue.EngineFailed)
            else -> Unit
        }
    }

    private fun onUtteranceEvent(event: TtsUtteranceEvent) {
        if (closed) return
        val current = utteranceId ?: return
        if (event.utteranceId != current) return
        val state = _state.value
        if (state.verseId == null) return
        when (event) {
            is TtsUtteranceEvent.Started ->
                _state.value = state.copy(phase = ReaderSpeechPhase.Speaking)

            is TtsUtteranceEvent.RangeStarted -> {
                val start = (utteranceBase + event.start).coerceIn(0, utteranceText.length)
                val end = (utteranceBase + event.end).coerceIn(start, utteranceText.length)
                spokenChars = start
                _state.value = state.copy(
                    phase = ReaderSpeechPhase.Speaking,
                    spokenSegment = utteranceText.substring(start, end).trim().ifEmpty { null },
                    progress = progressFor(start)
                )
            }

            is TtsUtteranceEvent.Finished -> {
                // Done means the whole verse was read; the bar closes and the
                // ViewModel clears the verse highlight. Clear the id so a
                // duplicate callback cannot reopen or mutate the state.
                utteranceId = null
                releaseFocus()
                _state.value = ReaderSpeechState()
            }

            is TtsUtteranceEvent.Failed -> {
                releaseFocus()
                unavailable(ReaderSpeechIssue.EngineFailed)
            }
        }
    }

    /** Another app took focus: pause instead of talking over it. */
    private fun onFocusLost() {
        if (closed) return
        focusHeld = false
        if (_state.value.phase == ReaderSpeechPhase.Speaking) {
            tts.stop()
            _state.value = _state.value.copy(
                phase = ReaderSpeechPhase.Paused,
                spokenSegment = null
            )
        }
    }

    private fun unavailable(issue: ReaderSpeechIssue) {
        if (_state.value.verseId == null) return
        pendingSpeak = false
        // No further event for this utterance may mutate the notice.
        utteranceId = null
        releaseFocus()
        _state.value = _state.value.copy(
            phase = ReaderSpeechPhase.Unavailable,
            issue = issue,
            spokenSegment = null
        )
    }

    private fun silence() {
        // A late onDone/onError must not act on a stopped or replaced verse.
        utteranceId = null
        tts.stop()
        releaseFocus()
    }

    private fun releaseFocus() {
        if (!focusHeld) return
        focusHeld = false
        audioFocus.abandonFocus()
    }

    private fun progressFor(chars: Int): Float? {
        if (utteranceText.isEmpty()) return null
        return (chars.toFloat() / utteranceText.length).coerceIn(0f, 1f)
    }

    private fun issueFor(status: TtsStatus): ReaderSpeechIssue = when (status) {
        TtsStatus.MissingLanguageData -> ReaderSpeechIssue.EnglishVoiceDataMissing
        TtsStatus.LanguageNotSupported -> ReaderSpeechIssue.EnglishVoiceNotSupported
        else -> ReaderSpeechIssue.EngineFailed
    }

    companion object {
        /** Utterance id prefix; the verse row id follows it. */
        const val UTTERANCE_PREFIX: String = "reader-verse-"

        /**
         * True only for an English language tag (`en`, `en-US`, `en-GB`).
         * Deliberately conservative: anything unknown is treated as
         * not-English, so it can never reach the English voice.
         */
        fun isEnglish(languageTag: String): Boolean =
            languageTag.trim().lowercase().let { it == "en" || it.startsWith("en-") }

        /**
         * A controller with no engine and no audio focus, for callers that do
         * not exercise playback (ViewModel tests). A non-English request still
         * reports [ReaderSpeechIssue.NoKonkaniVoice]; an English one reports
         * [ReaderSpeechIssue.EngineFailed] without crashing.
         */
        fun withoutEngine(): ReaderSpeechController = ReaderSpeechController(
            TextToSpeechEngineFactory { error("no text-to-speech engine in this build") },
            NoAudioFocus
        )

        private object NoAudioFocus : AudioFocusController {
            override fun requestFocus(onFocusLost: () -> Unit): Boolean = false
            override fun abandonFocus() = Unit
        }
    }
}
