package com.biblia.povitrpustok.perf

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First-use performance smoke (F1-21).
 *
 * Measures the real first-use sequence on a device/emulator: Books opens
 * (catalog frame + the one-time title scan), the first search (scan fallback
 * on builds without FTS5), and the first chapter open. The assertions are
 * deliberately generous safety budgets; the measured numbers are logged with
 * the `PerfBaseline` tag and recorded in `docs/performance.md`.
 *
 * Run isolated after clearing app data for a true first-use measurement:
 * `adb shell pm clear com.biblia.povitrpustok` then run this class. In the
 * full suite the corpus/title caches may already be warm, so the numbers are
 * a smoke check rather than the published baseline.
 */
@RunWith(AndroidJUnit4::class)
class FirstUsePerformanceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun first_use_flows_stay_within_generous_budgets() {
        waitForTag("home.bible")

        // Books: first catalog frame, then the one-time title scan.
        val booksStart = System.nanoTime()
        composeRule.onNodeWithTag("home.bible").performScrollTo().performClick()
        waitForTag("books.row.Utpoti")
        val booksFirstFrameMs = elapsedMs(booksStart)
        waitForTagGone("books.loading", timeoutMillis = 30_000)
        val booksTitlesMs = elapsedMs(booksStart)

        // First search: the emulator has no FTS5, so this exercises the scan fallback.
        pressBack()
        waitForTag("home.search")
        val searchStart = System.nanoTime()
        composeRule.onNodeWithTag("home.search").performScrollTo().performClick()
        waitForTag("search.field")
        composeRule.onNodeWithTag("search.field").performTextInput("subhagi")
        waitForTag("search.count", timeoutMillis = 30_000)
        val searchMs = elapsedMs(searchStart)

        // First chapter open: Genesis 1, including the first reader parse/render.
        pressBack()
        waitForTag("home.bible")
        val chapterStart = System.nanoTime()
        composeRule.onNodeWithTag("home.bible").performScrollTo().performClick()
        waitForTag("books.row.Utpoti")
        composeRule.onNodeWithTag("books.row.Utpoti").performClick()
        waitForTag("chapters.cell.1")
        composeRule.onNodeWithTag("chapters.cell.1").performClick()
        waitForText("Orombak", timeoutMillis = 30_000)
        val chapterMs = elapsedMs(chapterStart)

        Log.i(
            TAG,
            "booksFirstFrameMs=$booksFirstFrameMs booksTitlesMs=$booksTitlesMs " +
                "searchMs=$searchMs chapterMs=$chapterMs"
        )

        assertTrue("first Books frame took ${booksFirstFrameMs}ms", booksFirstFrameMs < 10_000)
        assertTrue("first Books titles took ${booksTitlesMs}ms", booksTitlesMs < 30_000)
        assertTrue("first search took ${searchMs}ms", searchMs < 30_000)
        assertTrue("first chapter open took ${chapterMs}ms", chapterMs < 30_000)
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun waitForTagGone(tag: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun elapsedMs(startNanos: Long): Long =
        (System.nanoTime() - startNanos) / 1_000_000

    private companion object {
        const val TAG = "PerfBaseline"
    }
}
