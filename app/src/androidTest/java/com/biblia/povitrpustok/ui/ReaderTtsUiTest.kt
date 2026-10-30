package com.biblia.povitrpustok.ui

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
import com.biblia.povitrpustok.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2-11/F2-12 on a device: the reader offers "Read this verse", and the only
 * shipped edition (Romi Konkani) is answered honestly — the notice says no
 * Konkani voice is installed, no playback strip appears, and the notice can be
 * dismissed. Nothing is sent to a voice engine.
 *
 * The speaking path itself needs an English edition (1.4), so it is covered by
 * `ReaderSpeechControllerTest` rather than here.
 */
@RunWith(AndroidJUnit4::class)
class ReaderTtsUiTest {

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

    private fun openVerseActions() {
        composeRule.onNodeWithTag("reader.verse.1").performScrollTo()
            .performTouchInput { longClick() }
        composeRule.onNodeWithTag("reader.action_sheet").assertIsDisplayed()
    }

    @Test
    fun the_verse_actions_offer_read_this_verse() {
        openFirstVerse()

        openVerseActions()

        composeRule.onNodeWithTag("reader.action_read").assertIsDisplayed()
        composeRule.onNodeWithText("Read this verse").assertIsDisplayed()
    }

    @Test
    fun reading_a_konkani_verse_explains_that_no_konkani_voice_is_installed() {
        openFirstVerse()
        openVerseActions()

        composeRule.onNodeWithTag("reader.action_read").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.tts_notice").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("No Konkani voice is installed").assertIsDisplayed()
        assertTrue(
            "nothing is spoken, so no playback strip may appear",
            composeRule.onAllNodesWithTag("reader.tts_bar").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun the_missing_voice_notice_can_be_dismissed() {
        openFirstVerse()
        openVerseActions()
        composeRule.onNodeWithTag("reader.action_read").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.tts_dismiss").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("reader.tts_dismiss").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.tts_notice").fetchSemanticsNodes().isEmpty()
        }
    }
}
