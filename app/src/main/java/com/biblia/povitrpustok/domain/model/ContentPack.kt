package com.biblia.povitrpustok.domain.model

import java.time.LocalDate

/**
 * One bundled content pack (F1-18).
 *
 * Packs are shipped inside the APK under `assets/packs/<id>/` and are
 * replaced only by an app update. They carry data, not code: [ContentPackType]
 * restricts every declared file to a JSON, text, or audio extension.
 *
 * Licence metadata is part of the pack rather than a side table so a pack
 * cannot be added without declaring its terms. [territory] is empty when the
 * licence is worldwide; [expiry] is null for content with no end date.
 */
data class ContentPack(
    val id: String,
    val type: ContentPackType,
    val version: String,
    val locale: String,
    val licence: String,
    val attribution: String,
    val territory: List<String>,
    val expiry: LocalDate?,
    val files: List<ContentPackFile>
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(files.isNotEmpty()) { "a content pack must declare at least one file" }
    }

    /** True when [expiry] has passed; the expiry date itself is still valid. */
    fun isExpired(today: LocalDate = LocalDate.now()): Boolean =
        expiry?.isBefore(today) == true

    fun file(path: String): ContentPackFile? = files.firstOrNull { it.path == path }
}

/**
 * The kind of content a pack carries.
 *
 * The parenthetical in F1-18 ("JSON/text/audio only") constrains the payload
 * format, not the content class: [CALENDAR] is a JSON payload whose annual
 * regeneration makes a territory and expiry mandatory.
 */
enum class ContentPackType(
    val manifestName: String,
    val allowedExtensions: Set<String>,
    val requiresTerritory: Boolean = false,
    val requiresExpiry: Boolean = false
) {
    JSON("json", setOf("json")),
    TEXT("text", setOf("txt", "md")),
    AUDIO("audio", setOf("mp3", "ogg", "opus", "wav", "m4a")),
    CALENDAR("calendar", setOf("json"), requiresTerritory = true, requiresExpiry = true);

    companion object {
        fun fromManifestName(value: String): ContentPackType? =
            entries.firstOrNull { it.manifestName == value }
    }
}

/** One payload file and the SHA-256 digest the manifest declares for it. */
data class ContentPackFile(
    val path: String,
    val sha256: String
)
