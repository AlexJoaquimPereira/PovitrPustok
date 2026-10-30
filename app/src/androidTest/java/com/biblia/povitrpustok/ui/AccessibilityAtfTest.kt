package com.biblia.povitrpustok.ui

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.biblia.povitrpustok.MainActivity
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheckResult
import com.google.android.apps.common.testing.accessibility.framework.checks.ClickableSpanCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.DuplicateClickableBoundsCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.DuplicateSpeakableTextCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.EditableContentDescCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.ImageContrastCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.LinkPurposeUnclearCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.RedundantDescriptionCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.SpeakableTextPresentCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.TextContrastCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.TouchTargetSizeCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.TraversalOrderCheck
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchyAndroid
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2-08 advisory: run the Android Accessibility Test Framework over the
 * primary screens and fail only on ATF ERROR results (P1). WARNING/INFO
 * results are logged for triage and recorded in docs/accessibility-1.3.1.md.
 *
 * The scan is deliberately narrow: Home, Books, the reader, Search,
 * Bookmarks, Daily, More, Settings and About. It is an advisory gate, not a
 * certification, and it does not replace the manual TalkBack script.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityAtfTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun enableAccessibilityChecks() {
        // Espresso's validator walks the View tree. Compose exposes its UI as
        // virtual accessibility nodes under a single AndroidComposeView, so
        // the scan below feeds ATF the active window's accessibility tree
        // instead (see scan()).
        AccessibilityChecks.enable()
            .setRunChecksFromRootView(true)
    }

    @After
    fun disableAccessibilityChecks() {
        AccessibilityChecks.disable()
    }

    @Test
    fun home_books_and_reader_have_no_p1_findings() {
        scan("Home")

        composeRule.onNodeWithTag("home.bible").performClick()
        waitForTag("books.row.Utpoti")
        scan("Books")

        composeRule.onNodeWithText("Utpoti").performScrollTo().performClick()
        waitForTag("chapters.cell.1")
        scan("Chapters")

        composeRule.onNodeWithTag("chapters.cell.1").performClick()
        waitForTag("reader.verse.1")
        scan("Reader")

        // The overflow menu is a separate window; scan it too.
        composeRule.onNodeWithTag("reader.menu").performClick()
        composeRule.onNodeWithTag("reader.menu.about").assertIsDisplayed()
        scan("Reader menu")
    }

    @Test
    fun search_bookmarks_daily_more_settings_and_about_have_no_p1_findings() {
        composeRule.onNodeWithTag("home.search").performClick()
        waitForTag("search.field")
        scan("Search empty")

        composeRule.onNodeWithTag("search.field").performTextInput("subhagi")
        waitForTag("search.count")
        scan("Search results")

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home.bookmarks").performClick()
        waitForTag("bookmarks.empty")
        scan("Bookmarks empty")

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home.daily").performClick()
        waitForTag("daily.date")
        scan("Daily")

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav.more").performClick()
        waitForTag("more.screen")
        scan("More")

        composeRule.onNodeWithTag("more.settings").performClick()
        waitForTag("settings.back")
        scan("Settings")

        composeRule.onNodeWithTag("settings.about").performScrollTo().performClick()
        waitForTag("about.screen")
        scan("About")
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scan(screen: String) {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow != null
        }
        val results = mutableListOf<AccessibilityHierarchyCheckResult>()
        composeRule.runOnUiThread {
            val root = checkNotNull(
                InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .rootInActiveWindow
            ) { "$screen: no active accessibility window" }
            val hierarchy = AccessibilityHierarchyAndroid
                .newBuilder(root, composeRule.activity)
                .build()
            Log.i(
                TAG,
                "$screen hierarchy: " + hierarchy.allWindows.joinToString { window ->
                    "${window.allViews.size} views"
                }
            )
            CHECKS.mapNotNull { AccessibilityCheckPreset.getHierarchyCheckForClass(it) }
                .forEach { check ->
                    results += check.runCheckOnHierarchy(hierarchy)
                }
        }
        results.forEach { result ->
            val element = result.element
            val where = element?.let {
                " [${it.className} text=${it.text} desc=${it.contentDescription} bounds=${it.boundsInScreen}]"
            }.orEmpty()
            Log.i(
                TAG,
                "$screen ${result.type} ${result.sourceCheckClass?.simpleName}: ${result.message}$where"
            )
        }
        assertTrue("$screen produced no ATF results", results.isNotEmpty())
        val errors = results.filter { it.type == AccessibilityCheckResultType.ERROR }
        assertTrue(
            "$screen has ATF P1 findings:\n" + errors.joinToString("\n") {
                val element = it.element
                "${it.sourceCheckClass?.simpleName}: ${it.message}" +
                    element?.let { e ->
                        " [${e.className} text=${e.text} desc=${e.contentDescription} bounds=${e.boundsInScreen}]"
                    }.orEmpty()
            },
            errors.isEmpty()
        )
    }

    private companion object {
        const val TAG = "AccessibilityAtf"

        /**
         * The hierarchy checks in ATF 3.1.2's latest preset. Listed by class
         * because the preset accessor returns a Guava ImmutableSet, which the
         * ATF artifact does not expose on the compile classpath.
         */
        val CHECKS = listOf(
            SpeakableTextPresentCheck::class.java,
            TouchTargetSizeCheck::class.java,
            DuplicateClickableBoundsCheck::class.java,
            DuplicateSpeakableTextCheck::class.java,
            TextContrastCheck::class.java,
            ImageContrastCheck::class.java,
            RedundantDescriptionCheck::class.java,
            EditableContentDescCheck::class.java,
            ClickableSpanCheck::class.java,
            LinkPurposeUnclearCheck::class.java,
            TraversalOrderCheck::class.java
        )
    }
}
