package com.biblia.povitrpustok.core.pack

import com.biblia.povitrpustok.core.result.ContentPackExpiredException
import com.biblia.povitrpustok.core.result.ContentPackManifestException
import com.biblia.povitrpustok.core.result.ContentPackValidationException
import com.biblia.povitrpustok.domain.model.ContentPack
import com.biblia.povitrpustok.domain.model.ContentPackFile
import com.biblia.povitrpustok.domain.model.ContentPackType
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Parses and validates `assets/packs/<pack-id>/manifest.json` (F1-18).
 *
 * Validation is deliberately strict and fail-closed:
 *  - unknown top-level fields are rejected, so a typo such as `expiryy`
 *    cannot silently disable an expiry;
 *  - the manifest id must match its directory;
 *  - every payload path is relative, stays inside the pack, and uses an
 *    extension allowed by [ContentPackType] (no executable content);
 *  - `files` and `checksum` must describe exactly the same paths, each with
 *    a lowercase SHA-256 digest;
 *  - calendar packs must declare a territory and expiry, and an expired pack
 *    is rejected at parse time.
 *
 * [today] is injectable so expiry tests do not depend on the wall clock.
 */
class ContentPackManifestParser(
    private val today: () -> LocalDate = LocalDate::now
) {

    fun parse(packId: String, manifestJson: String): ContentPack {
        val root = parseJson(packId, manifestJson)
        rejectUnknownFields(packId, root)

        val id = requiredString(packId, root, "id", MAX_ID_LENGTH)
        if (id != packId) {
            throw ContentPackValidationException(
                packId,
                "id '$id' does not match its directory"
            )
        }
        if (!ID_PATTERN.matches(id)) {
            throw ContentPackValidationException(
                packId,
                "id must be lowercase words separated by hyphens"
            )
        }

        val typeName = requiredString(packId, root, "type", MAX_TYPE_LENGTH)
        val type = ContentPackType.fromManifestName(typeName)
            ?: throw ContentPackValidationException(
                packId,
                "unsupported type '$typeName'; expected one of " +
                    ContentPackType.entries.joinToString { it.manifestName }
            )

        val version = requiredString(packId, root, "version", MAX_VERSION_LENGTH)
        if (!VERSION_PATTERN.matches(version)) {
            throw ContentPackValidationException(
                packId,
                "'version' must contain only letters, digits, '.', '_' or '-'"
            )
        }

        val locale = requiredString(packId, root, "locale", MAX_LOCALE_LENGTH)
        if (!LOCALE_PATTERN.matches(locale)) {
            throw ContentPackValidationException(packId, "'locale' is not a BCP-47 tag")
        }

        val licence = requiredString(packId, root, "licence", MAX_LICENCE_LENGTH)
        val attribution = requiredString(
            packId,
            root,
            "attribution",
            MAX_ATTRIBUTION_LENGTH
        )
        val territory = optionalStringArray(packId, root, "territory")
        val expiry = optionalExpiry(packId, root)
        val files = requiredFilePaths(packId, root, type)
        val checksums = requiredChecksums(packId, root, files)

        if (type.requiresTerritory && territory.isEmpty()) {
            throw ContentPackValidationException(
                packId,
                "type '${type.manifestName}' requires a territory"
            )
        }
        if (type.requiresExpiry && expiry == null) {
            throw ContentPackValidationException(
                packId,
                "type '${type.manifestName}' requires an expiry date"
            )
        }

        val pack = ContentPack(
            id = id,
            type = type,
            version = version,
            locale = locale,
            licence = licence,
            attribution = attribution,
            territory = territory,
            expiry = expiry,
            files = files.map { path ->
                ContentPackFile(path = path, sha256 = checksums.getValue(path))
            }
        )

        val now = today()
        if (expiry != null && expiry.isBefore(now)) {
            throw ContentPackExpiredException(packId, expiry, now)
        }
        return pack
    }

    private fun parseJson(packId: String, manifestJson: String): JsonObject {
        val element: JsonElement = try {
            val reader = JsonReader(StringReader(manifestJson)).apply {
                setStrictness(Strictness.STRICT)
            }
            val parsed = JsonParser.parseReader(reader)
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw ContentPackManifestException(
                    packId,
                    "unexpected content after the JSON object"
                )
            }
            parsed
        } catch (e: JsonParseException) {
            throw ContentPackManifestException(packId, "not valid JSON", e)
        } catch (e: IOException) {
            throw ContentPackManifestException(packId, "not valid JSON", e)
        } catch (e: IllegalStateException) {
            throw ContentPackManifestException(packId, "not valid JSON", e)
        }
        if (element == null || !element.isJsonObject) {
            throw ContentPackManifestException(packId, "root must be a JSON object")
        }
        return element.asJsonObject
    }

    private fun rejectUnknownFields(packId: String, root: JsonObject) {
        val unknown = root.keySet() - KNOWN_FIELDS
        if (unknown.isNotEmpty()) {
            throw ContentPackValidationException(
                packId,
                "unknown field(s): ${unknown.sorted().joinToString()}"
            )
        }
    }

    private fun requiredString(
        packId: String,
        root: JsonObject,
        field: String,
        maxLength: Int
    ): String {
        val element = root.get(field)
            ?: throw ContentPackValidationException(packId, "missing '$field'")
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw ContentPackValidationException(packId, "'$field' must be a string")
        }
        val value = element.asString
        if (value.isBlank()) {
            throw ContentPackValidationException(packId, "'$field' must not be blank")
        }
        if (value.length > maxLength) {
            throw ContentPackValidationException(
                packId,
                "'$field' must be at most $maxLength characters"
            )
        }
        return value
    }

    private fun optionalStringArray(
        packId: String,
        root: JsonObject,
        field: String
    ): List<String> {
        val element = root.get(field) ?: return emptyList()
        if (element.isJsonNull) return emptyList()
        if (!element.isJsonArray) {
            throw ContentPackValidationException(packId, "'$field' must be an array")
        }
        val values = element.asJsonArray.mapIndexed { index, item ->
            if (!item.isJsonPrimitive || !item.asJsonPrimitive.isString) {
                throw ContentPackValidationException(
                    packId,
                    "'$field[$index]' must be a string"
                )
            }
            item.asString
        }
        if (values.size != values.toSet().size) {
            throw ContentPackValidationException(packId, "'$field' contains duplicates")
        }
        values.forEach { code ->
            if (!TERRITORY_PATTERN.matches(code)) {
                throw ContentPackValidationException(
                    packId,
                    "'$field' entry '$code' must be an ISO 3166-1 alpha-2 code"
                )
            }
        }
        return values
    }

    private fun optionalExpiry(packId: String, root: JsonObject): LocalDate? {
        val element = root.get("expiry") ?: return null
        if (element.isJsonNull) return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw ContentPackValidationException(
                packId,
                "'expiry' must be an ISO-8601 date (yyyy-MM-dd)"
            )
        }
        return try {
            LocalDate.parse(element.asString)
        } catch (e: DateTimeParseException) {
            throw ContentPackValidationException(
                packId,
                "'expiry' must be an ISO-8601 date (yyyy-MM-dd)"
            )
        }
    }

    private fun requiredFilePaths(
        packId: String,
        root: JsonObject,
        type: ContentPackType
    ): List<String> {
        val element = root.get("files")
            ?: throw ContentPackValidationException(packId, "missing 'files'")
        if (!element.isJsonArray) {
            throw ContentPackValidationException(packId, "'files' must be an array")
        }
        val paths = element.asJsonArray.mapIndexed { index, item ->
            if (!item.isJsonPrimitive || !item.asJsonPrimitive.isString) {
                throw ContentPackValidationException(
                    packId,
                    "'files[$index]' must be a string"
                )
            }
            item.asString
        }
        if (paths.isEmpty()) {
            throw ContentPackValidationException(
                packId,
                "a content pack must declare at least one file"
            )
        }
        if (paths.size != paths.toSet().size) {
            throw ContentPackValidationException(packId, "'files' contains duplicates")
        }
        paths.forEach { path -> validatePath(packId, path, type) }
        return paths
    }

    private fun validatePath(packId: String, path: String, type: ContentPackType) {
        if (path == MANIFEST_FILE) {
            throw ContentPackValidationException(
                packId,
                "'$MANIFEST_FILE' cannot be a payload file"
            )
        }
        if (path.isBlank() || path.length > MAX_PATH_LENGTH) {
            throw ContentPackValidationException(
                packId,
                "payload paths must be 1..$MAX_PATH_LENGTH characters"
            )
        }
        if (path.contains('\\') || path.startsWith('/') || path.contains(':')) {
            throw ContentPackValidationException(
                packId,
                "payload path '$path' must be relative and use '/' separators"
            )
        }
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            throw ContentPackValidationException(
                packId,
                "payload path '$path' must not contain empty, '.' or '..' segments"
            )
        }
        if (segments.any { segment -> segment.any { it.isISOControl() } }) {
            throw ContentPackValidationException(
                packId,
                "payload path '$path' must not contain control characters"
            )
        }
        val extension = path.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        if (extension !in type.allowedExtensions) {
            throw ContentPackValidationException(
                packId,
                "payload '$path' is not allowed for type '${type.manifestName}'; " +
                    "allowed extensions: ${type.allowedExtensions.sorted().joinToString()}"
            )
        }
    }

    private fun requiredChecksums(
        packId: String,
        root: JsonObject,
        files: List<String>
    ): Map<String, String> {
        val element = root.get("checksum")
            ?: throw ContentPackValidationException(packId, "missing 'checksum'")
        if (!element.isJsonObject) {
            throw ContentPackValidationException(
                packId,
                "'checksum' must map each file path to a SHA-256 digest"
            )
        }
        val checksums = element.asJsonObject.entrySet().associate { (path, digest) ->
            if (!digest.isJsonPrimitive || !digest.asJsonPrimitive.isString) {
                throw ContentPackValidationException(
                    packId,
                    "'checksum.$path' must be a string"
                )
            }
            path to digest.asString
        }
        val declared = files.toSet()
        val checksummed = checksums.keys
        if (declared != checksummed) {
            val missing = declared - checksummed
            val extra = checksummed - declared
            throw ContentPackValidationException(
                packId,
                buildString {
                    append("'checksum' must cover exactly 'files'")
                    if (missing.isNotEmpty()) append("; missing: ${missing.sorted().joinToString()}")
                    if (extra.isNotEmpty()) append("; undeclared: ${extra.sorted().joinToString()}")
                }
            )
        }
        checksums.forEach { (path, digest) ->
            if (!SHA256_PATTERN.matches(digest)) {
                throw ContentPackValidationException(
                    packId,
                    "'checksum.$path' must be a lowercase 64-character SHA-256 digest"
                )
            }
        }
        return checksums
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val MAX_ID_LENGTH = 64
        const val MAX_TYPE_LENGTH = 16
        const val MAX_VERSION_LENGTH = 64
        const val MAX_LOCALE_LENGTH = 64
        const val MAX_LICENCE_LENGTH = 200
        const val MAX_ATTRIBUTION_LENGTH = 2_000
        const val MAX_PATH_LENGTH = 200

        val KNOWN_FIELDS = setOf(
            "id",
            "type",
            "version",
            "locale",
            "licence",
            "attribution",
            "territory",
            "expiry",
            "checksum",
            "files"
        )
        val ID_PATTERN = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        val VERSION_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
        val LOCALE_PATTERN = Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")
        val TERRITORY_PATTERN = Regex("[A-Z]{2}")
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
