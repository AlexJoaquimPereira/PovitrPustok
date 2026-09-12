package com.biblia.povitrpustok.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * AssetBibleDatabaseProvider preparation robustness (Track A §G):
 * atomic temp->rename copies, version-marker invalidation, no temp
 * residue, lazy per-book behavior.
 */
@RunWith(RobolectricTestRunner::class)
class AssetBibleDatabaseProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        File(context.filesDir, "bible").deleteRecursively()
    }

    private fun bibleDir() = File(context.filesDir, "bible")
    private fun databaseFile() = File(bibleDir(), "jakob.sqlite")
    private fun markerFile() = File(bibleDir(), "jakob.version")

    private fun freshProvider() = AssetBibleDatabaseProvider(context)

    @Test
    fun first_copy_creates_database_and_marker_without_temp_residue() = runBlocking {
        val provider = freshProvider()
        val db = provider.openBook("jakob")
        try {
            assertTrue("database file must exist", databaseFile().exists())
            assertTrue("version marker must exist", markerFile().exists())
            assertEquals(appVersion(), markerFile().readText())
            assertEquals(
                "no temp files may remain",
                0,
                bibleDir().listFiles().orEmpty().count { it.name.endsWith(".sqlite.tmp") }
            )
            assertTrue(db.hasFtsTable())
        } finally {
            provider.close()
        }
    }

    @Test
    fun stale_marker_triggers_lazy_recopy() = runBlocking {
        freshProvider().openBook("jakob").close()
        markerFile().writeText("0") // simulate an outdated copy marker

        val provider = freshProvider() // fresh handle cache => prepare runs again
        val db = provider.openBook("jakob")
        try {
            assertEquals("marker must be refreshed", appVersion(), markerFile().readText())
            assertTrue(db.hasFtsTable())
        } finally {
            provider.close()
        }
    }

    @Test
    fun garbage_final_without_marker_is_recopied() = runBlocking {
        bibleDir().mkdirs()
        databaseFile().writeBytes(ByteArray(64) { 0x7F }) // partial/corrupt final, no marker

        val provider = freshProvider()
        val db = provider.openBook("jakob")
        try {
            // the garbage was replaced by a valid copy
            assertTrue(databaseFile().length() > 64)
            assertEquals(appVersion(), markerFile().readText())
            assertTrue(db.hasFtsTable())
        } finally {
            provider.close()
        }
    }

    @Test
    fun stale_temp_file_is_cleaned_up() = runBlocking {
        bibleDir().mkdirs()
        File(bibleDir(), "jakob.sqlite.tmp").writeBytes(ByteArray(32) { 0x00 }) // crashed previous copy

        val provider = freshProvider()
        val db = provider.openBook("jakob")
        try {
            assertEquals(
                "stale temp must be cleaned",
                0,
                bibleDir().listFiles().orEmpty().count { it.name.endsWith(".sqlite.tmp") }
            )
            assertTrue(db.hasFtsTable())
        } finally {
            provider.close()
        }
    }

    @Test
    fun valid_marker_means_no_recopy() = runBlocking {
        freshProvider().openBook("jakob").close()

        // Tamper with the file but keep the valid marker: a valid marker
        // must NOT trigger a recopy (the file is trusted per the marker).
        databaseFile().writeBytes(ByteArray(32) { 0x42 })

        val provider = freshProvider()
        try {
            provider.openBook("jakob")
        } finally {
            provider.close()
        }
        // no recopy happened: the tampered file is untouched and the marker
        // still matches
        assertEquals(32, databaseFile().length())
        assertEquals(appVersion(), markerFile().readText())
    }

    @Test
    fun opened_database_is_read_only() = runBlocking {
        val provider = freshProvider()
        val db = provider.openBook("jakob")
        try {
            // OPEN_READONLY: a write attempt must fail
            try {
                db.sqliteDatabase.execSQL("CREATE TABLE probe (x INTEGER)")
                fail("expected an error writing to a read-only database")
            } catch (expected: android.database.sqlite.SQLiteException) {
                // expected
            }
        } finally {
            provider.close()
        }
    }

    @Test
    fun handles_are_cached_per_book() = runBlocking {
        val provider = freshProvider()
        try {
            val first = provider.openBook("jakob")
            val second = provider.openBook("jakob")
            assertTrue("same provider must reuse the cached handle", first === second)
        } finally {
            provider.close()
        }
    }

    private fun appVersion(): String {
        @Suppress("DEPRECATION")
        val code = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        return code.toString()
    }
}
