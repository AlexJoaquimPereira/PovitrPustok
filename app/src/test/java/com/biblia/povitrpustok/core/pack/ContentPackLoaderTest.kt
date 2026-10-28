package com.biblia.povitrpustok.core.pack

import com.biblia.povitrpustok.core.result.ContentPackChecksumException
import com.biblia.povitrpustok.core.result.ContentPackExpiredException
import com.biblia.povitrpustok.core.result.ContentPackManifestException
import com.biblia.povitrpustok.core.result.ContentPackNotFoundException
import com.biblia.povitrpustok.core.result.ContentPackValidationException
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

/** F1-18: the loader is fail-closed and verifies every declared digest. */
class ContentPackLoaderTest {

    private val today = LocalDate.of(2027, 1, 15)
    private val loader = ContentPackLoader(
        source = MapSource(),
        parser = ContentPackManifestParser { today }
    )

    @Test
    fun valid_pack_loads_and_verifies_every_file() {
        val source = MapSource()
        val pack = source.addPack(
            "sample",
            files = mapOf(
                "data.json" to """{"value":1}""".toByteArray(),
                "nested/more.json" to """{"value":2}""".toByteArray()
            )
        )

        val loaded = ContentPackLoader(source).load(pack)

        assertEquals("sample", loaded.id)
        assertEquals(2, loaded.files.size)
        assertEquals(
            """{"value":1}""",
            ContentPackLoader(source).open(loaded, "data.json")
                .bufferedReader()
                .use { it.readText() }
        )
    }

    @Test
    fun missing_manifest_is_a_typed_error() {
        val source = MapSource()
        source.files["sample"] = mutableMapOf("data.json" to "hello".toByteArray())

        expectThrows<ContentPackNotFoundException>("no readable manifest") {
            ContentPackLoader(source).load("sample")
        }
    }

    @Test
    fun corrupt_manifest_is_a_typed_error() {
        val source = MapSource()
        source.files["sample"] = mutableMapOf(
            "manifest.json" to """{"id": """.toByteArray()
        )

        expectThrows<ContentPackManifestException>("not valid JSON") {
            ContentPackLoader(source).load("sample")
        }
    }

    @Test
    fun checksum_mismatch_is_rejected() {
        val source = MapSource()
        source.addPack("sample", files = mapOf("data.json" to "hello".toByteArray()))
        source.files.getValue("sample")["data.json"] = "tampered".toByteArray()

        val error = expectThrows<ContentPackChecksumException>("checksum mismatch") {
            ContentPackLoader(source).load("sample")
        }
        assertTrue(error.message.orEmpty().contains(sha256("hello".toByteArray())))
        assertTrue(error.message.orEmpty().contains(sha256("tampered".toByteArray())))
    }

    @Test
    fun missing_declared_file_is_rejected() {
        val source = MapSource()
        source.addPack("sample", files = mapOf("data.json" to "hello".toByteArray()))
        source.files.getValue("sample").remove("data.json")

        expectThrows<ContentPackChecksumException>("is missing 'data.json'") {
            ContentPackLoader(source).load("sample")
        }
    }

    @Test
    fun expired_pack_is_rejected() {
        val source = MapSource()
        source.addPack(
            "calendar",
            type = "calendar",
            files = mapOf("calendar.json" to "{}".toByteArray()),
            territory = """["IN"]""",
            expiry = """"2027-01-14""""
        )

        expectThrows<ContentPackExpiredException>("expired on 2027-01-14") {
            ContentPackLoader(
                source,
                ContentPackManifestParser { today }
            ).load("calendar")
        }
    }

    @Test
    fun load_all_returns_every_pack_sorted_by_id() {
        val source = MapSource()
        source.addPack("zeta", files = mapOf("data.json" to "z".toByteArray()))
        source.addPack("alpha", files = mapOf("data.json" to "a".toByteArray()))

        assertEquals(
            listOf("alpha", "zeta"),
            ContentPackLoader(source).loadAll().map { it.id }
        )
    }

    @Test
    fun open_rejects_a_path_the_manifest_does_not_declare() {
        val source = MapSource()
        source.addPack("sample", files = mapOf("data.json" to "hello".toByteArray()))
        val pack = ContentPackLoader(source).load("sample")

        expectThrows<ContentPackValidationException>("is not declared") {
            ContentPackLoader(source).open(pack, "other.json")
        }
    }

    private class MapSource : ContentPackSource {
        val files = mutableMapOf<String, MutableMap<String, ByteArray>>()

        fun addPack(
            id: String,
            files: Map<String, ByteArray>,
            type: String = "json",
            territory: String? = null,
            expiry: String? = null
        ): String {
            val payloads = files.toMutableMap()
            payloads[ContentPackLoader.MANIFEST_FILE] = manifest(
                id = id,
                type = type,
                files = files,
                territory = territory,
                expiry = expiry
            )
            this.files[id] = payloads
            return id
        }

        override fun listPackIds(): List<String> = files.keys.toList()

        override fun open(packId: String, path: String): InputStream? =
            files[packId]?.get(path)?.inputStream()

        private fun manifest(
            id: String,
            type: String,
            files: Map<String, ByteArray>,
            territory: String?,
            expiry: String?
        ): ByteArray {
            val checksums = files.entries.joinToString(",") { (path, bytes) ->
                """"$path":"${sha256(bytes)}""""
            }
            val paths = files.keys.joinToString(",") { "\"$it\"" }
            val fields = buildList {
                add(""""id":"$id"""")
                add(""""type":"$type"""")
                add(""""version":"1.0"""")
                add(""""locale":"en-IN"""")
                add(""""licence":"CC0-1.0"""")
                add(""""attribution":"Sample content"""")
                territory?.let { add(""""territory":$it""") }
                expiry?.let { add(""""expiry":$it""") }
                add(""""checksum":{$checksums}""")
                add(""""files":[$paths]""")
            }
            return fields.joinToString(prefix = "{", postfix = "}", separator = ",")
                .toByteArray()
        }
    }

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
