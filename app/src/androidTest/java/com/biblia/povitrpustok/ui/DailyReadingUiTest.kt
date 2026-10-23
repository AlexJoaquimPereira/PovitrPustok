package com.biblia.povitrpustok.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-18: Daily Reading exposes day navigation on the screen itself and shows
 * the Gospel as the feature with its own action. Date stepping is asserted
 * through the header text, so the test is independent of liturgical data or
 * network availability.
 */
@RunWith(AndroidJUnit4::class)
class DailyReadingUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForDaily() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("daily.date").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dateText(): String =
        composeRule.onNodeWithTag("daily.date")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("")

    @Test
    fun date_navigation_steps_back_and_forward_in_place() {
        composeRule.onNodeWithTag("home.daily").performClick()
        waitForDaily()
        composeRule.onNodeWithTag("daily.prev").assertIsDisplayed()
        composeRule.onNodeWithTag("daily.next").assertIsDisplayed()

        val today = dateText()
        assertTrue("the date header must not be blank", today.isNotBlank())

        composeRule.onNodeWithTag("daily.prev").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { dateText() != today }
        val yesterday = dateText()
        assertNotEquals(today, yesterday)

        // A day other than today offers the way back.
        composeRule.onNodeWithTag("daily.today").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { dateText() == today }
        assertEquals(today, dateText())

        // Stepping forward is symmetric.
        composeRule.onNodeWithTag("daily.next").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { dateText() != today }
        assertNotEquals(today, dateText())
    }

    @Test
    fun the_gospel_is_the_featured_action() {
        composeRule.onNodeWithTag("home.daily").performClick()
        waitForDaily()

        // The liturgical API may be unavailable in a sandbox. That is a skip,
        // not a pass: a hidden Gospel block must not look like success.
        val hasGospel = composeRule.onAllNodesWithTag("daily.gospel")
            .fetchSemanticsNodes().isNotEmpty()
        Assume.assumeTrue("liturgical data unavailable; Gospel block not rendered", hasGospel)

        composeRule.onNodeWithTag("daily.gospel").assertIsDisplayed()
        composeRule.onNodeWithTag("daily.read_gospel").assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("reader.topbar").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
