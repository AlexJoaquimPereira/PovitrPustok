package com.biblia.povitrpustok.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.core.navigation.AppDestination
import com.biblia.povitrpustok.domain.model.SearchResult
import com.biblia.povitrpustok.domain.model.Verse
import com.biblia.povitrpustok.feature.books.BookListItem
import com.biblia.povitrpustok.feature.books.TestamentJump
import com.biblia.povitrpustok.feature.reader.PassageBanner
import com.biblia.povitrpustok.feature.reader.ReaderPassage
import com.biblia.povitrpustok.feature.reader.ReaderUiState
import com.biblia.povitrpustok.feature.search.SearchResultItem
import com.biblia.povitrpustok.ui.components.BookName
import com.biblia.povitrpustok.ui.components.BookNameLayout
import com.biblia.povitrpustok.ui.theme.BibleTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2-05: 200% non-linear font scale, rendered deterministically with
 * [DeviceConfigurationOverride] instead of by changing the device setting.
 *
 * The content tests pin a 320 dp-wide column (the narrow end of the largest
 * display size) so a wrap/stack regression is visible without a device
 * matrix; the navigation-suite test uses the phone width it names. The
 * assertions look at the laid-out height/position of the node, because the
 * semantics text is still the full string even when it is ellipsized.
 */
