package com.biblia.povitrpustok.feature.about

import androidx.annotation.StringRes
import com.biblia.povitrpustok.R

/**
 * The licence inventory shown on [AboutLicensesScreen].
 *
 * Versions are the ones resolved by the version catalog and the Compose BOM at
 * the time of writing; each row links to the project's own licence page so the
 * text can be checked at the source. Only libraries the APK actually ships are
 * listed as runtime dependencies — test and build tooling are kept separate
 * because they are never part of a release build.
 */
internal data class LicenceComponent(
    /** Stable id used in the `about.licenses.component.<id>` test tag. */
    val id: String,
    val name: String,
    val version: String?,
    @param:StringRes val licence: Int,
    val url: String,
    /** True for a library that arrives through another dependency. */
    val transitive: Boolean = false
)

private const val ANDROIDX_LICENSE =
    "https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt"
private const val ANDROID_TEST_LICENSE =
    "https://github.com/android/android-test/blob/main/LICENSE"
private const val KOTLIN_LICENSE =
    "https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt"
private const val MATERIAL_LICENSE =
    "https://github.com/material-components/material-components-android/blob/master/LICENSE"
private const val DAGGER_LICENSE =
    "https://github.com/google/dagger/blob/master/LICENSE.txt"
private const val RETROFIT_LICENSE =
    "https://github.com/square/retrofit/blob/master/LICENSE.txt"
private const val OKHTTP_LICENSE =
    "https://github.com/square/okhttp/blob/master/LICENSE.txt"
private const val GSON_LICENSE =
    "https://github.com/google/gson/blob/main/LICENSE"
private const val COROUTINES_LICENSE =
    "https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt"
private const val JUNIT_LICENSE =
    "https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt"
private const val ROBOLECTRIC_LICENSE =
    "https://github.com/robolectric/robolectric/blob/master/LICENSE"
private const val GRADLE_LICENSE =
    "https://github.com/gradle/gradle/blob/master/LICENSE"
private const val AGP_LICENSE =
    "https://android.googlesource.com/platform/tools/base/"
private const val KSP_LICENSE =
    "https://github.com/google/ksp/blob/main/LICENSE"

/** Libraries packaged in the APK and shipped to the device. */
internal val runtimeLicences: List<LicenceComponent> = listOf(
    LicenceComponent(
        id = "kotlin",
        name = "Kotlin",
        version = "2.2.10",
        licence = R.string.about_license_apache,
        url = KOTLIN_LICENSE
    ),
    LicenceComponent(
        id = "androidx_core",
        name = "AndroidX Core",
        version = "1.10.1",
        licence = R.string.about_license_apache,
        url = ANDROIDX_LICENSE
    ),
    LicenceComponent(
        id = "appcompat",
        name = "AppCompat",
        version = "1.6.1",
        licence = R.string.about_license_apache,
        url = ANDROIDX_LICENSE
    ),
    LicenceComponent(
        id = "activity",
        name = "Activity",
        version = "1.10.1",
        licence = R.string.about_license_apache,
        url = ANDROIDX_LICENSE
    ),
    LicenceComponent(
        id = "lifecycle",
        name = "Lifecycle",
        version = "2.9.4",
        licence = R.string.about_license_apache,
        url = ANDROIDX_LICENSE
    ),
    LicenceComponent(
        id = "navigation",
        name = "Navigation",
        version = "2.9.3",
        licence = R.string.about_license_apache,
        url = ANDROIDX_LICENSE
    ),
    LicenceComponent(
        id = "room",
        name = "Room",
        version = "2.7.2",
        licence = R.string.about_license_apache,
        url = ANDROIDX_LICENSE
    ),
    LicenceComponent(
        id = "compose",
        name = "Jetpack Compose BOM (Material 3, Icons)",
        version = "2025.11.01",
        licence = R.string.about_license_apache,
        url = ANDROIDX_LICENSE
    ),
    LicenceComponent(
        id = "material",
        name = "Material Components",
        version = "1.10.0",
        licence = R.string.about_license_apache,
        url = MATERIAL_LICENSE
    ),
    LicenceComponent(
        id = "hilt",
        name = "Hilt / Dagger",
        version = "2.60.1",
        licence = R.string.about_license_apache,
        url = DAGGER_LICENSE
    ),
    LicenceComponent(
        id = "hilt_navigation",
        name = "Hilt Navigation Compose",
        version = "1.3.0",
        licence = R.string.about_license_apache,
        url = ANDROIDX_LICENSE
    ),
    LicenceComponent(
        id = "retrofit",
        name = "Retrofit",
        version = "3.0.0",
        licence = R.string.about_license_apache,
        url = RETROFIT_LICENSE
    ),
    LicenceComponent(
        id = "okhttp",
        name = "OkHttp",
        version = null,
        licence = R.string.about_license_apache,
        url = OKHTTP_LICENSE,
        transitive = true
    ),
    LicenceComponent(
        id = "gson",
        name = "Gson",
        version = null,
        licence = R.string.about_license_apache,
        url = GSON_LICENSE,
        transitive = true
    ),
    LicenceComponent(
        id = "coroutines",
        name = "Kotlin Coroutines",
        version = "1.9.0",
        licence = R.string.about_license_apache,
        url = COROUTINES_LICENSE
    )
)

/** Libraries used by the JVM and device test suites only. */
internal val testLicences: List<LicenceComponent> = listOf(
    LicenceComponent(
        id = "junit",
        name = "JUnit 4",
        version = "4.13.2",
        licence = R.string.about_license_epl,
        url = JUNIT_LICENSE
    ),
    LicenceComponent(
        id = "robolectric",
        name = "Robolectric",
        version = "4.16.1",
        licence = R.string.about_license_mit,
        url = ROBOLECTRIC_LICENSE
    ),
    LicenceComponent(
        id = "androidx_test",
        name = "AndroidX Test",
        version = "1.7.0",
        licence = R.string.about_license_apache,
        url = ANDROID_TEST_LICENSE
    ),
    LicenceComponent(
        id = "espresso",
        name = "Espresso",
        version = "3.5.1",
        licence = R.string.about_license_apache,
        url = ANDROID_TEST_LICENSE
    ),
    LicenceComponent(
        id = "compose_test",
        name = "Compose UI Test",
        version = null,
        licence = R.string.about_license_apache,
        url = ANDROIDX_LICENSE
    )
)

/** Tools that compile the app; none of them are packaged in the APK. */
internal val buildLicences: List<LicenceComponent> = listOf(
    LicenceComponent(
        id = "gradle",
        name = "Gradle",
        version = null,
        licence = R.string.about_license_apache,
        url = GRADLE_LICENSE
    ),
    LicenceComponent(
        id = "agp",
        name = "Android Gradle Plugin",
        version = "9.3.1",
        licence = R.string.about_license_apache,
        url = AGP_LICENSE
    ),
    LicenceComponent(
        id = "kotlin_compiler",
        name = "Kotlin compiler",
        version = "2.2.10",
        licence = R.string.about_license_apache,
        url = KOTLIN_LICENSE
    ),
    LicenceComponent(
        id = "ksp",
        name = "KSP",
        version = "2.2.10-2.0.2",
        licence = R.string.about_license_apache,
        url = KSP_LICENSE
    )
)
