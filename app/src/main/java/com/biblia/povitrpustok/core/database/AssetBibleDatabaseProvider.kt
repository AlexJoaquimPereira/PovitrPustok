package com.biblia.povitrpustok.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.biblia.povitrpustok.core.result.BibleBookNotFoundException
import com.biblia.povitrpustok.core.result.BibleDatabaseNotFoundException
import com.biblia.povitrpustok.core.result.CorruptBibleDatabaseException
import com.biblia.povitrpustok.domain.model.BibleBook
import com.biblia.povitrpustok.domain.model.BibleCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Asset-backed [BibleDatabaseProvider] (implementation plan §11).
 *
 * Strategy: resolve the book via [BibleCatalog], lazily copy the APK asset
 * `bible/<file>.sqlite` to the deterministic internal location
 * `filesDir/bible/<file>.sqlite` on first use, then open it read-only and
 * cache the handle. Nothing is copied or opened at startup; each book is
 * prepared exactly once per process lifetime.
 *
 * Note: this class intentionally reads [BibleCatalog] (domain metadata) to
 * resolve book -> asset, a deliberate small core->domain dependency
 * required by the provider contract (task §5 assigns book->asset
 * resolution to the provider).
 */
@Singleton
class AssetBibleDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : BibleDatabaseProvider {

    private val mutex = Mutex()
    private val openDatabases = mutableMapOf<String, BibleDatabase>()

    override suspend fun openBook(bookId: String): BibleDatabase =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                openDatabases[bookId]?.let { return@withLock it }
                val book = BibleCatalog.byId(bookId)
                    ?: throw BibleBookNotFoundException(bookId)
                val database = prepare(book)
                openDatabases[bookId] = database
                database
            }
        }

    override fun close() {
        openDatabases.values.forEach { it.close() }
        openDatabases.clear()
    }

    private fun prepare(book: BibleBook): BibleDatabase {
        val directory = File(context.filesDir, BIBLE_DIRECTORY).apply { mkdirs() }
        val databaseFile = File(directory, book.id + DATABASE_EXTENSION)
        val markerFile = File(directory, book.id + VERSION_EXTENSION)

        try {
            ensurePrepared(book, databaseFile, markerFile)
        } catch (e: IOException) {
            throw BibleDatabaseNotFoundException(book.id, e)
        }

        val sqlite = try {
            SQLiteDatabase.openDatabase(
                databaseFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
        } catch (e: SQLiteException) {
            throw CorruptBibleDatabaseException(book.id, e)
        }
        return BibleDatabase(sqlite)
    }

    /**
     * Lazily ensures a valid, current copy of the book database exists
     * (Track A §G). A database is only trusted when its version marker
     * matches the current app versionCode — any missing, stale, or partial
     * file is recopied. The copy goes to a temp file and is atomically
     * renamed, so a partially copied database is never exposed as the
     * canonical file; temp files are cleaned up on failure. Per-book and
     * lazy — never a startup copy of the corpus.
     */
    private fun ensurePrepared(book: BibleBook, databaseFile: File, markerFile: File) {
        val currentVersion = appVersionCode().toString()
        val markerMatches = markerFile.exists() &&
            runCatching { markerFile.readText() }.getOrNull() == currentVersion
        if (databaseFile.exists() && markerMatches) {
            return
        }

        val tempFile = File(databaseFile.parentFile, book.id + TEMP_EXTENSION)
        try {
            context.assets.open(book.databaseAsset).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            if (!tempFile.renameTo(databaseFile)) {
                // Fallback for filesystems without atomic rename support.
                databaseFile.delete()
                if (!tempFile.renameTo(databaseFile)) {
                    throw IOException("could not move prepared database into place")
                }
            }
            markerFile.writeText(currentVersion)
        } finally {
            tempFile.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersionCode(): Int = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    } catch (e: Exception) {
        0
    }

    private companion object {
        const val BIBLE_DIRECTORY = "bible"
        const val DATABASE_EXTENSION = ".sqlite"
        const val VERSION_EXTENSION = ".version"
        const val TEMP_EXTENSION = ".sqlite.tmp"
    }
}
