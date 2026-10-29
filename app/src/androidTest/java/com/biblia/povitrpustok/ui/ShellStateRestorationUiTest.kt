package com.biblia.povitrpustok.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2-04: configuration-change (rotation) state restoration for the shell.
 *
 * Rotation is exercised with [androidx.test.core.app.ActivityScenario.recreate],
 * which destroys and recreates the activity. That is the honest boundary of
 * this test stack:
 *
 *  - the NavHost/back stack and every `rememberSaveable` value are restored
 *    from the saved instance state, the same channel Android uses after
 *    process death;
 *  - Hilt `ViewModel`s are **retained** across a configuration change, so a
 *    passing test here does not by itself prove that a screen rebuilds from a
 *    `SavedStateHandle`.
 *
 * True process death cannot be simulated from an instrumented test: killing
 * the app process also kills the instrumentation process that is running the
 * test. The closest reliable coverage for the one screen that persists a
 * selection is the ViewModel test
 * `DailyReadingViewModelTest.the_selected_date_survives_recreation_with_the_same_saved_state_handle`,
 * which builds a new ViewModel over a restored `SavedStateHandle`. Reader and
 * Daily Reading process-death restoration is therefore **not** asserted here;
 * it must not be inferred from these rotation tests.
 *
 * Route and state assertions use stable test tags or route arguments; the
 * reference-entry submit button is matched by its visible `Go` label.
 */
@RunWith(AndroidJUnit4::class)
class ShellStateRestorationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun home_survives_recreation_with_its_tab_selected() {
        composeRule.onNodeWithTag("home.notice").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.home").assertIsSelected()

        recreateActivity()

        waitForTag("home.notice")
        composeRule.onNodeWithTag("home.notice").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.home").assertIsSelected()
    }

    @Test
    fun books_tab_and_scroll_position_survive_recreation() {
        composeRule.onNodeWithTag("nav.bible").performClick()
        waitForTag("books.jump.old")
        composeRule.onNodeWithTag("nav.bible").assertIsSelected()

        // Scroll the same canonical list to the New Testament; the segment
        // reflects the scroll position, so it is the observable scroll state.
        composeRule.onNodeWithTag("books.jump.new").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasTestTag("books.jump.new") and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }

        recreateActivity()

        waitForTag("books.jump.old")
        composeRule.onNodeWithTag("nav.bible").assertIsSelected()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasTestTag("books.jump.new") and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("books.jump.new").assertIsSelected()
    }

    @Test
    fun more_tab_survives_recreation() {
        composeRule.onNodeWithTag("nav.more").performClick()
        waitForTag("more.screen")
        composeRule.onNodeWithTag("nav.more").assertIsSelected()

        recreateActivity()

        waitForTag("more.screen")
        composeRule.onNodeWithTag("more.screen").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.more").assertIsSelected()
    }

    @Test
    fun reader_deep_entry_keeps_its_book_chapter_and_passage() {
        // A reference entry is a real deep entry: it carries book, chapter,
        // verse target and passage arguments on the Reader route.
        composeRule.onNodeWithTag("home.reference").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Juanv 1:16")
        composeRule.onNodeWithText("Go").performClick()
        waitForTag("reader.passage_banner")
        waitForTag("reader.verse.16")
        composeRule.onNodeWithTag("reader.passage_banner").assertIsDisplayed()

        recreateActivity()

        waitForTag("reader.verse.16")
        composeRule.onNodeWithTag("reader.topbar").assertIsDisplayed()
        // The passage banner only renders when the route still carries the
        // passage arguments; the verse proves the same chapter is loaded.
        composeRule.onNodeWithTag("reader.passage_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("reader.verse.16").assertIsDisplayed()
    }

    @Test
    fun reader_scroll_position_survives_recreation() {
        openGenesisOne()
        // The reader list is the only scrollable on this screen.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("reader.verse.20"))
        composeRule.onNodeWithTag("reader.verse.20").assertIsDisplayed()

        recreateActivity()

        // The list state is remembered per chapter. If it were lost, the
        // recreated reader would be back at verse 1 and verse 20 would not
        // even be composed.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.verse.1").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("reader.verse.20").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reader.verse.20").assertIsDisplayed()
        composeRule.onNodeWithTag("reader.verse.1").assertDoesNotExist()
    }

    @Test
    fun daily_reading_selected_date_survives_recreation() {
        composeRule.onNodeWithTag("home.daily").performClick()
        waitForTag("daily.date")
        val today = dailyDateText()

        composeRule.onNodeWithTag("daily.prev").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { dailyDateText() != today }
        val browsedDay = dailyDateText()
        composeRule.onNodeWithTag("daily.today").assertIsDisplayed()

        recreateActivity()

        waitForTag("daily.date")
        assertEquals(
            "the browsed day must survive a configuration change",
            browsedDay,
            dailyDateText()
        )
        // A restored past day still offers the way back to today.
        composeRule.onNodeWithTag("daily.today").assertIsDisplayed()
    }

    @Test
    fun settings_and_about_survive_recreation_and_back_returns_to_settings() {
        composeRule.onNodeWithTag("home.notice").performClick()
        waitForTag("settings.back")

        recreateActivity()

        waitForTag("settings.back")
        composeRule.onNodeWithTag("settings.about").assertIsDisplayed()
        // A detail screen keeps its own chrome and no suite.
        composeRule.onNodeWithTag("nav.home").assertDoesNotExist()

        composeRule.onNodeWithTag("settings.about").performClick()
        waitForTag("about.screen")
        composeRule.onNodeWithTag("about.version").assertIsDisplayed()

        recreateActivity()

        waitForTag("about.screen")
        composeRule.onNodeWithTag("about.version").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.home").assertDoesNotExist()

        composeRule.onNodeWithTag("about.back").performClick()
        waitForTag("settings.back")
        composeRule.onNodeWithTag("settings.about").assertIsDisplayed()
    }

    private fun openGenesisOne() {
        composeRule.onNodeWithTag("home.bible").performClick()
        waitForTag("books.jump.old")
        composeRule.onNodeWithTag("books.row.Utpoti").performScrollTo().performClick()
        waitForTag("chapters.cell.1")
        composeRule.onNodeWithTag("chapters.cell.1").performClick()
        waitForTag("reader.verse.1")
    }

    private fun recreateActivity() {
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dailyDateText(): String =
        composeRule.onNodeWithTag("daily.date")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("")
}
