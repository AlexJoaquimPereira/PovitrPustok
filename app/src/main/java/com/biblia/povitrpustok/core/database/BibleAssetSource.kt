package com.biblia.povitrpustok.core.database

import java.io.InputStream

/**
 * Source of a packaged Bible database asset.
 *
 * Production uses `context.assets.open(path)`. The abstraction exists so the
 * provider's corrupt-packaged-asset path can be tested without mutating the
 * real corpus (DB-04).
 */
fun interface BibleAssetSource {
    fun open(assetPath: String): InputStream
}
