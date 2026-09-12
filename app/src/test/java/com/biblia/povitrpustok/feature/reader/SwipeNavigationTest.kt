package com.biblia.povitrpustok.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Swipe chapter navigation decision (corrective phase Issue 7): only
 * predominantly horizontal drags past the threshold navigate; vertical
 * scrolling and diagonal gestures are never chapter changes. Navigation
 * targets are position-based in the ViewModel (Job-safe).
 */
class SwipeNavigationTest {

    private val threshold = 96f

    @Test
    fun swipe_left_navigates_next() {
        assertEquals(SwipeDirection.NEXT, swipeDirection(-200f, 10f, threshold))
    }

    @Test
    fun swipe_right_navigates_previous() {
        assertEquals(SwipeDirection.PREVIOUS, swipeDirection(200f, -10f, threshold))
    }

    @Test
    fun below_threshold_does_not_navigate() {
        assertEquals(SwipeDirection.NONE, swipeDirection(-50f, 5f, threshold))
        assertEquals(SwipeDirection.NONE, swipeDirection(50f, 5f, threshold))
    }

    @Test
    fun vertical_drag_never_navigates() {
        assertEquals(SwipeDirection.NONE, swipeDirection(-120f, 400f, threshold))
        assertEquals(SwipeDirection.NONE, swipeDirection(120f, -400f, threshold))
    }

    @Test
    fun diagonal_drag_does_not_navigate() {
        // vertical-dominant diagonals are treated as scrolls
        assertEquals(SwipeDirection.NONE, swipeDirection(-110f, 115f, threshold))
        assertEquals(SwipeDirection.NONE, swipeDirection(110f, -115f, threshold))
    }

    @Test
    fun tiny_horizontal_wobble_inside_a_scroll_does_not_navigate() {
        assertEquals(SwipeDirection.NONE, swipeDirection(30f, 800f, threshold))
    }
}
