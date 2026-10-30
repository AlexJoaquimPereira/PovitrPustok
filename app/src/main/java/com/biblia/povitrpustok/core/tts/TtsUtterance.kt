package com.biblia.povitrpustok.core.tts

/**
 * Progress reported by an engine for one utterance (F2-11).
 *
 * The port deliberately reports the smallest useful set: the platform's
 * `UtteranceProgressListener` also carries synthesis frames, which nothing in
 * this app needs. [RangeStarted] mirrors `onRangeStart` and is the only way a
 * caller can follow the spoken word; engines that do not implement it simply
 * never emit it, and the caller must fall back to a coarser (verse-level)
 * position.
 *
 * Events are delivered on the engine's callback thread, which is not the main
 * thread. Receivers must be thread-safe and must not touch Compose state
 * directly.
 */
sealed interface TtsUtteranceEvent {

    /** The utterance id the caller passed to `EnglishTextToSpeech.speak`. */
    val utteranceId: String

    /** Playback of the utterance began. */
    data class Started(override val utteranceId: String) : TtsUtteranceEvent

    /**
     * The engine is about to speak `text.substring(start, end)` of the
     * utterance, measured in characters of the text passed to `speak`.
     */
    data class RangeStarted(
        override val utteranceId: String,
        val start: Int,
        val end: Int
    ) : TtsUtteranceEvent

    /** The utterance finished normally. */
    data class Finished(override val utteranceId: String) : TtsUtteranceEvent

    /** The engine could not speak the utterance. */
    data class Failed(override val utteranceId: String) : TtsUtteranceEvent
}
