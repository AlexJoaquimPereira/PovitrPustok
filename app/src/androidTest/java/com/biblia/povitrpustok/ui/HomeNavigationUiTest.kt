package com.biblia.povitrpustok.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TEST-03: executable Compose navigation coverage for the behaviors that were
 * previously only compile-verified: the Home attribution notice and back
 * navigation from a top-level destination.
 */
@RunWith(AndroidJUnit4::class)
class HomeNavigationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val notice =
        "Bible text © Archdiocese of Goa and Daman. All rights reserved."

    @Test
    fun home_shows_attribution_and_bookmarks_back_returns_home() {
        composeRule.onNodeWithText(notice).assertIsDisplayed()

        composeRule.onNodeWithText("Bookmarks").performClick()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()

        composeRule.onNodeWithText(notice).assertIsDisplayed()
    }

    @Test
    fun system_back_returns_home_from_books() {
        composeRule.onNodeWithText("Bible").performClick()
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(notice).assertIsDisplayed()
    }
}
