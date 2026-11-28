package com.example.campbnb.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Five-point star. */
@Composable
fun StarGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val outer = size.minDimension / 2f
        val inner = outer * 0.45f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = Math.PI / 5 * i - Math.PI / 2
            val x = size.width / 2f + (r * cos(angle)).toFloat()
            val y = size.height / 2f + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}

/** Eye outline with a pupil, for "view all tags". */
@Composable
fun EyeGlyph(modifier: Modifier = Modifier, color: Color = Color.DarkGray) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        drawOval(
            color,
            topLeft = Offset(stroke / 2, size.height * 0.2f),
            size = Size(size.width - stroke, size.height * 0.6f),
            style = Stroke(stroke)
        )
        drawCircle(color, radius = size.height * 0.15f, center = center)
    }
}

/** Small downward triangle for dropdown pills. */
@Composable
fun ArrowDownGlyph(modifier: Modifier = Modifier, color: Color = Color.DarkGray) {
    Canvas(modifier.size(width = 14.dp, height = 8.dp)) {
        val triangle = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        drawPath(triangle, color)
    }
}
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

@Composable
fun RepeatableArrowButton(
    arrow: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val currentOnClick = rememberUpdatedState(onClick)

    Box(
        modifier = Modifier
            .size(48.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            // Change the value immediately on press.
                            currentOnClick.value()

                            // Continue changing while held.
                            coroutineScope {
                                val repeatJob = launch {
                                    delay(400)

                                    while (isActive) {
                                        currentOnClick.value()
                                        delay(100)
                                    }
                                }

                                tryAwaitRelease()
                                repeatJob.cancel()
                            }
                        }
                    )
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = description

                onClick {
                    if (enabled) {
                        currentOnClick.value()
                        true
                    } else {
                        false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = arrow,
            style = MaterialTheme.typography.headlineMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )
    }
}
