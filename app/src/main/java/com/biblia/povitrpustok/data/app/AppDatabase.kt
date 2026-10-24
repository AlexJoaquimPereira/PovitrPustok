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
 * Version 2 adds the date-keyed liturgical cache
 * (corrective phase Issue 10) via [MIGRATION_1_2] — existing bookmarks and
 * reading state are preserved (no destructive fallback).
 */
@Database(
    entities = [
        BookmarkEntity::class,
        ReadingStateEntity::class,
        LiturgicalDayCacheEntity::class,
        LiturgicalReadingEntity::class
    ],
    version = 2,
    exportSchema = false
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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                // Audit P3-8: installing an older build over a newer database
                // must not crash. Only app state (bookmarks, reading position,
                // liturgical cache) lives here; the corpus is separate and
                // immutable, so dropping is safe.
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
