package com.biblia.povitrpustok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.biblia.povitrpustok.core.navigation.PovitrPustokNavHost
import com.biblia.povitrpustok.ui.theme.BibleTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BibleTheme {
                PovitrPustokNavHost()
            }
        }
    }
}
