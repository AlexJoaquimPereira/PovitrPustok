package com.biblia.povitrpustok.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
        composeRule.onNodeWithText("independent, unofficial publication", substring = true)
            .assertIsDisplayed()

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
        composeRule.onNodeWithText("Dhormprantik Povitr Pustokachea Vavracho Kendr")
            .assertIsDisplayed()
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
        composeRule.onNodeWithText("maintained by cpbjr", substring = true)
            .assertIsDisplayed()

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
    fun licences_page_lists_app_typeface_runtime_test_and_build_sections() {
        openAboutFromSettings()
        composeRule.onNodeWithTag("about.licenses").performScrollTo().performClick()
        waitForTag("about.licenses.screen")

        composeRule.onNodeWithTag("about.licenses.app").assertIsDisplayed()
        composeRule.onNodeWithTag("about.licenses.typefaces").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.licenses.typeface.atkinson")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("about.licenses.runtime").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.licenses.component.kotlin")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.licenses.test").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.licenses.component.junit")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.licenses.build").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.licenses.component.gradle")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun typeface_licence_renders_the_complete_ofl_text() {
        openAboutFromSettings()
        composeRule.onNodeWithTag("about.licenses").performScrollTo().performClick()
        waitForTag("about.licenses.screen")

        // The row names the face and its licence before the tap.
        composeRule.onNodeWithTag("about.licenses.typeface.atkinson")
            .performScrollTo()
            .assertTextContains("Atkinson Hyperlegible", substring = true)
            .assertTextContains("SIL OFL 1.1", substring = true)
            .performClick()
        waitForTag("about.typeface_license.screen")

        // The complete OFL travels with the APK: the title, the copyright line
        // and the closing disclaimer all have to be present, not a summary.
        composeRule.onNodeWithTag("about.typeface_license.text")
            .assertTextContains("SIL OPEN FONT LICENSE Version 1.1", substring = true)
        composeRule.onNodeWithTag("about.typeface_license.text")
            .assertTextContains("Copyright 2020 Braille Institute of America, Inc.", substring = true)
        composeRule.onNodeWithTag("about.typeface_license.text")
            .assertTextContains("DISCLAIMER", substring = true)

        composeRule.onNodeWithTag("about.typeface_license.back").performClick()
        waitForTag("about.licenses.screen")
    }

    @Test
    fun licences_section_labels_are_talkback_headings() {
        openAboutFromSettings()
        composeRule.onNodeWithTag("about.licenses").performScrollTo().performClick()
        waitForTag("about.licenses.screen")

        listOf(
            "about.licenses.typefaces",
            "about.licenses.runtime",
            "about.licenses.test",
            "about.licenses.build"
        )
            .forEach { tag ->
                composeRule.onNodeWithTag(tag)
                    .performScrollTo()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
            }
    }

    @Test
    fun licences_rows_merge_label_licence_and_destination() {
        openAboutFromSettings()
        composeRule.onNodeWithTag("about.licenses").performScrollTo().performClick()
        waitForTag("about.licenses.screen")

        composeRule.onNodeWithTag("about.licenses.component.kotlin")
            .performScrollTo()
            .assertTextContains("Kotlin", substring = true)
            .assertTextContains("2.2.10", substring = true)
            .assertTextContains("Apache License 2.0", substring = true)
            .assertTextContains("github.com/JetBrains/kotlin", substring = true)
    }

    @Test
    fun credits_links_to_the_project_repository_without_duplicating_disclosures() {
        openAboutFromSettings()
        composeRule.onNodeWithTag("about.credits").performScrollTo().performClick()
        waitForTag("about.credits.screen")

        composeRule.onNodeWithTag("about.credits.github").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about.credits.source").assertDoesNotExist()
        composeRule.onNodeWithTag("about.credits.api").assertDoesNotExist()
        composeRule.onNodeWithTag("about.credits.licenses").assertDoesNotExist()
        composeRule.onNodeWithTag("about.credits.reference").assertDoesNotExist()
        composeRule.onNodeWithText("Dhormprantik", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Diocesan Centre for Biblical Apostolate", substring = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText("Universalis", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Alex Pereira", substring = true).assertDoesNotExist()

        composeRule.onNodeWithTag("about.credits.back").performClick()
        waitForTag("about.screen")
    }
}
