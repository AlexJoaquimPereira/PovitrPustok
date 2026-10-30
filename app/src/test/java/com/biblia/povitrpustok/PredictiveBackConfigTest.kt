package com.biblia.povitrpustok

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2-07: predictive back and edge-to-edge are configuration, not features, so
 * the contract is checked against the files that carry it instead of against a
 * device.
 *
 * Three things have to stay true:
 *
 *  - the application opts into the platform `OnBackInvokedDispatcher`, which is
 *    what lets the system animate Back at all;
 *  - no production source registers a legacy `BackHandler` /
 *    `OnBackPressedCallback` / `onBackPressed()` path that would sit in front of
 *    Navigation Compose's own back callback and suppress that animation;
 *  - `MainActivity` keeps drawing edge to edge.
 *
 * The behaviour itself (Back from Reader, Books and Daily Reading) is covered
 * on a device by `PredictiveBackUiTest`.
 */
class PredictiveBackConfigTest {

    private fun file(relative: String): File {
        val file = File(relative)
        assertTrue(
            "$relative must exist (working dir ${File(".").absolutePath})",
            file.isFile
        )
        return file
    }

    @Test
    fun manifest_opts_into_the_platform_back_dispatcher() {
        val manifest = file("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "the application must declare enableOnBackInvokedCallback so Back " +
                "is dispatched through OnBackInvokedDispatcher and the system " +
                "can play the predictive-back animation",
            Regex("""android:enableOnBackInvokedCallback="true"""").containsMatchIn(manifest)
        )
        assertTrue(
            "the flag belongs on <application>, not on a single activity",
            manifest.substringBefore("<activity").contains("enableOnBackInvokedCallback")
        )
    }

    @Test
    fun production_code_registers_no_legacy_back_handler() {
        val offenders = productionSources().filter { source ->
            LEGACY_BACK_MARKERS.any { marker -> source.readText().contains(marker) }
        }
        assertEquals(
            "Back must stay with Navigation Compose; a legacy dispatcher path " +
                "would suppress the predictive-back animation",
            emptyList<File>(),
            offenders
        )
    }

    @Test
    fun main_activity_draws_edge_to_edge() {
        val activity = file("src/main/java/com/biblia/povitrpustok/MainActivity.kt").readText()
        assertTrue(
            "MainActivity must call enableEdgeToEdge() before setContent",
            Regex("""enableEdgeToEdge\(\)""").containsMatchIn(activity)
        )
    }

    private fun directory(relative: String): File {
        val directory = File(relative)
        assertTrue(
            "$relative must exist (working dir ${File(".").absolutePath})",
            directory.isDirectory
        )
        return directory
    }

    private fun productionSources(): List<File> =
        directory("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private companion object {
        /** Legacy back entry points that predate the predictive-back API. */
        val LEGACY_BACK_MARKERS = listOf(
            "BackHandler",
            "OnBackPressedCallback",
            "onBackPressed()",
            "KEYCODE_BACK"
        )
    }
}
