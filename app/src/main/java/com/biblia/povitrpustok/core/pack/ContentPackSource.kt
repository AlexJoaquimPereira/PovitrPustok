package com.biblia.povitrpustok.core.pack

import java.io.InputStream

/**
 * Read-only access to bundled pack assets (F1-18).
 *
 * Production reads the APK through [AssetContentPackSource]; tests use an
 * in-memory implementation. There is deliberately no download, cache, or
 * replacement API: packs change only through an app update.
 */
interface ContentPackSource {

    /** Pack directory names directly under `assets/packs`, sorted. */
    fun listPackIds(): List<String>

    /**
     * Opens `assets/packs/<packId>/<path>`, or returns null when the asset
     * does not exist. The caller owns the returned stream.
     */
    fun open(packId: String, path: String): InputStream?
}
