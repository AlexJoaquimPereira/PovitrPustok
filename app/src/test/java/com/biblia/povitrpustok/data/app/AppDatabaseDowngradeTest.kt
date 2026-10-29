package com.biblia.povitrpustok.data.app

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F1-08: documents the downgrade behaviour of the production builder.
 *
 * `AppDatabase.create` uses
 * `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`, so
 * installing an older app build over a newer database drops every app-owned
 * table and recreates the current schema instead of crashing. This test pins
 * that behaviour so a future policy change cannot land silently.
 *
 * Why not `MigrationTestHelper` alone: the helper can only create a database
 * at a version with an exported schema, and the highest exported version is
 * the current one (v3). It cannot fabricate a v4 database. The reliable way
 * to exercise Room's real downgrade path is therefore to create a valid v3
 * database through the helper and then bump `PRAGMA user_version` to 4,
 * which is exactly what a newer app build would have left on disk. Room's
 * production builder — not a test-local copy — then opens the file.
 *
 * Data-loss boundary: this database contains only app state (bookmarks,
 * reading state, liturgical cache). The Bible corpus lives in separate,
 * immutable read-only databases and is never touched. A destructive
 * downgrade therefore loses user bookmarks/reading position without a
 * user-visible warning today; F1-08's policy decision (warning vs. explicit
 * migration) remains open and must not be changed without owner approval.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseDowngradeTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    @Test
    fun opening_a_newer_database_drops_app_state_and_recreates_v3() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(AppDatabase.DATABASE_NAME)

        helper.createDatabase(AppDatabase.DATABASE_NAME, 3).apply {
            execSQL(
                "INSERT INTO bookmarks " +
                    "(id,bookId,chapterPosition,verseStart,verseEnd,startOffset,endOffset," +
                    "chapterNumber,createdAt,updatedAt,deletedAt) " +
                    "VALUES ('${UUID.randomUUID()}', 'jakob', 1, 5, 5, 0, 0, 1, 1000, 1000, NULL)"
            )
            execSQL(
                "INSERT INTO reading_state (id,bookId,chapterPosition,updatedAt) " +
                    "VALUES (1,'jakob',3,1000)"
            )
            // Simulate a future build having upgraded the same file to v4.
            execSQL("PRAGMA user_version = 4")
            close()
        }

        val roomDatabase = AppDatabase.create(context)
        try {
            val database = roomDatabase.openHelper.writableDatabase
            assertEquals("Room must recreate the current schema", 3, database.version)
            assertEquals(
                "downgrade is destructive by policy: bookmarks are dropped",
                0,
                scalar(database, "SELECT count(*) FROM bookmarks")
            )
            assertEquals(
                "reading state is dropped too",
                0,
                scalar(database, "SELECT count(*) FROM reading_state")
            )
            assertEquals(0, scalar(database, "SELECT count(*) FROM liturgical_day_cache"))

            // The recreated schema must be usable, not merely present.
            val id = runBlocking {
                roomDatabase.bookmarkDao().insert(
                    BookmarkEntity(
                        id = UUID.randomUUID().toString(),
                        bookId = "jakob",
                        chapterPosition = 1,
                        verseStart = 5,
                        verseEnd = 5,
                        startOffset = 0,
                        endOffset = 0,
                        chapterNumber = 1,
                        createdAt = 2000L,
                        updatedAt = 2000L
                    )
                )
            }
            assertTrue("the downgraded database must accept new bookmarks", id > 0)
            assertEquals(1, runBlocking { roomDatabase.bookmarkDao().observeAll().first() }.size)
        } finally {
            roomDatabase.close()
        }
    }

    private fun scalar(database: SupportSQLiteDatabase, sql: String): Int =
        database.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
