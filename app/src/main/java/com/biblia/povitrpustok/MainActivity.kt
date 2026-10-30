package com.biblia.povitrpustok

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.domain.repository.ReadingPreferencesRepository
import com.biblia.povitrpustok.ui.PovitrPustokApp
import com.biblia.povitrpustok.ui.theme.BibleTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single activity host.
 *
 * Extends [AppCompatActivity] rather than `ComponentActivity` so the AppCompat
 * per-app-language delegate (F2-13) applies to this activity on API 30-32,
 * where the framework `localeConfig` picker does not exist yet. The theme
 * (`Theme.PovitrPustok`, Material 3 DayNight NoActionBar) already descends from
 * AppCompat, and AppCompatActivity is still a `ComponentActivity`, so Compose,
 * `enableEdgeToEdge()` and Hilt behave exactly as before.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var readingPreferencesRepository: ReadingPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // F-15: the persisted preferences are the single source of truth for
            // theme, Scripture size/leading and red letter. Collecting here means
            // a Settings change recomposes the theme without an app restart.
            val preferences by readingPreferencesRepository.preferences
                .collectAsStateWithLifecycle()
            BibleTheme(preferences = preferences) {
                PovitrPustokApp()
            }
        }
    }
}
