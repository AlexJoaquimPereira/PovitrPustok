package com.biblia.povitrpustok.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The running app's version name, or `null` when the package manager cannot
 * answer.
 *
 * `buildConfig` is not enabled for this module, so the value comes from
 * [android.content.pm.PackageManager]. Kept in `ui` because both Settings and
 * the About pages state the version; one helper means they can never disagree.
 */
@Composable
fun rememberAppVersionName(): String? {
    val context = LocalContext.current
    return remember(context) { context.appVersionName() }
}

@Suppress("DEPRECATION")
private fun Context.appVersionName(): String? = runCatching {
    packageManager.getPackageInfo(packageName, 0).versionName
}.getOrNull()
