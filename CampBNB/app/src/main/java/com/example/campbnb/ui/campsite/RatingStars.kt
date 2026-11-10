package com.example.campbnb.ui.campsite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Code for star rating functionality obtained by prompting Claude Sonnet 5.5

// Todo: stars are text characters, not icons, and have no screen-reader labels.

/**
 * Star rating widgets for campsites (US 01.01.04 shows the average, US 01.13.01 lets an
 * entrant rate). screens pass in numbers from CampsiteModel and react to taps by calling
 * CampsiteController.saveRating.
 */

private val StarGold = Color(0xFFFFC107)
private val StarGrey = Color(0xFFBDBDBD)
private const val STAR = "\u2605"   // the star character

/** How many stars to fill for an average: rounded to the nearest whole star, from 0 to 5. */
fun starsFor(average: Double): Int {
    val rounded = Math.round(average).toInt()
    if (rounded < 0) {
        return 0
    }
    if (rounded > 5) {
        return 5
    }
    return rounded
}

/** The text shown beside the stars, such as "4.5 (2 ratings)" or "No ratings yet". */
fun ratingSummary(average: Double, count: Int): String {
    if (count == 0) {
        return "No ratings yet"
    }
    val number = String.format(Locale.US, "%.1f", average)
    if (count == 1) {
        return number + " (1 rating)"
    }
    return number + " (" + count + " ratings)"
}

/** Read-only stars showing a campsite's average rating and how many people rated it. */
@Composable
fun RatingDisplay(average: Double, count: Int, modifier: Modifier = Modifier) {
    val filled = starsFor(average)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        for (position in 1..5) {
            var color = StarGrey
            if (position <= filled) {
                color = StarGold
            }
            Text(text = STAR, color = color, fontSize = 18.sp)
        }
        Text(
            text = ratingSummary(average, count),
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * Five tappable stars. [currentStars] is the user's saved rating (0 if none); tapping star
 * number N calls [onRate] with N.
 */
@Composable
fun RatingStars(currentStars: Int, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        for (position in 1..5) {
            var color = StarGrey
            if (position <= currentStars) {
                color = StarGold
            }
            Text(
                text = STAR,
                color = color,
                fontSize = 36.sp,
                modifier = Modifier
                    .testTag("star_" + position)
                    .clickable { onRate(position) }
                    .padding(4.dp)
            )
        }
    }
}