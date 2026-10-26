package com.biblia.povitrpustok.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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
 * 1.2.3: Settings > About is a landing page, and every disclosure has its own
 * page. These tests cover the navigation and the two content rules that matter:
 * the source-edition page keeps only the diocesan heading + copyright (no
 * permission number, no reproduction paragraph), and the app licence page
 * renders the complete Apache-2.0 text from the packaged asset.
 */
@RunWith(AndroidJUnit4::class)
class AboutUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openAboutFromSettings() {
        composeRule.onNodeWithTag("home.notice").performScrollTo().performClick()
        waitForTag("settings.back")
        composeRule.onNodeWithTag("settings.about").performScrollTo().performClick()
        waitForTag("about.screen")
    }

    @Test
    fun about_landing_lists_each_disclosure_and_back_returns_to_settings() {
        openAboutFromSettings()

        composeRule.onNodeWithTag("about.version").assertIsDisplayed()
        composeRule.onNodeWithTag("about.source").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.api").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.licenses").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.credits").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("about.back").performClick()
        waitForTag("settings.back")
        composeRule.onNodeWithTag("settings.about").assertIsDisplayed()
    }

    @Test
    fun source_edition_keeps_only_the_heading_and_copyright() {
        openAboutFromSettings()
        composeRule.onNodeWithTag("about.source").performScrollTo().performClick()
        waitForTag("about.source.screen")

        composeRule.onNodeWithTag("about.source.copyright")
            .assertTextContains("Bible text © Archdiocese of Goa and Daman", substring = true)
        composeRule.onNodeWithText("CP/2163/2017", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("No part of this work", substring = true).assertDoesNotExist()

        composeRule.onNodeWithTag("about.source.back").performClick()
        waitForTag("about.screen")
    }

    @Test
    fun api_page_links_to_the_website_repository_and_licence() {
        openAboutFromSettings()
        composeRule.onNodeWithTag("about.api").performScrollTo().performClick()
        waitForTag("about.api.screen")

        composeRule.onNodeWithTag("about.api.website").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.api.repo").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.api.license").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.api.reference").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("about.api.back").performClick()
        waitForTag("about.screen")
    }

    @Test
    fun app_licence_renders_the_complete_apache_text_and_notice() {
        openAboutFromSettings()
        composeRule.onNodeWithTag("about.licenses").performScrollTo().performClick()
        waitForTag("about.licenses.screen")
        composeRule.onNodeWithTag("about.licenses.app").performClick()
        waitForTag("about.app_license.screen")

        composeRule.onNodeWithTag("about.app_license.text")
            .assertTextContains("Apache License", substring = true)
        composeRule.onNodeWithTag("about.app_license.text")
            .assertTextContains("END OF TERMS AND CONDITIONS", substring = true)
        composeRule.onNodeWithTag("about.app_license.text")
            .assertTextContains("APPENDIX: How to apply", substring = true)
        composeRule.onNodeWithTag("about.app_license.notice")
            .assertTextContains("Archdiocese of Goa and Daman", substring = true)

        composeRule.onNodeWithTag("about.app_license.back").performClick()
        waitForTag("about.licenses.screen")
    }

    @Test
    fun credits_links_to_the_three_disclosure_pages() {
        openAboutFromSettings()
        composeRule.onNodeWithTag("about.credits").performScrollTo().performClick()
        waitForTag("about.credits.screen")

        composeRule.onNodeWithTag("about.credits.github").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.credits.source").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.credits.api").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.credits.licenses").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Alex Pereira", substring = true).assertDoesNotExist()

        composeRule.onNodeWithTag("about.credits.back").performClick()
        waitForTag("about.screen")
    }
}
