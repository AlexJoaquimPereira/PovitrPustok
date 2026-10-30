package com.biblia.povitrpustok.core.tts

import java.io.Closeable
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Readiness of the English text-to-speech controller.
 *
 * The branches are intentionally explicit: the UI must be able to say whether
 * the engine is still initializing, ready, missing its English language data
 * (`-1`), or does not support English at all (`-2`) instead of failing
 * silently.
 */
sealed interface TtsStatus {
    /** No engine has been created yet. */
    data object Idle : TtsStatus

    /** The engine is being created and has not reported its init result. */
    data object Initializing : TtsStatus

    /** Initialization succeeded and English is available and selected. */
    data object Ready : TtsStatus

    /** `LANG_MISSING_DATA`: the English voice data is not installed. */
    data object MissingLanguageData : TtsStatus

    /** `LANG_NOT_SUPPORTED`: the installed engine has no English voice. */
    data object LanguageNotSupported : TtsStatus

    /** The platform engine reported an initialization error (usually `-1`). */
    data class InitializationFailed(val errorCode: Int) : TtsStatus

    /** [EnglishTextToSpeech.shutdown] has released the engine. */
    data object ShutDown : TtsStatus
}

/**
 * English-only controller over one [TextToSpeechEngine] (F2-10).
 *
 * Boundary rules:
 *  - No Compose, no Android `Context`, no background service, no audio focus,
 *    no SSML language switching, and no network. F2-11 wires this to the
 *    Reader action and owns audio focus.
 *  - English only. [initialize] checks and selects [Locale.ENGLISH]; there is
 *    no Konkani voice path, and callers must never pass Romi Konkani text to
 *    [speak].
 *  - The controller owns exactly one engine. Create it where the playback
 *    lives and call [shutdown] from the matching lifecycle owner; it is not a
 *    singleton and must not outlive its screen/ViewModel.
 *
 * Thread-safe: [initialize]/[shutdown]/the init callback synchronize on the
 * controller, and [status] is a [StateFlow].
 */
class EnglishTextToSpeech(
    private val engineFactory: TextToSpeechEngineFactory
) : Closeable {

    private val _status = MutableStateFlow<TtsStatus>(TtsStatus.Idle)
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    @Volatile
    private var engine: TextToSpeechEngine? = null

    /** Set when [initialize]'s factory calls back before [engine] is assigned. */
    private var pendingInitResult: Int? = null

    /** A controller is single-use; after [shutdown] it cannot be reinitialized. */
    private var closed = false

    /**
     * Optional sink for [TtsUtteranceEvent]s (F2-11). Set by the owner before
     * [initialize]; it is invoked on the engine's callback thread, so the
     * implementation must be thread-safe. `null` means "nobody is listening".
     */
    @Volatile
    var onUtteranceEvent: ((TtsUtteranceEvent) -> Unit)? = null

    /** True once the engine has been created, initialized, and is ready. */
    val isReady: Boolean
        get() = _status.value == TtsStatus.Ready

    /**
     * Creates the engine once. The returned [status] moves to [TtsStatus.Ready]
     * only after a successful init callback and an English availability check.
     * A second call is a no-op, including while initialization is in flight.
     */
    @Synchronized
    fun initialize() {
        if (closed || engine != null) return
        _status.value = TtsStatus.Initializing
        val created = try {
            engineFactory.create { result -> onInit(result) }
        } catch (e: Exception) {
            _status.value = TtsStatus.InitializationFailed(TtsResult.ERROR)
            return
        }
        // Registered before the engine is published so a very fast first
        // utterance cannot outrun its own progress events.
        created.setUtteranceListener { event -> onUtteranceEvent?.invoke(event) }
        engine = created
        // Handle a factory that invokes onInit synchronously from create().
        pendingInitResult?.let { result ->
            pendingInitResult = null
            onInitialized(result)
        }
    }

    @Synchronized
    private fun onInit(result: Int) {
        if (closed) return
        if (engine == null) {
            pendingInitResult = result
        } else {
            onInitialized(result)
        }
    }

    private fun onInitialized(result: Int) {
        if (result != TtsResult.SUCCESS) {
            _status.value = TtsStatus.InitializationFailed(result)
            return
        }
        val engine = engine ?: run {
            _status.value = TtsStatus.InitializationFailed(TtsResult.ERROR)
            return
        }
        // Android's isLanguageAvailable returns a negative code when English
        // cannot be used; -1 means the voice data is missing, -2 means the
        // engine does not support the language.
        val availability = statusFor(engine.isLanguageAvailable(Locale.ENGLISH))
        if (availability != TtsStatus.Ready) {
            _status.value = availability
            return
        }
        // isLanguageAvailable only queries. Selecting English makes the
        // wrapper genuinely English-only even when the device default differs.
        _status.value = statusFor(engine.setLanguage(Locale.ENGLISH))
    }

    private fun statusFor(result: Int): TtsStatus = when {
        result == TtsLanguageAvailability.MISSING_DATA -> TtsStatus.MissingLanguageData
        result == TtsLanguageAvailability.NOT_SUPPORTED -> TtsStatus.LanguageNotSupported
        result >= TtsLanguageAvailability.AVAILABLE -> TtsStatus.Ready
        // Any other negative code is treated as "not supported" rather than
        // pretending the engine is usable.
        else -> TtsStatus.LanguageNotSupported
    }

    /**
     * Speaks [text] only when [status] is [TtsStatus.Ready] and [text] is not
     * blank. Returns true when the engine accepted the utterance.
     *
     * [utteranceId] is forwarded to the engine and echoed on every
     * [TtsUtteranceEvent], so the caller can tell its own utterances apart.
     */
    fun speak(text: String, utteranceId: String = DEFAULT_UTTERANCE_ID): Boolean {
        if (!isReady || text.isBlank()) return false
        val engine = engine ?: return false
        return engine.speak(text, utteranceId) == TtsResult.SUCCESS
    }

    /** Stops playback and clears the queue. Safe when idle or not ready. */
    fun stop() {
        engine?.stop()
    }

    /**
     * Releases the engine. Idempotent: the engine's `shutdown` runs at most
     * once, and later [initialize] calls are ignored.
     */
    @Synchronized
    fun shutdown() {
        val current = engine
        engine = null
        pendingInitResult = null
        closed = true
        onUtteranceEvent = null
        _status.value = TtsStatus.ShutDown
        current?.shutdown()
    }

    override fun close() = shutdown()

    companion object {
        /** Utterance id used when the caller does not supply one. */
        const val DEFAULT_UTTERANCE_ID: String = "verse"
    }
}
