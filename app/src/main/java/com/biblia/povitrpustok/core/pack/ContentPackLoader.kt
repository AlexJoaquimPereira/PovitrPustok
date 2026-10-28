package com.biblia.povitrpustok.core.pack

import com.biblia.povitrpustok.core.result.ContentPackChecksumException
import com.biblia.povitrpustok.core.result.ContentPackNotFoundException
import com.biblia.povitrpustok.core.result.ContentPackValidationException
import com.biblia.povitrpustok.domain.model.ContentPack
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Loads bundled content packs and verifies every declared SHA-256 digest
 * (F1-18).
 *
 * Loading is fail-closed: the first missing manifest, invalid manifest, or
 * checksum mismatch aborts the whole load rather than returning partial
 * content. There is no network or cache layer; [ContentPackSource] is the
 * only I/O boundary.
 */
class ContentPackLoader(
    private val source: ContentPackSource,
    private val parser: ContentPackManifestParser = ContentPackManifestParser()
) {

    /** Loads every pack under `assets/packs`, sorted by id. */
    fun loadAll(): List<ContentPack> =
        source.listPackIds().sorted().map(::load)

    /** Loads one pack and verifies all of its declared files. */
    fun load(packId: String): ContentPack {
        val manifestJson = readText(packId, MANIFEST_FILE)
            ?: throw ContentPackNotFoundException(packId)
        val pack = parser.parse(packId, manifestJson)
        pack.files.forEach { file ->
            val actual = try {
                source.open(packId, file.path)?.use(::sha256)
            } catch (e: IOException) {
                throw ContentPackChecksumException(
                    packId,
                    file.path,
                    file.sha256,
                    actual = null,
                    cause = e
                )
            }
            if (actual == null || actual != file.sha256) {
                throw ContentPackChecksumException(packId, file.path, file.sha256, actual)
            }
        }
        return pack
    }

    /**
     * Opens a payload file that [pack] declares. The digest is checked by
     * [load]; this method only enforces that the path is declared.
     */
    fun open(pack: ContentPack, path: String): InputStream {
        val file = pack.file(path)
            ?: throw ContentPackValidationException(
                pack.id,
                "'$path' is not declared by the manifest"
            )
        return source.open(pack.id, file.path)
            ?: throw ContentPackChecksumException(pack.id, file.path, file.sha256, null)
    }

    private fun readText(packId: String, path: String): String? {
        val stream = source.open(packId, path) ?: return null
        return try {
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            throw ContentPackNotFoundException(packId, e)
        }
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MANIFEST_FILE = "manifest.json"
    }
}
