package com.biblia.povitrpustok.feature.books

/**
 * Why a book introduction failed to load (F-24).
 *
 * The ViewModel must not carry user-visible English; the screen maps this to
 * a localized resource, so a future `values-kok` only changes resources.
 */
enum class IntroductionError {
    BOOK_NOT_FOUND,
    UNKNOWN
}
