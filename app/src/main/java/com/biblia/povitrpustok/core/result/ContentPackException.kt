package com.biblia.povitrpustok.core.result

import java.time.LocalDate

/**
 * Base class for content-pack failures (F1-18).
 *
 * Pack loading is fail-closed: a missing, malformed, unlicensed, expired, or
 * checksum-mismatched pack surfaces as one of these typed errors rather than
 * silently loading partial content.
 */
sealed class ContentPackException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** The pack has no readable `manifest.json` in the APK assets. */
class ContentPackNotFoundException(
    packId: String,
    cause: Throwable? = null
) : ContentPackException("Content pack '$packId' has no readable manifest", cause)

/** The manifest is not valid JSON or is not a JSON object. */
class ContentPackManifestException(
    packId: String,
    reason: String,
    cause: Throwable? = null
) : ContentPackException("Content pack '$packId' manifest is invalid: $reason", cause)

/** A required field is missing, malformed, or inconsistent with the pack. */
class ContentPackValidationException(
    packId: String,
    reason: String
) : ContentPackException("Content pack '$packId' is invalid: $reason")

/** The pack's declared expiry date has passed. */
class ContentPackExpiredException(
    packId: String,
    expiry: LocalDate,
    today: LocalDate
) : ContentPackException("Content pack '$packId' expired on $expiry (today: $today)")

/** A declared payload file is missing or does not match its manifest digest. */
class ContentPackChecksumException(
    packId: String,
    path: String,
    expected: String,
    actual: String?,
    cause: Throwable? = null
) : ContentPackException(
    if (actual == null) {
        "Content pack '$packId' is missing '$path'"
    } else {
        "Content pack '$packId' checksum mismatch for '$path' " +
            "(expected $expected, got $actual)"
    },
    cause
)
