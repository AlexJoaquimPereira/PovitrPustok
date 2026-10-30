package com.biblia.povitrpustok.core.tts

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the engine factory and the audio-focus handle, never a controller
 * or a live engine.
 *
 * F2-11 owns the [EnglishTextToSpeech] instance next to the Reader playback
 * state (for example in a ViewModel) and must call
 * [EnglishTextToSpeech.shutdown] when that owner is cleared. Keeping the
 * factory singleton is safe because it stores only the application context.
 * [AudioFocusController] is deliberately *not* a singleton: each playback owner
 * acquires and releases its own focus handle.
 */
@Module
@InstallIn(SingletonComponent::class)
object TtsModule {

    @Provides
    @Singleton
    fun provideTextToSpeechEngineFactory(
        @ApplicationContext context: Context
    ): TextToSpeechEngineFactory = AndroidTextToSpeechEngineFactory(context)

    @Provides
    fun provideAudioFocusController(
        @ApplicationContext context: Context
    ): AudioFocusController = AndroidAudioFocusController(context)
}
