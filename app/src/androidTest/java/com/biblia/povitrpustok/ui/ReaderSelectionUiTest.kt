package com.biblia.povitrpustok.ui

import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.biblia.povitrpustok.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Day 18 (F-23): long-press opens verse actions; copy/share use the domain
 * plain text, and bookmarking stays reachable through the same sheet.
 */
@RunWith(AndroidJUnit4::class)
class ReaderSelectionUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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

    @Test
    fun long_press_offers_copy_share_and_bookmark() {
        openFirstVerse()

        composeRule.onNodeWithTag("reader.verse.1").performScrollTo()
            .performTouchInput { longClick() }

        composeRule.onNodeWithTag("reader.action_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("reader.action_copy").assertIsDisplayed()
        composeRule.onNodeWithTag("reader.action_share").assertIsDisplayed()
        composeRule.onNodeWithTag("reader.action_bookmark").assertIsDisplayed()
    }

    @Test
    fun copying_a_verse_puts_plain_text_with_its_reference_on_the_clipboard() {
        openFirstVerse()

        composeRule.onNodeWithTag("reader.verse.1").performScrollTo()
            .performTouchInput { longClick() }
        composeRule.onNodeWithTag("reader.action_copy").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.action_sheet").fetchSemanticsNodes().isEmpty()
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val copied = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.text?.toString()

        assertNotNull("the clipboard must receive the verse", copied)
        assertTrue(
            "the reference must accompany the text, was: $copied",
            copied!!.startsWith("Utpoti 1:1")
        )
        assertFalse("HTML must never be copied", copied.contains("<"))
    }
}
