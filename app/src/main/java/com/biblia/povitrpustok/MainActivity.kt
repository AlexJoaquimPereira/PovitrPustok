package com.biblia.povitrpustok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biblia.povitrpustok.core.navigation.PovitrPustokNavHost
import com.biblia.povitrpustok.domain.repository.ReadingPreferencesRepository
import com.biblia.povitrpustok.ui.theme.BibleTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
                PovitrPustokNavHost()
            }
        }
    }
}
