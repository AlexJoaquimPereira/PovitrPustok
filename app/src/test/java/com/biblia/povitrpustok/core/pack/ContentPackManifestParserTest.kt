package com.biblia.povitrpustok.core.pack

import com.biblia.povitrpustok.core.result.ContentPackExpiredException
import com.biblia.povitrpustok.core.result.ContentPackManifestException
import com.biblia.povitrpustok.core.result.ContentPackValidationException
import com.biblia.povitrpustok.domain.model.ContentPackType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** F1-18: manifests are strict, fail-closed, and reject executable payloads. */
class ContentPackManifestParserTest {

    private val digest = "a".repeat(64)
    private val today = LocalDate.of(2027, 1, 15)
    private val parser = ContentPackManifestParser { today }

    @Test
    fun valid_json_pack_parses_every_field() {
        val pack = parser.parse(
            "sample",
            manifest(territory = """["IN"]""", expiry = """"2027-12-31"""")
        )

        assertEquals("sample", pack.id)
        assertEquals(ContentPackType.JSON, pack.type)
        assertEquals("1.0", pack.version)
        assertEquals("en-IN", pack.locale)
        assertEquals("CC0-1.0", pack.licence)
        assertEquals("Sample content", pack.attribution)
        assertEquals(listOf("IN"), pack.territory)
        assertEquals(LocalDate.of(2027, 12, 31), pack.expiry)
        assertEquals(digest, pack.file("data.json")?.sha256)
    }

    @Test
    fun calendar_pack_requires_territory_and_expiry() {
        expectThrows<ContentPackValidationException>("requires a territory") {
            parser.parse(
                "sample",
                manifest(type = "calendar", expiry = """"2027-12-31"""")
            )
        }
        expectThrows<ContentPackValidationException>("requires an expiry date") {
            parser.parse("sample", manifest(type = "calendar", territory = """["IN"]"""))
        }

        val pack = parser.parse(
            "sample",
            manifest(
                type = "calendar",
                territory = """["IN"]""",
                expiry = """"2027-12-31""""
            )
        )
        assertEquals(ContentPackType.CALENDAR, pack.type)
    }

    @Test
    fun expired_pack_is_rejected_but_the_expiry_date_itself_is_valid() {
        expectThrows<ContentPackExpiredException>("expired on 2027-01-14") {
            parser.parse("sample", manifest(expiry = """"2027-01-14""""))
        }
        val pack = parser.parse("sample", manifest(expiry = """"2027-01-15""""))
        assertEquals(LocalDate.of(2027, 1, 15), pack.expiry)
    }

    @Test
    fun missing_or_blank_licence_and_attribution_are_rejected() {
        expectThrows<ContentPackValidationException>("'licence' must not be blank") {
            parser.parse("sample", manifest(licence = ""))
        }
        expectThrows<ContentPackValidationException>("'attribution' must not be blank") {
            parser.parse("sample", manifest(attribution = ""))
        }
        expectThrows<ContentPackValidationException>("missing 'licence'") {
            parser.parse("sample", manifest(includeLicence = false))
        }
    }

    @Test
    fun corrupt_or_non_object_json_is_rejected() {
        expectThrows<ContentPackManifestException>("not valid JSON") {
            parser.parse("sample", """{"id": """)
        }
        expectThrows<ContentPackManifestException>("root must be a JSON object") {
            parser.parse("sample", "[]")
        }
        expectThrows<ContentPackManifestException>("not valid JSON") {
            parser.parse("sample", manifest() + " trailing")
        }
    }

    @Test
    fun unknown_type_and_unknown_fields_are_rejected() {
        expectThrows<ContentPackValidationException>("unsupported type 'executable'") {
            parser.parse("sample", manifest(type = "executable"))
        }
        expectThrows<ContentPackValidationException>("unknown field(s): expiryy") {
            parser.parse(
                "sample",
                manifest(extraFields = """"expiryy": "2027-12-31"""")
            )
        }
    }

    @Test
    fun manifest_id_must_match_its_directory() {
        expectThrows<ContentPackValidationException>("does not match its directory") {
            parser.parse("other", manifest())
        }
        expectThrows<ContentPackValidationException>("lowercase words") {
            parser.parse("Sample_Pack", manifest(id = "Sample_Pack"))
        }
    }

    @Test
    fun executable_and_unknown_payload_extensions_are_rejected() {
        expectThrows<ContentPackValidationException>("is not allowed for type 'json'") {
            parser.parse(
                "sample",
                manifest(
                    files = """["payload.dex"]""",
                    checksum = """{"payload.dex":"$digest"}"""
                )
            )
        }
        expectThrows<ContentPackValidationException>("is not allowed for type 'text'") {
            parser.parse(
                "sample",
                manifest(
                    type = "text",
                    files = """["notes.pdf"]""",
                    checksum = """{"notes.pdf":"$digest"}"""
                )
            )
        }
    }

    @Test
    fun payload_paths_must_stay_inside_the_pack() {
        listOf("../data.json", "/data.json", "a//data.json", "a/./data.json").forEach { path ->
            expectThrows<ContentPackValidationException> {
                parser.parse(
                    "sample",
                    manifest(
                        files = """["$path"]""",
                        checksum = """{"$path":"$digest"}"""
                    )
                )
            }
        }
    }

    @Test
    fun checksum_must_cover_exactly_the_declared_files() {
        expectThrows<ContentPackValidationException>("missing: data.json") {
            parser.parse(
                "sample",
                manifest(checksum = """{"other.json":"$digest"}""")
            )
        }
        expectThrows<ContentPackValidationException>("undeclared: other.json") {
            parser.parse(
                "sample",
                manifest(
                    checksum = """{"data.json":"$digest","other.json":"$digest"}"""
                )
            )
        }
    }

    @Test
    fun checksum_must_be_a_lowercase_sha256_digest() {
        listOf("abc", "A".repeat(64), "g".repeat(64)).forEach { badDigest ->
            expectThrows<ContentPackValidationException>("lowercase 64-character SHA-256") {
                parser.parse(
                    "sample",
                    manifest(checksum = """{"data.json":"$badDigest"}""")
                )
            }
        }
    }

    @Test
    fun territory_and_locale_are_validated() {
        expectThrows<ContentPackValidationException>("ISO 3166-1 alpha-2") {
            parser.parse("sample", manifest(territory = """["India"]"""))
        }
        expectThrows<ContentPackValidationException>("BCP-47") {
            parser.parse("sample", manifest(locale = "English (India)"))
        }
    }

    @Test
    fun file_list_must_be_non_empty_and_unique() {
        expectThrows<ContentPackValidationException>("at least one file") {
            parser.parse("sample", manifest(files = "[]", checksum = "{}"))
        }
        expectThrows<ContentPackValidationException>("duplicates") {
            parser.parse(
                "sample",
                manifest(
                    files = """["data.json","data.json"]""",
                    checksum = """{"data.json":"$digest"}"""
                )
            )
        }
    }

    @Test
    fun manifest_file_cannot_be_a_payload_file() {
        expectThrows<ContentPackValidationException>("cannot be a payload file") {
            parser.parse(
                "sample",
                manifest(
                    files = """["manifest.json"]""",
                    checksum = """{"manifest.json":"$digest"}"""
                )
            )
        }
    }

    private fun manifest(
        id: String = "sample",
        type: String = "json",
        version: String = "1.0",
        locale: String = "en-IN",
        licence: String = "CC0-1.0",
        attribution: String = "Sample content",
        territory: String? = null,
        expiry: String? = null,
        files: String = """["data.json"]""",
        checksum: String = """{"data.json":"$digest"}""",
        includeLicence: Boolean = true,
        extraFields: String? = null
    ): String {
        val fields = buildList {
            add(""""id": "$id"""")
            add(""""type": "$type"""")
            add(""""version": "$version"""")
            add(""""locale": "$locale"""")
            if (includeLicence) add(""""licence": "$licence"""")
            add(""""attribution": "$attribution"""")
            territory?.let { add(""""territory": $it""") }
            expiry?.let { add(""""expiry": $it""") }
            add(""""checksum": $checksum""")
            add(""""files": $files""")
            extraFields?.let { add(it) }
        }
        return fields.joinToString(prefix = "{", postfix = "}", separator = ",")
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
