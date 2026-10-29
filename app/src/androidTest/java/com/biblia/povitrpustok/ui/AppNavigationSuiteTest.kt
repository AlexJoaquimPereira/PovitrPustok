package com.biblia.povitrpustok.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.core.navigation.AppDestination
import com.biblia.povitrpustok.ui.theme.BibleTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2-03: the suite's breakpoint branch, exercised without a device window
 * resize by passing the window width explicitly.
 */
@RunWith(AndroidJUnit4::class)
class AppNavigationSuiteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compact_width_puts_the_suite_at_the_bottom() {
        setSuite(windowWidth = 599.dp)

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val home = composeRule.onNodeWithTag("nav.home").getUnclippedBoundsInRoot()
        val rootHeight = root.bottom - root.top
        assertTrue(home.top > rootHeight / 2)
    }

    @Test
    fun medium_and_expanded_widths_put_the_suite_on_the_leading_edge() {
        setSuite(windowWidth = 840.dp)

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val home = composeRule.onNodeWithTag("nav.home").getUnclippedBoundsInRoot()
        val rootWidth = root.right - root.left
        assertTrue(home.left < rootWidth / 2)
    }

    @Test
    fun detail_routes_render_no_suite_items() {
        composeRule.setContent {
            BibleTheme {
                AppNavigationSuite(
                    windowWidth = 1000.dp,
                    selectedRoute = AppDestination.READER,
                    onSelect = {}
                ) {
                    Box(modifier = Modifier.fillMaxSize().testTag("suite.content"))
                }
            }
        }

        composeRule.onNodeWithTag("suite.content").assertIsDisplayed()
        composeRule.onNodeWithTag("nav.home").assertDoesNotExist()
        composeRule.onNodeWithTag("nav.bible").assertDoesNotExist()
        composeRule.onNodeWithTag("nav.more").assertDoesNotExist()
    }

    private fun setSuite(windowWidth: Dp) {
        composeRule.setContent {
            BibleTheme {
                AppNavigationSuite(
                    windowWidth = windowWidth,
                    selectedRoute = AppDestination.HOME,
                    onSelect = {}
                ) {
                    Box(modifier = Modifier.fillMaxSize().testTag("suite.content"))
                }
            }
        }
    }
}
