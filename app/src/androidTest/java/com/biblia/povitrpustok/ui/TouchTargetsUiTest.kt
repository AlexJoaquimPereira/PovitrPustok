package com.biblia.povitrpustok.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2-07: 48 dp targets and labels on the surfaces the 1.3.1 shell and TTS work
 * added — the More hub rows, the verse action sheet (Read / Copy / Share /
 * Bookmark), the reader chapter picker, and the Books testament jump.
 *
 * The older surfaces already have their own checks (`HomeNavigationUiTest`
 * covers chapter cells, `LargeFontUiTest` covers the stacked jump control), so
 * nothing here repeats them. The navigation-suite items are measured in
 * `AppNavigationSuiteTest`, where both layout branches can be rendered
 * deterministically. The reader speech bar is measured in
 * `ReaderSpeechBarUiTest`, because its speaking state needs an English edition
 * that 1.3.1 does not ship.
 *
 * The assertion is on the merged, clickable node: a target that only *looks*
 * 48 dp tall but hands the tap to a smaller child would still fail.
 */
@RunWith(AndroidJUnit4::class)
class TouchTargetsUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun more_rows_are_full_width_48dp_targets() {
        composeRule.onNodeWithTag("nav.more").performClick()
        composeRule.onNodeWithTag("more.screen").assertIsDisplayed()

        listOf(
            "more.search",
            "more.bookmarks",
            "more.daily",
            "more.reference",
            "more.settings"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun verse_actions_are_48dp_targets() {
        openFirstVerse()
        composeRule.onNodeWithTag("reader.verse.1")
            .performScrollTo()
            .performTouchInput { longClick() }
        composeRule.onNodeWithTag("reader.action_sheet").assertIsDisplayed()

        // Read / Copy / Share / Bookmark: the four things a long-press offers.
        listOf(
            "reader.action_read",
            "reader.action_copy",
            "reader.action_share",
            "reader.action_bookmark"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
        // Every action is named, so TalkBack never reads an icon-only row.
        composeRule.onNodeWithText("Read this verse").assertIsDisplayed()
        composeRule.onNodeWithText("Copy verse").assertIsDisplayed()
        composeRule.onNodeWithText("Share verse").assertIsDisplayed()
    }

    @Test
    fun chapter_picker_cells_are_48dp_targets() {
        openFirstVerse()
        composeRule.onNodeWithTag("reader.chapter_picker").performClick()
        composeRule.onNodeWithTag("reader.picker").assertIsDisplayed()

        listOf("reader.picker.cell.1", "reader.picker.cell.2").forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun books_testament_jump_segments_are_48dp_targets() {
        composeRule.onNodeWithTag("home.bible").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("books.jump.old").fetchSemanticsNodes().isNotEmpty()
        }

        listOf("books.jump.old", "books.jump.new").forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    private fun openFirstVerse() {
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
}
