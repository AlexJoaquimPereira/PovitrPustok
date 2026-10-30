package com.biblia.povitrpustok.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2-07: predictive back on the two detail flows the existing back-stack tests
 * do not cover.
 *
 * `PovitrPustokShellUiTest` already covers tab Back and a More detail screen,
 * `HomeNavigationUiTest` covers Back from Books, and `BooksUiTest` uses the
 * Reader's own back button. What is left is the *system* Back from the Reader
 * and from Daily Reading, which is exactly the path the predictive-back flag
 * changes: Back is dispatched through the activity's
 * `OnBackInvokedDispatcher`, reaches Navigation Compose, and pops one entry.
 *
 * `OnBackPressedDispatcher.onBackPressed()` is the test-side equivalent of a
 * completed back gesture; it exercises the same dispatcher chain (including
 * the manifest opt-in) without depending on gesture timing.
 */
@RunWith(AndroidJUnit4::class)
class PredictiveBackUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun system_back_returns_to_the_chapter_grid_from_the_reader() {
        openFirstChapter()

        pressSystemBack()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("chapters.cell.1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chapters.cell.1").assertIsDisplayed()
        // One entry popped, not two: the reader is gone but its parent stayed.
        composeRule.onNodeWithTag("reader.topbar").assertDoesNotExist()
    }

    @Test
    fun system_back_returns_to_more_from_daily_reading() {
        composeRule.onNodeWithTag("nav.more").performClick()
        composeRule.onNodeWithTag("more.screen").assertIsDisplayed()
        composeRule.onNodeWithTag("more.daily").performClick()

        // The date navigator is screen chrome, so it is there whatever the
        // liturgical feed does: a network failure must not make Back untested.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("daily.date").fetchSemanticsNodes().isNotEmpty()
        }

        pressSystemBack()

        composeRule.onNodeWithTag("more.screen").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.more").assertIsSelected()
    }

    private fun openFirstChapter() {
        composeRule.onNodeWithTag("home.bible").performClick()
        composeRule.onNodeWithText("Utpoti").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("chapters.cell.1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chapters.cell.1").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.verse.1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressSystemBack() {
        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }
}
