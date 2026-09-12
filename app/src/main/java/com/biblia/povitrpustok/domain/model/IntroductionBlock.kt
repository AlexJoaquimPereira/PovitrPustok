package com.biblia.povitrpustok.domain.model

/**
 * One source introduction block of a book (implementation plan §13).
 *
 * Books differ in introduction structure; each source block is preserved
 * independently (position, type, html) and never flattened into one
 * string.
 */
data class IntroductionBlock(
    val id: Long,
    val bookId: String,
    val position: Int,
    val type: String,
    val html: String
)
