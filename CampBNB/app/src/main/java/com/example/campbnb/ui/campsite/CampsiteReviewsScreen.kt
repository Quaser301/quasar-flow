package com.example.campbnb.ui.campsite

/*
 * CampsiteReviewsScreen.kt
 *
 * Role: view. The reviews screen a campsite's Details page switches to when "See all reviews" is
 * tapped (US 01.13.02). Every rating is shown as a card with the reviewer's name, their stars and
 * their written review. An admin can select a card to get a Delete button (US 03.09.01, 03.10.01).
 *
 * Data in, callbacks out: it holds no model or controller, so the screen that uses it decides
 * where the ratings come from and what deleting does.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.models.Rating
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.ui.admin.ConfirmRemovalDialog
import com.example.campbnb.ui.components.GradientButton
import com.example.campbnb.ui.components.StarGlyph
import com.example.campbnb.ui.theme.ButtonGradient
import com.example.campbnb.ui.theme.DeleteGradient
import com.example.campbnb.ui.theme.DeleteStart
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.ui.theme.StarTeal
import androidx.compose.material3.Surface

// prompted Claude Sonnet 5.5 for some implementations related to the star ratings

/**
 * Scrollable list of the ratings and reviews one campsite has received.
 *
 * Everyone can scroll through the cards. When [isAdmin] is true, tapping a card selects it
 * (tap it again to unselect) and a red Delete button appears; the button opens a pop-up where
 * the admin picks a reason before [onDeleteReview] is called.
 *
 * @param campsiteName Shown under the title.
 * @param reviews The ratings to show, with the review text and name of each reviewer.
 * @param average The campsite's average stars, shown under the title.
 * @param isAdmin True if the viewer is an admin and may select cards to delete them.
 * @param onBack Called when the back circle is tapped.
 * @param onDeleteReview Called with the selected rating and the reason the admin picked.
 */
@Composable
fun CampsiteReviewsScreen(
    campsiteName: String,
    reviews: List<Rating>,
    average: Double,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onDeleteReview: (Rating, RemovalReason?) -> Unit
) {
    var selectedUserId by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    val selected = findSelected(reviews, selectedUserId)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            // Back button and title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ButtonGradient)
                        .testTag("reviews_back")
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { Text("<", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Column {
                    Text("Reviews", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(campsiteName, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            RatingDisplay(average, reviews.size)
            Spacer(Modifier.height(12.dp))

            if (reviews.isEmpty()) {
                Text("No reviews yet", fontSize = 14.sp, modifier = Modifier.testTag("no_reviews"))
            }

            // Bottom padding leaves room for the Delete button so it never covers the last card.
            LazyColumn(
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reviews, key = { review -> review.id }) { review ->
                    ReviewCard(
                        review = review,
                        isSelected = review.userId == selectedUserId,
                        isSelectable = isAdmin,
                        onClick = {
                            if (review.userId == selectedUserId) {
                                selectedUserId = ""
                            } else {
                                selectedUserId = review.userId
                            }
                        }
                    )
                }
            }
        }

        // The Delete button has its own strip under the list, so it never covers a card.
        if (isAdmin && selected != null) {
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    GradientButton(
                        text = "Delete review",
                        onClick = { confirmDelete = true },
                        brush = DeleteGradient,
                        height = 52.dp,
                        modifier = Modifier.testTag("delete_review")
                    )
                }
            }
        }
    }

    if (confirmDelete && selected != null) {
        val target: Rating = selected
        ConfirmRemovalDialog(
            title = "Delete this rating and review?",
            message = nameShownFor(target) + "'s rating and review will be permanently deleted and " +
                    "they will be told why. This cannot be undone.",
            confirmLabel = "Delete",
            reasonRequired = true,
            onConfirm = { reason ->
                confirmDelete = false
                selectedUserId = ""
                onDeleteReview(target, reason)
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** The rating whose reviewer is [userId], or null if nothing is selected (or it has since disappeared). */
private fun findSelected(reviews: List<Rating>, userId: String): Rating? {
    for (review in reviews) {
        if (review.userId == userId) {
            return review
        }
    }
    return null
}

/** The name to show for a reviewer; ratings saved without a name are shown as anonymous. */
private fun nameShownFor(review: Rating): String {
    if (review.userName.isBlank()) {
        return "Anonymous camper"
    }
    return review.userName
}

/** One rating-and-review card: reviewer name, stars, and the review text if they wrote one. */
@Composable
private fun ReviewCard(
    review: Rating,
    isSelected: Boolean,
    isSelectable: Boolean,
    onClick: () -> Unit
) {
    var borderColor = Color.Transparent
    if (isSelected) {
        borderColor = DeleteStart
    }

    Column(
        Modifier
            .fillMaxWidth()
            .testTag("review_card_" + review.userId)
            .clip(RoundedCornerShape(8.dp))
            .background(FieldGrey.copy(alpha = 0.35f))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = isSelectable, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(nameShownFor(review), fontSize = 16.sp, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (position in 1..5) {
                var circleColor = FieldGrey
                if (position <= review.stars) {
                    circleColor = StarTeal
                }
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(circleColor),
                    contentAlignment = Alignment.Center
                ) { StarGlyph(Modifier.size(13.dp)) }
            }
        }

        if (review.review.isBlank()) {
            Text("No written review", fontSize = 13.sp, color = Color.Gray)
        } else {
            Text(review.review, fontSize = 13.sp)
        }
    }
}