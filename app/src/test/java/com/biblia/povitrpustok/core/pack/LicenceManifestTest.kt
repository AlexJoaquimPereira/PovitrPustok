package com.biblia.povitrpustok.core.pack

import com.biblia.povitrpustok.core.result.ContentPackChecksumException
import com.biblia.povitrpustok.core.result.ContentPackExpiredException
import com.biblia.povitrpustok.core.result.ContentPackValidationException
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * F1-19 build-time licence gate.
 *
 * Every bundled pack must declare a licence, attribution, and (for calendar
 * packs) a territory and expiry. The gate runs the same parser/loader the app
 * uses, so a pack that would fail at runtime also fails the build. Fixtures
 * prove the gate rejects a missing row, a missing territory/expiry, an expired
 * term, and a checksum mismatch.
 */
class LicenceManifestTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val fixtureToday = LocalDate.of(2027, 1, 15)
    private val fixtureParser = ContentPackManifestParser { fixtureToday }

    @Test
    fun real_bundled_packs_pass_the_licence_gate() {
        val packsRoot = repoRoot().resolve("app/src/main/assets/packs")
        assertTrue("missing bundled pack directory: $packsRoot", packsRoot.isDirectory)

        val packs = ContentPackLoader(
            source = DirectoryPackSource(packsRoot),
            parser = ContentPackManifestParser()
        ).loadAll()

        packs.forEach { pack ->
            assertTrue("${pack.id} has no licence", pack.licence.isNotBlank())
            assertTrue("${pack.id} has no attribution", pack.attribution.isNotBlank())
            if (pack.type.requiresTerritory) {
                assertTrue("${pack.id} has no territory", pack.territory.isNotEmpty())
            }
            if (pack.type.requiresExpiry) {
                assertNotNull("${pack.id} has no expiry", pack.expiry)
            }
        }
    }

    @Test
    fun a_valid_fixture_pack_passes() {
        val root = temporaryFolder.newFolder("valid-packs")
        writePack(
            root,
            id = "sample",
            files = mapOf("data.json" to """{"value":1}"""),
            type = "json",
            territory = null,
            expiry = null
        )

        val pack = ContentPackLoader(DirectoryPackSource(root), fixtureParser)
            .load("sample")

        assertTrue(pack.licence.isNotBlank())
        assertTrue(pack.attribution.isNotBlank())
    }

    @Test
    fun missing_licence_row_fails_the_gate() {
        val root = temporaryFolder.newFolder("missing-licence")
        writePack(
            root,
            id = "no-licence",
            files = mapOf("data.json" to "{}"),
            type = "json",
            includeLicence = false
        )

        expectThrows<ContentPackValidationException>("missing 'licence'") {
            ContentPackLoader(DirectoryPackSource(root), fixtureParser).loadAll()
        }
    }

    @Test
    fun missing_attribution_row_fails_the_gate() {
        val root = temporaryFolder.newFolder("missing-attribution")
        writePack(
            root,
            id = "no-attribution",
            files = mapOf("data.json" to "{}"),
            type = "json",
            includeAttribution = false
        )

        expectThrows<ContentPackValidationException>("missing 'attribution'") {
            ContentPackLoader(DirectoryPackSource(root), fixtureParser).loadAll()
        }
    }

    @Test
    fun calendar_without_territory_or_expiry_fails_the_gate() {
        val noTerritory = temporaryFolder.newFolder("no-territory")
        writePack(
            noTerritory,
            id = "calendar",
            files = mapOf("calendar.json" to "{}"),
            type = "calendar",
            expiry = """"2027-12-31""""
        )
        expectThrows<ContentPackValidationException>("requires a territory") {
            ContentPackLoader(DirectoryPackSource(noTerritory), fixtureParser).loadAll()
        }

        val noExpiry = temporaryFolder.newFolder("no-expiry")
        writePack(
            noExpiry,
            id = "calendar",
            files = mapOf("calendar.json" to "{}"),
            type = "calendar",
            territory = """["IN"]"""
        )
        expectThrows<ContentPackValidationException>("requires an expiry date") {
            ContentPackLoader(DirectoryPackSource(noExpiry), fixtureParser).loadAll()
        }
    }

    @Test
    fun expired_term_fails_the_gate() {
        val root = temporaryFolder.newFolder("expired")
        writePack(
            root,
            id = "calendar",
            files = mapOf("calendar.json" to "{}"),
            type = "calendar",
            territory = """["IN"]""",
            expiry = """"2027-01-14""""
        )

        expectThrows<ContentPackExpiredException>("expired on 2027-01-14") {
            ContentPackLoader(DirectoryPackSource(root), fixtureParser).loadAll()
        }
    }

    @Test
    fun checksum_mismatch_fails_the_gate() {
        val root = temporaryFolder.newFolder("checksum")
        val packDir = File(root, "sample").apply { mkdirs() }
        File(packDir, "data.json").writeText("tampered")
        File(packDir, ContentPackLoader.MANIFEST_FILE).writeText(
            manifest(
                id = "sample",
                files = mapOf("data.json" to "expected"),
                type = "json"
            )
        )

        expectThrows<ContentPackChecksumException>("checksum mismatch") {
            ContentPackLoader(DirectoryPackSource(root), fixtureParser).loadAll()
        }
    }

    private fun writePack(
        root: File,
        id: String,
        files: Map<String, String>,
        type: String,
        territory: String? = null,
        expiry: String? = null,
        includeLicence: Boolean = true,
        includeAttribution: Boolean = true
    ) {
        val packDir = File(root, id).apply { mkdirs() }
        files.forEach { (path, content) ->
            File(packDir, path).apply { parentFile?.mkdirs() }.writeText(content)
        }
        File(packDir, ContentPackLoader.MANIFEST_FILE).writeText(
            manifest(
                id = id,
                files = files,
                type = type,
                territory = territory,
                expiry = expiry,
                includeLicence = includeLicence,
                includeAttribution = includeAttribution
            )
        )
    }

    private fun manifest(
        id: String,
        files: Map<String, String>,
        type: String,
        territory: String? = null,
        expiry: String? = null,
        includeLicence: Boolean = true,
        includeAttribution: Boolean = true
    ): String {
        val checksums = files.entries.joinToString(",") { (path, content) ->
            """"$path":"${sha256(content.toByteArray())}""""
        }
        val paths = files.keys.joinToString(",") { "\"$it\"" }
        val fields = buildList {
            add(""""id":"$id"""")
            add(""""type":"$type"""")
            add(""""version":"1.0"""")
            add(""""locale":"en-IN"""")
            if (includeLicence) add(""""licence":"CC0-1.0"""")
            if (includeAttribution) add(""""attribution":"Sample content"""")
            territory?.let { add(""""territory":$it""") }
            expiry?.let { add(""""expiry":$it""") }
            add(""""checksum":{$checksums}""")
            add(""""files":[$paths]""")
        }
        return fields.joinToString(prefix = "{", postfix = "}", separator = ",")
    }

    private fun repoRoot(): File =
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main/assets/packs").isDirectory }

    private class DirectoryPackSource(private val root: File) : ContentPackSource {
        override fun listPackIds(): List<String> =
            root.listFiles()
                ?.filter { it.isDirectory }
                ?.map { it.name }
                .orEmpty()

        override fun open(packId: String, path: String): InputStream? {
            val packDir = File(root, packId)
            val file = File(packDir, path)
            val canonicalPack = packDir.canonicalFile
            if (!file.canonicalPath.startsWith(canonicalPack.path + File.separator)) return null
            return if (file.isFile) file.inputStream() else null
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private inline fun <reified T : Throwable> expectThrows(
        messagePart: String? = null,
        block: () -> Unit
    ): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) {
                if (messagePart != null) {
                    assertTrue(
                        "message was: ${e.message}",
                        e.message.orEmpty().contains(messagePart)
                    )
                }
                return e
            }
            throw e
        }
        fail("expected ${T::class.simpleName}")
        throw AssertionError("unreachable")
    }
}
