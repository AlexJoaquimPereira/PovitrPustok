package com.biblia.povitrpustok.core.pack

import android.content.res.AssetManager
import java.io.IOException
import java.io.InputStream

/**
 * [ContentPackSource] backed by the APK's read-only assets (F1-18).
 *
 * Only directories that contain at least one entry are reported as packs, so
 * a stray file under `assets/packs/` (for example a placeholder) is not
 * mistaken for a pack directory. A malformed pack still fails when loaded.
 */
class AssetContentPackSource(
    private val assets: AssetManager
) : ContentPackSource {

    override fun listPackIds(): List<String> =
        try {
            assets.list(PACKS_ROOT)
                .orEmpty()
                .filter { entry -> assets.list("$PACKS_ROOT/$entry")?.isNotEmpty() == true }
                .sorted()
        } catch (e: IOException) {
            emptyList()
        }

    override fun open(packId: String, path: String): InputStream? =
        try {
            assets.open("$PACKS_ROOT/$packId/$path")
        } catch (e: IOException) {
            null
        }

    companion object {
        const val PACKS_ROOT = "packs"
    }
}
