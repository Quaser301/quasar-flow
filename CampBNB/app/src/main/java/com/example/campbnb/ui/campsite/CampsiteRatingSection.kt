// ===== ui/campsite/CampsiteRatingSection.kt =====
package com.example.campbnb.ui.campsite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.ui.shared.observeModel

// Todo: nothing stops a user rating a campsite they have never visited.

/**
 * View: the rating block for the Details screen (US 01.13.01, US 01.01.04). Shows the
 * campsite's average and the user's own rating; tapping a star saves a new rating.
 * It loads the ratings and redraws itself, so the Details screen only has to place it.
 */
@Composable
fun CampsiteRatingSection(
    model: CampsiteModel,
    controller: CampsiteController,
    campsiteId: String,
    userId: String,
    modifier: Modifier = Modifier
) {
    observeModel(model)
    LaunchedEffect(campsiteId) {
        controller.loadRatings()
    }

    Column(modifier = modifier) {
        RatingDisplay(
            average = model.averageRating(campsiteId),
            count = model.ratingCount(campsiteId)
        )
        Text("Your rating", fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        RatingStars(
            currentStars = model.myRating(campsiteId, userId),
            onRate = { stars -> controller.saveRating(campsiteId, userId, stars) }
        )
        if (model.statusMessage.isNotBlank()) {
            Text(
                text = model.statusMessage,
                fontSize = 14.sp,
                modifier = Modifier.testTag("rating_message").padding(top = 4.dp)
            )
        }
    }
}