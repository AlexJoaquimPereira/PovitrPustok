package com.biblia.povitrpustok.data.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Mutable application database (implementation plan §28, §2).
 *
 * Holds only application state (bookmarks, reading state, liturgical day
 * cache). It is completely separate from the immutable Bible SQLite
 * assets, which are never written or migrated.
 *
 * Version 2 adds the date-keyed liturgical cache (corrective phase Issue 10)
 * via [MIGRATION_1_2]. Version 3 replaces corpus-row-id bookmark identity with
 * a stable [com.biblia.povitrpustok.domain.model.VerseAnchor], an app-owned
 * UUID, and updated/deleted timestamps via [MIGRATION_2_3]. Both migrations
 * preserve existing user rows.
 */
@Database(
    entities = [
        BookmarkEntity::class,
        ReadingStateEntity::class,
        LiturgicalDayCacheEntity::class,
        LiturgicalReadingEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun readingStateDao(): ReadingStateDao

    abstract fun liturgicalDayCacheDao(): LiturgicalDayCacheDao

    companion object {
        const val DATABASE_NAME = "povitr_pustok_app.db"

        /** Adds the two liturgical cache tables; bookmarks/reading state untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `liturgical_day_cache` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, `season` TEXT, " +
                        "`celebrationName` TEXT, `celebrationType` TEXT, " +
                        "`celebrationDescription` TEXT, `celebrationQuote` TEXT, " +
                        "`fetchedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_liturgical_day_cache_date` " +
                        "ON `liturgical_day_cache` (`date`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `liturgical_reading` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`day_id` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                        "`reference` TEXT NOT NULL, `display_label` TEXT, " +
                        "`position` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`day_id`) REFERENCES `liturgical_day_cache`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_liturgical_reading_day_id` " +
                        "ON `liturgical_reading` (`day_id`)"
                )
            }
        }

        /** Rebuilds bookmarks with a UUID and the stable anchor index. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks_new` (" +
                        "`id` TEXT NOT NULL, " +
                        "`bookId` TEXT NOT NULL, " +
                        "`chapterPosition` INTEGER NOT NULL, " +
                        "`verseStart` INTEGER NOT NULL, " +
                        "`verseEnd` INTEGER NOT NULL, " +
                        "`startOffset` INTEGER NOT NULL, " +
                        "`endOffset` INTEGER NOT NULL, " +
                        "`chapterNumber` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))"
                )
                // Create the anchor index before copying so duplicate anchors
                // from the old row-id identity collapse instead of aborting
                // the upgrade. Oldest row wins.
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_anchor` " +
                        "ON `bookmarks_new` (`bookId`, `chapterPosition`, `verseStart`, " +
                        "`verseEnd`, `startOffset`, `endOffset`)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `bookmarks_new` (" +
                        "`id`,`bookId`,`chapterPosition`,`verseStart`,`verseEnd`," +
                        "`startOffset`,`endOffset`,`chapterNumber`,`createdAt`,`updatedAt`,`deletedAt`) " +
                        "SELECT " +
                        "lower(hex(randomblob(4))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "'4' || substr(lower(hex(randomblob(2))), 2) || '-' || " +
                        "substr('89ab', abs(random()) % 4 + 1, 1) || " +
                        "substr(lower(hex(randomblob(2))), 2) || '-' || " +
                        "lower(hex(randomblob(6))), " +
                        "`bookId`, `chapterPosition`, " +
                        "`verseStart`, `verseEnd`, 0, 0, `chapterNumber`, `createdAt`, " +
                        "`createdAt`, NULL FROM `bookmarks` " +
                        "ORDER BY `createdAt` ASC, `id` ASC"
                )
                db.execSQL("DROP TABLE `bookmarks`")
                db.execSQL("ALTER TABLE `bookmarks_new` RENAME TO `bookmarks`")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // Audit P3-8: installing an older build over a newer database
                // must not crash. Only app state (bookmarks, reading position,
                // liturgical cache) lives here; the corpus is separate and
                // immutable, so dropping is safe.
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
