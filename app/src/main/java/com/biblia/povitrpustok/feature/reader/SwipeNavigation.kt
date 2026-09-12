package com.biblia.povitrpustok.feature.reader

import kotlin.math.abs

/**
 * Chapter-navigation decision from an accumulated horizontal drag
 * (corrective phase Issue 7).
 *
 * A swipe changes chapter only when the gesture is predominantly
 * horizontal AND exceeds the threshold — vertical scrolling and diagonal
 * drags must never navigate. Navigation itself stays position-based in
 * the ViewModel (`next()`/`previous()`), never chapter.number arithmetic.
 */
enum class SwipeDirection {
    /** Swipe left -> next chapter. */
    NEXT,

    /** Swipe right -> previous chapter. */
    PREVIOUS,

    /** Vertical/diagonal/below-threshold — no navigation. */
    NONE
}

fun swipeDirection(totalDx: Float, totalDy: Float, threshold: Float): SwipeDirection =
    when {
        abs(totalDx) <= threshold -> SwipeDirection.NONE
        abs(totalDy) > abs(totalDx) -> SwipeDirection.NONE
        totalDx < 0f -> SwipeDirection.NEXT
        else -> SwipeDirection.PREVIOUS
    }
