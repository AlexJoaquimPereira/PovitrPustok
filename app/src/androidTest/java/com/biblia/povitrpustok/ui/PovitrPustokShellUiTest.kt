package com.biblia.povitrpustok.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2-01/F2-02: the shell's top-level behaviour on a device.
 *
 * The suite layout branch itself is covered deterministically by
 * [AppNavigationSuiteTest]; this class verifies the wiring inside
 * [MainActivity] — tab selection, back-stack behaviour, More reachability and
 * the suite being absent on detail screens.
 */
@RunWith(AndroidJUnit4::class)
class PovitrPustokShellUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigation_suite_matches_the_current_window_width() {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val home = composeRule.onNodeWithTag("nav.home")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("nav.bible").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.more").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.home").assertIsSelected()

        val rootWidth = root.right - root.left
        val rootHeight = root.bottom - root.top
        if (rootWidth < 600.dp) {
            // Compact: bottom bar.
            assertTrue(home.top > rootHeight / 2)
        } else {
            // Medium/expanded: rail on the leading edge.
            assertTrue(home.left < rootWidth / 2)
        }
    }

    @Test
    fun tab_switches_do_not_stack_and_back_returns_home() {
        composeRule.onNodeWithTag("nav.bible").performClick()
        composeRule.onNodeWithTag("books.jump.old").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.bible").assertIsSelected()

        composeRule.onNodeWithTag("nav.home").performClick()
        composeRule.onNodeWithTag("home.notice").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.home").assertIsSelected()

        // Switch back and forth again; Back must still land on Home rather
        // than exiting or revealing a duplicate Bible entry.
        composeRule.onNodeWithTag("nav.bible").performClick()
        composeRule.onNodeWithTag("books.jump.old").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home.notice").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.home").assertIsSelected()
    }

    @Test
    fun more_hub_lists_the_secondary_destinations() {
        composeRule.onNodeWithTag("nav.more").performClick()
        composeRule.onNodeWithTag("more.screen").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.more").assertIsSelected()

        listOf(
            "more.search",
            "more.bookmarks",
            "more.daily",
            "more.reference",
            "more.settings"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun more_opens_search_without_the_suite_and_back_returns_to_more() {
        composeRule.onNodeWithTag("nav.more").performClick()
        composeRule.onNodeWithTag("more.search").performClick()

        composeRule.onNodeWithTag("search.field").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.home").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag("more.screen").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.more").assertIsSelected()
    }

    @Test
    fun detail_screens_keep_their_own_chrome_and_hide_the_suite() {
        composeRule.onNodeWithTag("home.notice").performClick()
        composeRule.onNodeWithTag("settings.back").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.home").assertDoesNotExist()

        composeRule.onNodeWithTag("settings.back").performClick()
        composeRule.onNodeWithTag("home.notice").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.home").assertIsDisplayed()
    }
}
