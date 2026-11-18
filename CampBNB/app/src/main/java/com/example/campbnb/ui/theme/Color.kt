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
val StarTeal = Color(0xFF5BC0DE)
val EyePink = Color(0xFFF0A8A8)
val DeleteStart = Color(0xFFA64545)
val DeleteEnd = Color(0xFF6B1212)

val ErrorRed = Color(0xFFFC3A6B)

// For headers/system info (IE "Fetching logs...", that sort of thing)
val AdminInfoColor = Color(0xFF424A52)
val AdminInteractionColor = Color(0xFF3C4B28)
// for the actual info pulled from the system
val AdminDataColor1 = Color(0xFF002147)
val AdminDataColor2 = Color(0xFFD4AF37)

val AdminDataColor3 = Color(0xFF7A560F)


/** Red pill button for destructive actions like Delete Site. */
val DeleteGradient = Brush.horizontalGradient(listOf(DeleteStart, DeleteEnd))

/** Start, Login and Sign Up backgrounds: dark green fading to light green. */
val ScreenGradient = Brush.verticalGradient(listOf(GreenTop, GreenMid))

/** Role select background: dark green through light green to lime. */
val RoleGradient = Brush.verticalGradient(0f to GreenTop, 0.5f to GreenMid, 1f to LimeBottom)

/** Pill buttons: light green on the left to dark green on the right. */
val ButtonGradient = Brush.horizontalGradient(listOf(ButtonStart, ButtonEnd))

val SearchBarBg = Color(0xFFECE6F0)   // pale lavender search pill