package com.cady.cadysalesapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Rounder than the Material baseline on purpose: text fields and menus 10dp, chips 14dp, cards 18-22dp,
 * dialogs and sheets 28dp. One consistent family instead of the baseline's mix of square-ish and pill.
 */
val CadyShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
