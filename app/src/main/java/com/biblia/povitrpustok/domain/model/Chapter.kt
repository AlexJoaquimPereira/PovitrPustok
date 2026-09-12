package com.biblia.povitrpustok.domain.model

/**
 * A chapter record inside one book database (implementation plan §13).
 *
 * Invariants from the plan — never assume bookId + number is unique:
 *  - [id] is the internal identity (chapters.id).
 *  - [number] is the source/display chapter number (may repeat within a
 *    book, e.g. Job).
 *  - [position] is the source-order position used for sequential
 *    previous/next navigation (unique within a book).
 */
data class Chapter(
    val id: Long,
    val bookId: String,
    val number: Int,
    val position: Int
)
