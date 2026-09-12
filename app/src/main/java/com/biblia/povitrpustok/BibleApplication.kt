package com.biblia.povitrpustok

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point and Hilt dependency-graph host.
 *
 * Data-layer bindings that later phases register (BibleDatabaseProvider,
 * BibleRepository, BibleSearchRepository, the application database, and
 * LiturgicalRepository) live in [com.biblia.povitrpustok.data.di].
 */
@HiltAndroidApp
class BibleApplication : Application()
