package com.biblia.povitrpustok.domain.model

/**
 * In-app reading preferences (F-15).
 *
 * The system font scale stays authoritative; these values are a bounded
 * multiplier on top of it, so the user can tune Scripture without the app
 * fighting Android's accessibility setting.
 */
enum class ScriptureSize(val scale: Float) {
    SMALL(0.9f),
    MEDIUM(1f),
    LARGE(1.2f),
    EXTRA_LARGE(1.35f);

    companion object {
        /** Tolerates a stored value from a newer/older build. */
        fun fromNameOrMedium(name: String?): ScriptureSize =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/** Reading leading multiplier; [NORMAL] reproduces the canonical 32 sp leading. */
enum class LineSpacing(val scale: Float) {
    COMPACT(0.92f),
    NORMAL(1f),
    RELAXED(1.15f);

    companion object {
        fun fromNameOrNormal(name: String?): LineSpacing =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/** App theme choice; [SYSTEM] follows the device setting (the default). */
enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromNameOrSystem(name: String?): AppTheme =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * The complete, persistable reading preference set. Defaults are the
 * pre-Settings behaviour, so an existing install sees no change.
 */
data class ReadingPreferences(
    val scriptureSize: ScriptureSize = ScriptureSize.MEDIUM,
    val lineSpacing: LineSpacing = LineSpacing.NORMAL,
    val redLetter: Boolean = true,
    val theme: AppTheme = AppTheme.SYSTEM
)
