/*
 * 25-11-26: Campy created with love but also Claude Sonnet 5.5
 *
 * Purpose: Reusable Jetpack Compose component that draws and animates "Campy",
 * the app's dancing tent mascot. Intended for the welcome/login screen but can be
 * used anywhere (empty states, loading screens, etc.).
 *
 * Outstanding issues: none known. Drawing is done with a Canvas, so no image assets are needed.
 */
package com.example.campsite.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

private val TentOrange = Color(0xFFF28C28)
private val TentOutline = Color(0xFFB85C00)
private val LimbBrown = Color(0xFF5D3A1A)
private val FlagGreen = Color(0xFF2E7D32)
private val MouthRed = Color(0xFF7A1F1F)
private val Cheek = Color(0x66FF6F91)

/**
 * Campy, the dancing tent mascot.
 *
 * @param modifier standard Compose modifier
 * @param size width and height of the mascot
 * @param animate set to false for a still pose (e.g. when the user has reduced motion enabled)
 */
@Composable
fun CampyMascot(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    animate: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "campy")
    val beatRaw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beat"
    )
    val beat = if (animate) beatRaw else 0f

    val phase = beat * 2f * PI.toFloat()
    val bounce = abs(sin(phase))          // 0..1, two hops per cycle
    val sway = sin(phase)                 // -1..1, left/right lean
    val blinking = animate && beat > 0.90f && beat < 0.95f

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Campy the dancing tent mascot" }
    ) {
        // Everything below is drawn in a 200 x 200 design space.
        scale(scale = this.size.width / 200f, pivot = Offset.Zero) {

            // Ground shadow (shrinks while Campy is in the air)
            val shadowWidth = 70f - bounce * 18f
            drawOval(
                color = Color(0x33000000),
                topLeft = Offset(100f - shadowWidth / 2f, 180f),
                size = Size(shadowWidth, 10f)
            )

            translate(top = -bounce * 12f) {
                rotate(degrees = sway * 6f, pivot = Offset(100f, 170f)) {

                    val limb = Stroke(width = 7f, cap = StrokeCap.Round)

                    // Legs: alternate lifting each foot
                    val leftFootY = 178f - max(0f, sin(phase)) * 10f
                    val rightFootY = 178f - max(0f, -sin(phase)) * 10f
                    drawLine(LimbBrown, Offset(85f, 150f), Offset(80f, leftFootY), limb.width, StrokeCap.Round)
                    drawLine(LimbBrown, Offset(115f, 150f), Offset(120f, rightFootY), limb.width, StrokeCap.Round)
                    drawOval(LimbBrown, Offset(70f, leftFootY - 3f), Size(20f, 9f))
                    drawOval(LimbBrown, Offset(110f, rightFootY - 3f), Size(20f, 9f))

                    // Arms: wave up and down in opposite directions
                    val leftWave = 0.5f + 0.5f * sin(phase)
                    val rightWave = 0.5f + 0.5f * sin(phase + PI.toFloat())
                    val leftHand = Offset(36f, 115f - 38f * leftWave)
                    val rightHand = Offset(164f, 115f - 38f * rightWave)
                    drawLine(LimbBrown, Offset(62f, 118f), leftHand, limb.width, StrokeCap.Round)
                    drawLine(LimbBrown, Offset(138f, 118f), rightHand, limb.width, StrokeCap.Round)
                    drawCircle(LimbBrown, radius = 6f, center = leftHand)
                    drawCircle(LimbBrown, radius = 6f, center = rightHand)

                    // Flag pole and fluttering flag
                    drawLine(LimbBrown, Offset(100f, 30f), Offset(100f, 8f), 3f, StrokeCap.Round)
                    val flag = Path().apply {
                        moveTo(100f, 8f)
                        lineTo(100f, 22f)
                        lineTo(124f + sin(phase * 2f) * 4f, 15f + sin(phase * 2f + 1f) * 2f)
                        close()
                    }
                    drawPath(flag, FlagGreen)

                    // Tent body
                    val tent = Path().apply {
                        moveTo(100f, 28f)
                        lineTo(160f, 156f)
                        lineTo(40f, 156f)
                        close()
                    }
                    drawPath(tent, TentOrange)
                    drawPath(
                        tent,
                        TentOutline,
                        style = Stroke(width = 5f, join = StrokeJoin.Round)
                    )

                    // Eyes
                    if (blinking) {
                        drawLine(Color(0xFF222222), Offset(79f, 112f), Offset(93f, 112f), 3f, StrokeCap.Round)
                        drawLine(Color(0xFF222222), Offset(107f, 112f), Offset(121f, 112f), 3f, StrokeCap.Round)
                    } else {
                        drawCircle(Color.White, radius = 8f, center = Offset(86f, 112f))
                        drawCircle(Color.White, radius = 8f, center = Offset(114f, 112f))
                        // Pupils glance in the direction Campy is leaning
                        val look = sway * 2f
                        drawCircle(Color(0xFF222222), radius = 4f, center = Offset(86f + look, 113f))
                        drawCircle(Color(0xFF222222), radius = 4f, center = Offset(114f + look, 113f))
                    }

                    // Cheeks and open smile
                    drawCircle(Cheek, radius = 6f, center = Offset(74f, 128f))
                    drawCircle(Cheek, radius = 6f, center = Offset(126f, 128f))
                    drawArc(
                        color = MouthRed,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(89f, 121f),
                        size = Size(22f, 18f)
                    )
                }
            }
        }
    }
}