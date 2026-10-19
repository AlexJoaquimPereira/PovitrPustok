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
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
 * Preparation is validated and recoverable (DB-04):
 *  - an internal copy is trusted only when its version marker matches, its
 *    header/schema are intact, and `PRAGMA quick_check` reports `ok`;
 *  - a missing, stale, or corrupt copy is discarded and re-published once
 *    from the packaged asset;
 *  - a corrupt packaged asset fails with [CorruptBibleDatabaseException]
 *    instead of being silently trusted, and leaves no partial state;
 *  - publication is crash-safe: the database is fsynced and atomically
 *    renamed into place *before* the marker is written (itself fsynced and
 *    atomically renamed). A crash between steps leaves a missing/stale
 *    marker, which forces a clean recopy next time.
 *
 * Note: this class intentionally reads [BibleCatalog] (domain metadata) to
 * resolve book -> asset, a deliberate small core->domain dependency
 * required by the provider contract (task §5 assigns book->asset
 * resolution to the provider).
 *
 * Handle policy (DB-05): at most one cached handle per book — a fixed,
 * corpus-sized bound of 73 descriptors, all released by [close]. No
 * eviction is justified: the bound is small and fixed, and eviction would
 * add use-after-close risk for negligible benefit. Measured costs and
 * methodology live in the remediation ledger. `close()` remains a
 * test/lifecycle operation (see PROVIDER-01); production keeps the cache
 * for the process lifetime.
 */
@Singleton
class AssetBibleDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetSource: BibleAssetSource
) : BibleDatabaseProvider {

    /**
     * Convenience constructor for non-DI callers/tests. Production uses the
     * injectable constructor with a platform [BibleAssetSource].
     */
    constructor(context: Context) : this(
        context,
        BibleAssetSource { assetPath -> context.assets.open(assetPath) }
    )

    private val mutex = Mutex()
    private val openDatabases = mutableMapOf<String, BibleDatabase>()
    private var closed = false

    override suspend fun openBook(bookId: String): BibleDatabase =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                check(!closed) { "BibleDatabaseProvider is closed." }
                openDatabases[bookId]?.let { return@withLock it }
                val book = BibleCatalog.byId(bookId)
                    ?: throw BibleBookNotFoundException(bookId)
                val database = prepare(book)
                openDatabases[bookId] = database
                database
            }
        }

    override fun close() {
        // Synchronized with openBook and terminal (PROVIDER-01). runBlocking
        // is safe here: production never closes the singleton; tests call it
        // from teardown, outside any provider lock.
        runBlocking {
            mutex.withLock {
                if (closed) return@withLock
                closed = true
                openDatabases.values.forEach { it.close() }
                openDatabases.clear()
            }
        }
    }

    private fun prepare(book: BibleBook): BibleDatabase {
        val directory = File(context.filesDir, BIBLE_DIRECTORY).apply { mkdirs() }
        val databaseFile = File(directory, book.id + DATABASE_EXTENSION)
        val markerFile = File(directory, book.id + VERSION_EXTENSION)
        val tempFile = File(directory, book.id + TEMP_EXTENSION)
        val markerTempFile = File(directory, book.id + MARKER_TEMP_EXTENSION)

        // Trust an existing copy only when the marker matches AND the file
        // passes header/schema/integrity validation.
        if (markerMatches(markerFile) && isValidDatabase(databaseFile)) {
            openReadOnly(databaseFile)?.let { return it }
            // Validated but unopenable: fall through to one recovery attempt.
        }

        // Missing, stale, corrupt, or unopenable: replace it exactly once.
        discard(databaseFile, markerFile, tempFile, markerTempFile)
        publishFromAsset(book, databaseFile, markerFile, tempFile, markerTempFile)

        return openReadOnly(databaseFile) ?: run {
            discard(databaseFile, markerFile, tempFile, markerTempFile)
            throw CorruptBibleDatabaseException(book.id, null)
        }
    }

    /** Copies the packaged asset to a validated, fsynced temp file and publishes it. */
    private fun publishFromAsset(
        book: BibleBook,
        databaseFile: File,
        markerFile: File,
        tempFile: File,
        markerTempFile: File
    ) {
        try {
            val input = try {
                assetSource.open(book.databaseAsset)
            } catch (e: IOException) {
                throw BibleDatabaseNotFoundException(book.id, e)
            }
            input.use { source ->
                FileOutputStream(tempFile).use { output ->
                    source.copyTo(output)
                    output.flush()
                    // Make the bytes durable before they can become canonical.
                    output.fd.sync()
                }
            }

            if (!isValidDatabase(tempFile)) {
                throw CorruptBibleDatabaseException(book.id, null)
            }

            moveAtomically(tempFile, databaseFile)
            writeMarkerAtomically(markerFile, markerTempFile)
        } catch (e: IOException) {
            throw BibleDatabaseNotFoundException(book.id, e)
        } finally {
            tempFile.delete()
            markerTempFile.delete()
        }
    }

    /**
     * True only when the internal copy is structurally sound: readable with
     * the expected schema and passing SQLite's quick integrity check.
     */
    private fun isValidDatabase(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                if (!quickCheckPasses(database)) return false
                requiredTablesPresent(database)
            }
        } catch (e: RuntimeException) {
            // SQLiteException (corrupt/not a database) and any other runtime
            // failure while inspecting the file mean it cannot be trusted.
            false
        }
    }

    private fun quickCheckPasses(database: SQLiteDatabase): Boolean =
        database.rawQuery("PRAGMA quick_check", null).use { cursor ->
            cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
        }

    private fun requiredTablesPresent(database: SQLiteDatabase): Boolean =
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN (?, ?, ?, ?, ?, ?)",
            REQUIRED_TABLES.toTypedArray()
        ).use { cursor ->
            val found = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            found.containsAll(REQUIRED_TABLES)
        }

    private fun openReadOnly(databaseFile: File): BibleDatabase? =
        try {
            BibleDatabase(
                SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)
            )
        } catch (e: SQLiteException) {
            null
        }

    private fun markerMatches(markerFile: File): Boolean =
        markerFile.isFile &&
            runCatching { markerFile.readText() }.getOrNull() == appVersionCode().toString()

    private fun writeMarkerAtomically(markerFile: File, markerTempFile: File) {
        FileOutputStream(markerTempFile).use { output ->
            output.write(appVersionCode().toString().toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        moveAtomically(markerTempFile, markerFile)
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun discard(
        databaseFile: File,
        markerFile: File,
        tempFile: File,
        markerTempFile: File
    ) {
        databaseFile.delete()
        markerFile.delete()
        tempFile.delete()
        markerTempFile.delete()
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
        const val MARKER_TEMP_EXTENSION = ".version.tmp"

        /** Every imported book database must expose the full read schema. */
        val REQUIRED_TABLES = listOf(
            "books",
            "chapters",
            "verses",
            "headings",
            "introduction_blocks",
            "verses_fts"
        )
    }
}
