package com.example.campbnb.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Approximated from the Figma screenshot. Replace with exact values.
val GreenTop = Color(0xFF4A9A34)
val GreenMid = Color(0xFF88BB5E)
val LimeBottom = Color(0xFFC6DC52)
val ButtonStart = Color(0xFF75B04F)
val ButtonEnd = Color(0xFF2F7526)
val FieldGrey = Color(0xFFD9D9D9)
val LinkBlue = Color(0xFF2E9BDA)

/** Start, Login and Sign Up backgrounds: dark green fading to light green. */
val ScreenGradient = Brush.verticalGradient(listOf(GreenTop, GreenMid))

/** Role select background: dark green through light green to lime. */
val RoleGradient = Brush.verticalGradient(0f to GreenTop, 0.5f to GreenMid, 1f to LimeBottom)

/** Pill buttons: light green on the left to dark green on the right. */
val ButtonGradient = Brush.horizontalGradient(listOf(ButtonStart, ButtonEnd))

val SearchBarBg = Color(0xFFECE6F0)   // pale lavender search pill