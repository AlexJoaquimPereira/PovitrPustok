package com.biblia.povitrpustok.feature.reader

/**
 * Why a chapter failed to load (F-24).
 *
 * The ViewModel must not carry user-visible English; the screen maps this to
 * a localized resource, so a future `values-kok` only changes resources.
 */
enum class ReaderLoadError {
    BOOK_NOT_FOUND,
    CHAPTER_NOT_FOUND,
    UNKNOWN
}
