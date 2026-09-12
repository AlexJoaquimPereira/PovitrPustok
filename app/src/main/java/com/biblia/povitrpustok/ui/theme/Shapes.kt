package com.biblia.povitrpustok.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Minimal, low-noise shapes (plan §41). The reader surface itself uses
 * no elevation or card treatment; shapes apply only where controls need
 * them.
 */
val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp)
)
