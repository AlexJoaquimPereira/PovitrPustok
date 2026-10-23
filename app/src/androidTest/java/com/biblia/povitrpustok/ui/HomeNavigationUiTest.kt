package com.biblia.povitrpustok.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.MainActivity
import androidx.compose.ui.unit.dp
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
    fun home_actions_expose_stable_tags_and_notice() {
        listOf(
            "home.bible",
            "home.search",
            "home.bookmarks",
            "home.reference",
            "home.daily",
            "home.notice",
        ).forEach { tag -> composeRule.onNodeWithTag(tag).assertIsDisplayed() }
    }

    @Test
    fun home_primary_action_opens_books() {
        // The one primary action is Bible: the front door, not a menu item.
        composeRule.onNodeWithTag("home.bible").assertIsDisplayed().performClick()

        // A Books-only node, not just a shared title string.
        composeRule.onNodeWithText("Old Testament").assertIsDisplayed()
    }

    @Test
    fun home_shows_the_masthead_and_one_node_per_destination_label() {
        composeRule.onNodeWithTag("home.overline").assertIsDisplayed()
        composeRule.onNodeWithTag("home.hero").assertIsDisplayed()
        composeRule.onNodeWithTag("home.grid").assertIsDisplayed()

        // F-11: the labels stay unique click targets; subtitles must not
        // introduce a second match for any of them.
        listOf("Bible", "Search", "Bookmarks").forEach { label ->
            composeRule.onAllNodesWithText(label).assertCountEquals(1)
        }
    }

    @Test
    fun search_is_reachable_back_works_and_shows_a_result_count() {
        composeRule.onNodeWithTag("home.search").performClick()
        composeRule.onNodeWithTag("search.field").performTextInput("subhagi")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("search.count")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("search.count").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag("home.notice").assertIsDisplayed()
    }

    @Test
    fun search_returns_james_results() {
        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("subhagi")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            // The query field plus at least one rendered result snippet.
            composeRule.onAllNodesWithText("subhagi", substring = true)
                .fetchSemanticsNodes().size > 1
        }
        composeRule.onNodeWithText("No results found.").assertDoesNotExist()
    }

    @Test
    fun system_back_returns_home_from_books() {
        composeRule.onNodeWithText("Bible").performClick()
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(notice).assertIsDisplayed()
    }

    @Test
    fun chapter_cells_meet_the_48dp_touch_target() {
        composeRule.onNodeWithTag("home.bible").performClick()
        composeRule.onNodeWithText("Utpoti").performScrollTo().performClick()

        composeRule.onNodeWithTag("chapters.cell.1")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun empty_bookmarks_offers_browse_books() {
        composeRule.onNodeWithTag("home.bookmarks").performClick()
        composeRule.onNodeWithText("No bookmarks yet.").assertIsDisplayed()

        composeRule.onNodeWithTag("bookmarks.empty.action").performClick()

        // A Books-only node, not just the shared title string.
        composeRule.onNodeWithText("Old Testament").assertIsDisplayed()
    }

    @Test
    fun long_press_bookmarks_a_verse_and_offers_undo() {
        composeRule.onNodeWithTag("home.bible").performClick()
        composeRule.onNodeWithText("Utpoti").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("chapters.cell.1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chapters.cell.1").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.verse.1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reader.verse.1")
            .performScrollTo()
            .performTouchInput { longClick() }

        composeRule.onNodeWithText("Undo").assertIsDisplayed().performClick()
        // The undo removes the bookmark again, so the suite leaves the app
        // database as it found it for the empty-bookmarks test.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun reader_chrome_opens_the_chapter_picker_and_back_returns() {
        composeRule.onNodeWithTag("home.bible").performClick()
        composeRule.onNodeWithText("Utpoti").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("chapters.cell.1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chapters.cell.1").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.actionbar").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("reader.chapter_picker").performClick()
        composeRule.onNodeWithTag("reader.picker").assertIsDisplayed()
        composeRule.onNodeWithTag("reader.picker.cell.2").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.picker").fetchSemanticsNodes().isEmpty()
        }
        // The selection actually loaded chapter 2 (the bar shows its label).
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Ch 2").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("reader.back").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("chapters.cell.1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chapters.cell.1").assertIsDisplayed()
    }

    @Test
    fun reference_passage_banner_offers_the_full_chapter() {
        composeRule.onNodeWithTag("home.reference").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Juanv 1:16")
        composeRule.onNodeWithText("Go").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.passage_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reader.show_full_chapter").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.passage_banner").fetchSemanticsNodes().isEmpty()
        }
        // Verse 1 was filtered out by the verse-16 passage; it is proof that
        // the whole chapter actually loaded rather than an error clearing it.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.verse.1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun chapter_only_reference_has_no_passage_banner() {
        composeRule.onNodeWithTag("home.reference").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Juanv 1")
        composeRule.onNodeWithText("Go").performClick()

        // Gate on loaded content, not on the always-present action bar.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.verse.1").fetchSemanticsNodes().isNotEmpty()
        }
        // A chapter-only reference is not a filtered passage: no banner.
        composeRule.onNodeWithTag("reader.passage_banner").assertDoesNotExist()
    }
}
