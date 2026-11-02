package com.biblia.povitrpustok.data.bible

import com.biblia.povitrpustok.domain.model.BibleCatalog
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Verifies the committed WEBC/DRA edition manifests and, when the generated
 * assets are present, that the on-disk databases match them exactly.
 *
 * The English SQLite assets are gitignored like the Konkani corpus, so a
 * fresh clone still validates the metadata and skips the file checks.
 */
class EditionManifestTest {

    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val repoRoot: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "tools/editions/webc-manifest.json").isFile }

    @Test
    fun `metadata matches the pinned sources and the 73-book catalog`() {
        val expected = listOf(
            Expected("webc", 1328, 35408, "3eea854d339cd3809a9efd03621ff88c7dc0f3e82a44ace0b2b54b2b824a922b"),
            Expected("dra", 1334, 35811, "041f23de2fde2e6c75383b4fe15f53867c676db5fb6b4c93bd55138ffb93f0a6"),
        )
        val catalogIds = BibleCatalog.books.map { it.id }.toSet()
        val konkaniFingerprint = gson.fromJson(
            File(repoRoot, "tools/corpus/corpus-manifest.json").readText(),
            Manifest::class.java,
        ).schemaFingerprint

        for (edition in expected) {
            val manifest = read(edition.id)
            assertEquals(1, manifest.format)
            assertEquals(edition.id, manifest.edition)
            assertEquals(73, manifest.bookCount)
            assertEquals(73, manifest.books.size)
            assertEquals(edition.chapters, manifest.totalChapters)
            assertEquals(edition.verses, manifest.totalVerses)
            assertEquals(edition.sourceSha256, manifest.sourceSha256)
            assertEquals("edition schema must match the corpus", konkaniFingerprint, manifest.schemaFingerprint)
            assertEquals(catalogIds, manifest.books.map { it.id }.toSet())
            assertEquals(73, manifest.books.map { it.id }.toSet().size)
            assertTrue(manifest.sourceUrl.startsWith("https://"))
        }
    }

    @Test
    fun `assets match the manifest when the edition is present`() {
        for (edition in listOf("webc", "dra")) {
            val assets = File(repoRoot, "app/src/main/assets/bible/$edition")
            assumeTrue("$edition assets are not generated in this checkout", assets.isDirectory)
            val manifest = read(edition)
            val expectedFiles = manifest.books.map { it.file }.toSet()
            val actualFiles = assets.listFiles()
                ?.filter { it.isFile && it.extension == "sqlite" }
                ?.map { it.name }
                ?.toSet()
                .orEmpty()
            assertEquals("$edition file set changed", expectedFiles, actualFiles)

            var verses = 0
            for (book in manifest.books) {
                val file = File(assets, book.file)
                assertTrue("missing $edition/${book.file}", file.isFile)
                assertEquals("size changed for $edition/${book.file}", book.bytes, file.length())
                assertEquals("hash changed for $edition/${book.file}", book.sha256, file.sha256())
                assertTrue("$edition/${book.file} has no chapters", book.chapters > 0)
                assertTrue("$edition/${book.file} has no verses", book.verses > 0)
                verses += book.verses
            }
            assertEquals(manifest.totalVerses, verses)

            val aggregate = MessageDigest.getInstance("SHA-256")
                .digest(
                    manifest.books.joinToString("") { "${it.file}:${it.sha256}\n" }.toByteArray(),
                )
                .joinToString("") { "%02x".format(it) }
            assertEquals("$edition aggregate hash is inconsistent", manifest.aggregateSha256, aggregate)
        }
    }

    private fun read(edition: String): Manifest =
        gson.fromJson(
            File(repoRoot, "tools/editions/$edition-manifest.json").readText(),
            Manifest::class.java,
        )

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

    private data class Expected(
        val id: String,
        val chapters: Int,
        val verses: Int,
        val sourceSha256: String,
    )

    private data class Manifest(
        val format: Int,
        val edition: String,
        val sourceUrl: String,
        val sourceSha256: String,
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
