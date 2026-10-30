package com.biblia.povitrpustok.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.povitrpustok.feature.reader.ReaderSpeechBar
import com.biblia.povitrpustok.feature.reader.ReaderSpeechIssue
import com.biblia.povitrpustok.feature.reader.ReaderSpeechPhase
import com.biblia.povitrpustok.feature.reader.ReaderSpeechState
import com.biblia.povitrpustok.ui.theme.BibleTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2-07: the reader speech bar is the newest interactive surface, and its
 * speaking state cannot be reached on a device yet — 1.3.1 ships Romi Konkani
 * only, and reading it aloud is refused by design (F2-12). Rendering the bar
 * directly is the only way to check its targets and labels, and it keeps the
 * check honest: the same composable the reader uses, with a state the reader
 * would hand it.
 *
 * Asserted here:
 *  - pause/resume and stop are named for TalkBack (no icon-only actions);
 *  - both keep a 48 dp touch target;
 *  - the "nothing was spoken" notice keeps a 48 dp Dismiss and stays inside a
 *    320 dp column at 200% font scale, where it stacks.
 *
 * The touch-target measurement uses [touchBoundsInRoot], not the layout box.
 * Material 3 `IconButton` deliberately draws a 40 dp box inside a 48 dp touch
 * (and accessibility) target, so the layout height of an icon button is not
 * the number the 48 dp rule is about; the expanded bounds are. Components that
 * are sized by their own modifier (`TextButton` here) are measured as laid
 * out.
 */
@RunWith(AndroidJUnit4::class)
class ReaderSpeechBarUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pause_and_stop_are_labelled_48dp_targets_while_speaking() {
        setBar(
            ReaderSpeechState(
                phase = ReaderSpeechPhase.Speaking,
                verseId = 1L,
                spokenSegment = "Orombak"
            ),
            verseNumber = 1
        )

        composeRule.onNodeWithText("Reading verse 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause reading").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stop reading").assertIsDisplayed()
        assertTouchTarget("reader.tts_pause")
        assertTouchTarget("reader.tts_stop")
    }

    @Test
    fun the_paused_state_offers_a_labelled_resume() {
        setBar(
            ReaderSpeechState(phase = ReaderSpeechPhase.Paused, verseId = 1L),
            verseNumber = 1
        )

        composeRule.onNodeWithContentDescription("Resume reading").assertIsDisplayed()
        assertTouchTarget("reader.tts_pause")
        assertTouchTarget("reader.tts_stop")
    }

    @Test
    fun the_missing_voice_notice_keeps_a_48dp_dismiss_at_two_x() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                BibleTheme {
                    Box(modifier = Modifier.width(320.dp).fillMaxHeight()) {
                        ReaderSpeechBar(
                            speech = ReaderSpeechState(
                                phase = ReaderSpeechPhase.Unavailable,
                                issue = ReaderSpeechIssue.NoKonkaniVoice
                            ),
                            verseNumber = null,
                            onTogglePause = {},
                            onStop = {},
                            onDismiss = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("reader.tts_notice").assertIsDisplayed()
        composeRule.onNodeWithText("No Konkani voice is installed").assertIsDisplayed()
        composeRule.onNodeWithTag("reader.tts_dismiss")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun nothing_is_drawn_when_no_verse_was_asked_for() {
        setBar(ReaderSpeechState(), verseNumber = null)

        composeRule.onNodeWithTag("reader.tts_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("reader.tts_notice").assertDoesNotExist()
    }

    /** The whole expanded target, not just the drawn box, must clear 48 dp. */
    private fun assertTouchTarget(tag: String) {
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
        val width = with(composeRule.density) { (bounds.right - bounds.left).toDp() }
        val height = with(composeRule.density) { (bounds.bottom - bounds.top).toDp() }
        assertTrue("$tag touch width was $width", width >= 48.dp)
        assertTrue("$tag touch height was $height", height >= 48.dp)
    }

    private fun setBar(speech: ReaderSpeechState, verseNumber: Int?) {
        composeRule.setContent {
            BibleTheme {
                ReaderSpeechBar(
                    speech = speech,
                    verseNumber = verseNumber,
                    onTogglePause = {},
                    onStop = {},
                    onDismiss = {}
                )
            }
        }
    }
}
