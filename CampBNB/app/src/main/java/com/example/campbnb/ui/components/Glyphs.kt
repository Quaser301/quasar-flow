package com.example.campbnb.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Three horizontal lines (hamburger menu) */
@Composable
fun MenuGlyph(modifier: Modifier = Modifier, color: Color = Color.DarkGray) {
    Canvas(modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        listOf(stroke / 2, size.height / 2, size.height - stroke / 2).forEach { y ->
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        }
    }
}

/** Magnifying glass: a circle with a handle. */
@Composable
fun SearchGlyph(modifier: Modifier = Modifier, color: Color = Color.DarkGray) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val radius = size.minDimension * 0.35f
        val center = Offset(radius + stroke, radius + stroke)
        drawCircle(color, radius, center, style = Stroke(stroke))
        drawLine(
            color,
            start = Offset(center.x + radius * 0.71f, center.y + radius * 0.71f),
            end = Offset(size.width - stroke, size.height - stroke),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
