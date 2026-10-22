package com.biblia.povitrpustok.data.di

import android.content.Context
import com.biblia.povitrpustok.core.database.AssetBibleDatabaseProvider
import com.biblia.povitrpustok.core.database.BibleAssetSource
import com.biblia.povitrpustok.core.database.BibleDatabaseProvider
import com.biblia.povitrpustok.data.app.AppDatabase
import com.biblia.povitrpustok.data.app.BookmarkRepositoryImpl
import com.biblia.povitrpustok.data.bible.BibleRepositoryImpl
import com.biblia.povitrpustok.data.bible.BookNameRepositoryImpl
import com.biblia.povitrpustok.data.bible.BibleSearchRepositoryImpl
import com.biblia.povitrpustok.data.bible.BookTitleRepositoryImpl
import com.biblia.povitrpustok.data.bible.ScriptureReferenceResolverImpl
import com.biblia.povitrpustok.data.liturgy.CatholicReadingsApi
import com.biblia.povitrpustok.data.liturgy.LiturgicalRepositoryImpl
import com.biblia.povitrpustok.domain.repository.BibleRepository
import com.biblia.povitrpustok.domain.repository.BibleSearchRepository
import com.biblia.povitrpustok.domain.repository.BookNameRepository
import com.biblia.povitrpustok.domain.repository.BookTitleRepository
import com.biblia.povitrpustok.domain.repository.BookmarkRepository
import com.biblia.povitrpustok.domain.repository.LiturgicalRepository
import com.biblia.povitrpustok.domain.repository.ScriptureReferenceResolver
import com.google.gson.Gson
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Data-layer Hilt module (implementation plan §46: feature → domain →
 * data → core).
 *
 * Phase 2/5/6 bindings:
 *  - [BibleDatabaseProvider] — lazy, read-only per-book Bible SQLite access.
 *  - [BibleRepository] — domain-facing repository over the Bible databases.
 *  - [BibleSearchRepository] — FTS5 full-text search over all books.
 *  - [ScriptureReferenceResolver] — reference strings to stable locations.
 *
 * Later phases add bindings here for the Room application database and
 * LiturgicalRepository — none of which exist yet, so no bindings are
 * declared for them.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideBibleAssetSource(
        @ApplicationContext context: Context
    ): BibleAssetSource = BibleAssetSource { assetPath -> context.assets.open(assetPath) }

    @Provides
    @Singleton
    fun provideBibleDatabaseProvider(
        @ApplicationContext context: Context,
        assetSource: BibleAssetSource
    ): BibleDatabaseProvider = AssetBibleDatabaseProvider(context, assetSource)

    @Provides
    @Singleton
    fun provideBibleRepository(
        provider: BibleDatabaseProvider
    ): BibleRepository = BibleRepositoryImpl(provider)

    @Provides
    @Singleton
    fun provideBibleSearchRepository(
        provider: BibleDatabaseProvider
    ): BibleSearchRepository = BibleSearchRepositoryImpl(provider)

    @Provides
    @Singleton
    fun provideBookTitleRepository(
        provider: BibleDatabaseProvider
    ): BookTitleRepository = BookTitleRepositoryImpl(provider)

    @Provides
    @Singleton
    fun provideBookNameRepository(
        titles: BookTitleRepository
    ): BookNameRepository = BookNameRepositoryImpl(titles)

    @Provides
    @Singleton
    fun provideScriptureReferenceResolver(
        repository: BibleRepository
    ): ScriptureReferenceResolver = ScriptureReferenceResolverImpl(repository)

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = AppDatabase.create(context)

    @Provides
    @Singleton
    fun provideBookmarkRepository(
        database: AppDatabase
    ): BookmarkRepository = BookmarkRepositoryImpl(database)

    @Provides
    @Singleton
    fun provideCatholicReadingsApi(): CatholicReadingsApi =
        Retrofit.Builder()
            .baseUrl(CATHOLIC_READINGS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(CatholicReadingsApi::class.java)

    @Provides
    @Singleton
    fun provideLiturgicalRepository(
        api: CatholicReadingsApi,
        appDatabase: AppDatabase
    ): LiturgicalRepository = LiturgicalRepositoryImpl(api, appDatabase)

    /** Injectable clock so Daily Reading "today" is deterministic in tests. */
    @Provides
    fun provideLocalDateProvider(): () -> LocalDate = { LocalDate.now() }

    private const val CATHOLIC_READINGS_BASE_URL = "https://cpbjr.github.io/catholic-readings-api/"
}
