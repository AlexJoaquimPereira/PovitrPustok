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
 * F-15: Settings & About is reachable from Home and from the Reader, the
 * controls write through immediately, and the selection survives navigating
 * away and back (the repository is the single source of truth, so no restart
 * is involved).
 *
 * Every test restores the defaults it changed so the suite leaves the app as
 * it found it.
 */
@RunWith(AndroidJUnit4::class)
class SettingsUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForSettings() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("settings.back").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openSettingsFromHome() {
        composeRule.onNodeWithTag("home.notice").performScrollTo().performClick()
        waitForSettings()
    }

    @Test
    fun home_notice_opens_settings_and_back_returns_home() {
        openSettingsFromHome()

        composeRule.onNodeWithTag("settings.about.copyright").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings.version").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("settings.back").performClick()
        composeRule.onNodeWithTag("home.bible").assertIsDisplayed()
    }

    @Test
    fun reader_overflow_opens_settings() {
        composeRule.onNodeWithTag("home.bible").performClick()
        composeRule.onNodeWithText("Utpoti").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("chapters.cell.1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chapters.cell.1").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("reader.topbar").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("reader.menu").performClick()
        composeRule.onNodeWithTag("reader.menu.about").performClick()

        waitForSettings()
        composeRule.onNodeWithTag("settings.about.copyright").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun scripture_size_applies_without_restart_and_survives_navigation() {
        openSettingsFromHome()

        composeRule.onNodeWithTag("settings.scripture_size.EXTRA_LARGE")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings.scripture_size.EXTRA_LARGE").assertIsSelected()
        composeRule.onNodeWithTag("settings.preview").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("settings.back").performClick()
        composeRule.onNodeWithTag("home.bible").assertIsDisplayed()
        openSettingsFromHome()

        // The selection is read back from the repository, not from a screen
        // snapshot: leaving and returning must preserve it.
        composeRule.onNodeWithTag("settings.scripture_size.EXTRA_LARGE")
            .performScrollTo()
            .assertIsSelected()

        // Restore the default for the rest of the suite.
        composeRule.onNodeWithTag("settings.scripture_size.MEDIUM")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings.scripture_size.MEDIUM").assertIsSelected()
    }

    @Test
    fun theme_choice_persists_and_restores_to_system() {
        openSettingsFromHome()

        composeRule.onNodeWithTag("settings.theme.DARK").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings.theme.DARK").assertIsSelected()

        composeRule.onNodeWithTag("settings.back").performClick()
        openSettingsFromHome()
        composeRule.onNodeWithTag("settings.theme.DARK").performScrollTo().assertIsSelected()

        composeRule.onNodeWithTag("settings.theme.SYSTEM").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings.theme.SYSTEM").assertIsSelected()
    }
}
