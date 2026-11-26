// ===== ui/campsite/CampsiteRatingSection.kt =====
package com.example.campbnb.ui.campsite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.ui.components.GradientButton
import com.example.campbnb.ui.shared.observeModel

// Todo: nothing stops a user rating a campsite they have never visited.

/**
 * View: the "Your rating" and "Your review" block for the Details screen (US 01.13.01,
 * US 01.13.02). Tapping a star saves a rating; writing text and pressing "Post review" attaches
 * a review to that rating. The campsite's average is shown in the Details header, which reads
 * the same CampsiteModel, so it updates when the user rates.
 *
 * @param userName The name shown beside the user's rating and review on the reviews screen.
 */
@Composable
fun CampsiteRatingSection(
    model: CampsiteModel,
    controller: CampsiteController,
    campsiteId: String,
    userId: String,
    userName: String = "",
    modifier: Modifier = Modifier
) {
    observeModel(model)
    LaunchedEffect(campsiteId) {
        controller.loadRatings()
    }

    // The review the user already posted, so the box starts with it and they can edit it.
    var savedReview = ""
    val myRating = model.findRating(campsiteId, userId)
    if (myRating != null) {
        savedReview = myRating.review
    }
    var reviewText by remember(savedReview) { mutableStateOf(savedReview) }

    Column(modifier = modifier) {
        Text("Rate this campsite", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        RatingStars(
            currentStars = model.myRating(campsiteId, userId),
            onRate = { stars -> controller.saveRating(campsiteId, userId, stars, userName) }
        )
        OutlinedTextField(
            value = reviewText,
            onValueChange = { newText -> reviewText = newText },
            label = { Text("Write a review") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("review_input")
        )
        GradientButton(
            text = "Post review",
            onClick = { controller.saveReview(campsiteId, userId, userName, reviewText) },
            modifier = Modifier.padding(top = 8.dp).testTag("post_review")
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