@RunWith(AndroidJUnit4::class)
class LargeFontUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val longBookName = "Luka Pormannem Jezu Kristachem Xubhvortoman"

    @Test
    fun testament_jump_stacks_at_two_x_and_keeps_full_width_targets() {
        setLargeFontContent {
            TestamentJump(
                inNewTestament = false,
                onJumpToOldTestament = {},
                onJumpToNewTestament = {}
            )
        }

        val old = composeRule.onNodeWithTag("books.jump.old").getUnclippedBoundsInRoot()
        val new = composeRule.onNodeWithTag("books.jump.new").getUnclippedBoundsInRoot()

        // Two rows, not one: the second segment starts at or below the first.
        assertTrue("New Testament should sit below Old Testament", new.top >= old.bottom)
        // Full-width labels: a half-width segment is where "Old Testam…" came from.
        assertTrue("Old Testament target should be full width", (old.right - old.left) > 250.dp)
        assertTrue("New Testament target should be full width", (new.right - new.left) > 250.dp)
        assertTrue("Targets stay at least 48 dp", (old.bottom - old.top) >= 48.dp && (new.bottom - new.top) >= 48.dp)
        composeRule.onNodeWithText("Old Testament").assertIsDisplayed()
        composeRule.onNodeWithText("New Testament").assertIsDisplayed()
    }

    @Test
    fun testament_jump_stays_side_by_side_at_normal_font_scale() {
        composeRule.setContent {
            BibleTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    TestamentJump(
                        inNewTestament = false,
                        onJumpToOldTestament = {},
                        onJumpToNewTestament = {}
                    )
                }
            }
        }

        val old = composeRule.onNodeWithTag("books.jump.old").getUnclippedBoundsInRoot()
        val new = composeRule.onNodeWithTag("books.jump.new").getUnclippedBoundsInRoot()
        assertTrue("Segments share a row at 1x", old.top == new.top)
        assertTrue("Each segment is half the row", (old.right - old.left) < 160.dp)
    }

    @Test
    fun book_list_name_wraps_to_two_lines_at_two_x() {
        setLargeFontContent {
            BookListItem(
                localName = longBookName,
                englishName = "Gospel of Luke",
                onClick = {}
            )
        }

        val name = composeRule
            .onNodeWithText(longBookName, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        // Android 14+ scales large text non-linearly: at 200% bodyLarge is
        // ~41 dp per line, so two lines are ~82 dp; one line is ~28 dp.
        assertTrue("Book name should use a second line, was ${(name.bottom - name.top)}", (name.bottom - name.top) >= 75.dp)
    }

    @Test
    fun bookmark_name_wraps_to_two_lines_at_two_x() {
        setLargeFontContent {
            BookName(
                localName = longBookName,
                englishName = null,
                layout = BookNameLayout.INLINE,
                localMaxLines = 2
            )
        }

        val name = composeRule
            .onNodeWithText(longBookName, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        // titleMedium is ~37.5 dp per line at 200%; two lines are ~75 dp.
        assertTrue("Bookmark name should use a second line, was ${(name.bottom - name.top)}", (name.bottom - name.top) >= 70.dp)
    }

    @Test
    fun search_result_name_wraps_to_two_lines_at_two_x() {
        setLargeFontContent {
            SearchResultItem(
                result = SearchResult(
                    bookId = "luke",
                    chapterId = 1L,
                    chapterNumber = 13,
                    chapterPosition = 13,
                    verseId = 1L,
                    verseStart = 31,
                    verseEnd = 35,
                    snippet = "Têch ghoddier Farizevantle kãi zann Jezuk sangunk lagle."
                ),
                onClick = {},
                bookName = longBookName
            )
        }

        val name = composeRule
            .onNodeWithText(longBookName, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        // titleSmall is ~34 dp per line at 200%; two lines are ~69 dp.
        assertTrue("Search result name should use a second line, was ${(name.bottom - name.top)}", (name.bottom - name.top) >= 65.dp)
    }

    @Test
    fun continue_reading_name_wraps_at_two_x() {
        setLargeFontContent {
            ContinueReadingCard(
                bookName = longBookName,
                updatedAt = System.currentTimeMillis(),
                onClick = {}
            )
        }

        val name = composeRule
            .onNodeWithText(longBookName, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        // titleMedium is ~37.5 dp per line at 200%; two lines are ~75 dp.
        assertTrue("Continue-reading name should use a second line, was ${(name.bottom - name.top)}", (name.bottom - name.top) >= 70.dp)
    }

    @Test
    fun passage_banner_stacks_the_label_above_the_action_at_two_x() {
        // Verses 1 and 35 present, but only 31–35 requested: the banner is
        // shown because the passage does not cover the whole chapter.
        val state = ReaderUiState(
            passage = ReaderPassage(verseStart = 31, verseEnd = 35),
            verses = listOf(
                Verse(1L, 1L, 13, 1, 1, "<p>one</p>", "one"),
                Verse(2L, 1L, 13, 35, 35, "<p>thirty-five</p>", "thirty-five")
            )
        )
        setLargeFontContent {
            PassageBanner(state = state, onShowFullChapter = {})
        }

        val label = composeRule
            .onNodeWithText("Verses 31–35", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val action = composeRule
            .onNodeWithTag("reader.show_full_chapter")
            .getUnclippedBoundsInRoot()
        assertTrue("Label should keep the full verse range", (label.right - label.left) > 250.dp)
        assertTrue("Action should sit below the label", action.top >= label.bottom)
    }

    @Test
    fun navigation_suite_labels_stay_inside_the_bar_at_two_x() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                BibleTheme {
                    Box(modifier = Modifier.width(448.dp).fillMaxHeight()) {
                        AppNavigationSuite(
                            windowWidth = 448.dp,
                            selectedRoute = AppDestination.HOME,
                            onSelect = {}
                        ) {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("nav.home").assertIsDisplayed()
        val label = composeRule
            .onNodeWithText("Home", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("Home label should be inside the bar", label.bottom <= root.bottom)
        assertTrue("Home label should be visible", (label.bottom - label.top) > 0.dp)
    }

    /** Pins a 320 dp column at 2x; the narrow end of the largest display size. */
    private fun setLargeFontContent(
        width: Dp = 320.dp,
        content: @Composable () -> Unit
    ) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                BibleTheme {
                    Box(modifier = Modifier.width(width).fillMaxHeight()) {
                        content()
                    }
                }
            }
        }
    }
}
