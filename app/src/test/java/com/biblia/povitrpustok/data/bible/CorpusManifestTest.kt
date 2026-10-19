package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.testutil.CorpusAvailabilityRule
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Verifies that the private corpus on disk is exactly the corpus described by
 * the committed manifest. Catches a stale download, a partial extraction, or a
 * regenerated corpus that was not intentionally versioned.
 */
class CorpusManifestTest {

    @get:Rule
    val corpusAvailabilityRule = CorpusAvailabilityRule()

    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @Test
    fun `assets match the committed corpus manifest`() {
        val repoRoot = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "tools/corpus/corpus-manifest.json").isFile }
        val manifest = gson.fromJson(
            File(repoRoot, "tools/corpus/corpus-manifest.json").readText(),
            CorpusManifest::class.java,
        )
        val assets = File(repoRoot, "app/src/main/assets/bible")

        assertEquals(1, manifest.format)
        assertEquals(73, manifest.bookCount)
        assertEquals(73, manifest.books.size)
        assertTrue(manifest.corpusVersion.isNotBlank())
        assertTrue(manifest.schemaFingerprint.isNotBlank())

        val expectedFiles = manifest.books.map { it.file }.toSet()
        val actualFiles = assets.listFiles()
            ?.filter { it.isFile && it.extension == "sqlite" }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
        assertEquals("corpus file set changed", expectedFiles, actualFiles)

        var verses = 0
        for (book in manifest.books) {
            val file = File(assets, book.file)
            assertTrue("missing ${book.file}", file.isFile)
            assertEquals("size changed for ${book.file}", book.bytes, file.length())
            assertEquals("hash changed for ${book.file}", book.sha256, file.sha256())
            assertTrue("${book.file} has no chapters", book.chapters > 0)
            assertTrue("${book.file} has no verses", book.verses > 0)
            verses += book.verses
        }
        assertEquals(manifest.totalVerses, verses)

        val aggregate = MessageDigest.getInstance("SHA-256")
            .digest(
                manifest.books.joinToString("") { "${it.file}:${it.sha256}\n" }.toByteArray(),
            )
            .joinToString("") { "%02x".format(it) }
        assertEquals("manifest aggregate hash is inconsistent", manifest.aggregateSha256, aggregate)
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class CorpusManifest(
        val format: Int,
        val corpusVersion: String,
        val bookCount: Int,
        val totalChapters: Int,
        val totalVerses: Int,
        val aggregateSha256: String,
        val schemaFingerprint: String,
        val books: List<Book>,
    )

    private data class Book(
        val file: String,
        val id: String,
        val sha256: String,
        val bytes: Long,
        val chapters: Int,
        val verses: Int,
        val userVersion: Int,
    )
}
