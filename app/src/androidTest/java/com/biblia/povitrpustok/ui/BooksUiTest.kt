package com.biblia.povitrpustok.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.MainActivity
import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.biblia.povitrpustok.domain.model.Testament
import com.biblia.povitrpustok.feature.books.BooksGrouping
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Day 17 (F-20, F-21, F-31): Books scanning aids and the Introduction
 * screen. The jump control and the last-read marker are asserted through
 * visible state, not implementation details.
 */
@RunWith(AndroidJUnit4::class)
class BooksUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun openBooks() {
        composeRule.onNodeWithTag("home.bible").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("books.jump.old").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun the_testament_jump_moves_between_sections() {
        openBooks()

        composeRule.onNodeWithTag("books.jump.old").assertIsDisplayed()
        composeRule.onNodeWithTag("books.jump.new").assertIsDisplayed()

        // The jump must land on the first book of each section, in the same
        // canonical order the list renders.
        val sections = BooksGrouping.groupByTestament(BibleCatalog.books)
        val firstOld = sections.first { it.first == Testament.OLD_TESTAMENT }.second.first().id
        val firstNew = sections.first { it.first == Testament.NEW_TESTAMENT }.second.first().id

        composeRule.onNodeWithTag("books.jump.new").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("books.row.$firstNew")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("books.jump.old").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("books.row.$firstOld").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun a_recently_read_book_is_marked_last_read() {
        // Create a reading position: open the first chapter of Genesis.
        composeRule.onNodeWithTag("home.bible").performClick()
        composeRule.onNodeWithText("Utpoti").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("chapters.cell.1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chapters.cell.1").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.topbar").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("reader.back").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("chapters.cell.1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("books.jump.old").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodesWithText("Last read").assertCountEquals(1)
    }

    @Test
    fun the_introduction_names_its_book_and_links_to_chapters() {
        openBooks()
        composeRule.onNodeWithText("Utpoti").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("chapters.cell.1").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Introduction").performClick()
        // The link is the last item of a long introduction, so it is only
        // composed once the list scrolls to it.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("introduction.list").fetchSemanticsNodes().isNotEmpty()
        }

        // The bar names the book rather than a bare "Introduction".
        composeRule.onNodeWithText("Utpoti · Introduction").assertIsDisplayed()

        composeRule.onNodeWithTag("introduction.list")
            .performScrollToNode(hasTestTag("introduction.chapters"))
        composeRule.onNodeWithTag("introduction.chapters").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("chapters.cell.1").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
