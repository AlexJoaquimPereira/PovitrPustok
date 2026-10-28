package com.biblia.povitrpustok.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F1-18: pack types restrict payload formats and expiry semantics are explicit. */
class ContentPackTest {

    private val digest = "a".repeat(64)

    private fun pack(
        type: ContentPackType = ContentPackType.JSON,
        expiry: LocalDate? = null
    ) = ContentPack(
        id = "sample",
        type = type,
        version = "1.0",
        locale = "en-IN",
        licence = "CC0-1.0",
        attribution = "Sample content",
        territory = emptyList(),
        expiry = expiry,
        files = listOf(ContentPackFile("data.json", digest))
    )

    @Test
    fun pack_types_allow_only_data_extensions() {
        assertEquals(
            setOf("json"),
            ContentPackType.JSON.allowedExtensions
        )
        assertEquals(
            setOf("txt", "md"),
            ContentPackType.TEXT.allowedExtensions
        )
        assertEquals(
            setOf("mp3", "ogg", "opus", "wav", "m4a"),
            ContentPackType.AUDIO.allowedExtensions
        )
        ContentPackType.entries.forEach { type ->
            assertFalse(type.allowedExtensions.any { it in setOf("apk", "dex", "so", "jar", "sh") })
        }
    }

    @Test
    fun calendar_packs_require_territory_and_expiry() {
        assertTrue(ContentPackType.CALENDAR.requiresTerritory)
        assertTrue(ContentPackType.CALENDAR.requiresExpiry)
        assertFalse(ContentPackType.JSON.requiresTerritory)
        assertFalse(ContentPackType.TEXT.requiresExpiry)
        assertEquals(setOf("json"), ContentPackType.CALENDAR.allowedExtensions)
    }

    @Test
    fun manifest_names_round_trip_and_unknown_names_are_rejected() {
        ContentPackType.entries.forEach { type ->
            assertEquals(type, ContentPackType.fromManifestName(type.manifestName))
        }
        assertNull(ContentPackType.fromManifestName("executable"))
        assertNull(ContentPackType.fromManifestName("JSON"))
    }

    @Test
    fun expiry_date_is_inclusive() {
        val expiry = LocalDate.of(2027, 12, 31)
        assertTrue(pack(expiry = expiry).isExpired(LocalDate.of(2028, 1, 1)))
        assertFalse(pack(expiry = expiry).isExpired(expiry))
        assertFalse(pack(expiry = expiry).isExpired(LocalDate.of(2027, 12, 30)))
        assertFalse(pack(expiry = null).isExpired(LocalDate.of(2100, 1, 1)))
    }

    @Test
    fun files_are_looked_up_by_path() {
        val content = pack()
        assertEquals(digest, content.file("data.json")?.sha256)
        assertNull(content.file("missing.json"))
    }
}
