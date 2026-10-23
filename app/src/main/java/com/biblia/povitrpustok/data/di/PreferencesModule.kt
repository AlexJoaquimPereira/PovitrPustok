package com.biblia.povitrpustok.data.di

import com.biblia.povitrpustok.data.preferences.SharedPreferencesReadingPreferencesRepository
import com.biblia.povitrpustok.domain.repository.ReadingPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** F-15: the persisted reading preferences, one instance per process. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {

    @Binds
    @Singleton
    abstract fun bindReadingPreferencesRepository(
        implementation: SharedPreferencesReadingPreferencesRepository
    ): ReadingPreferencesRepository
}
