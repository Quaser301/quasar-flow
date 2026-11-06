package com.example.campbnb

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.Rating
import com.example.campbnb.ui.campsite.CampsiteRatingSection
import org.junit.Rule
import org.junit.Test

/** In-memory repository for the UI test; never touches Firebase. */
class FakeRatingsForUi : CampsiteRepository() {
    val saved = mutableMapOf<String, Rating>()

    override fun getAll(onSuccess: (List<Campsite>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(emptyList())
    }

    override fun getAllRatings(onSuccess: (List<Rating>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(saved.values.toList())
    }

    override fun saveRating(rating: Rating, onComplete: (Result<Exception?>) -> Unit) {
        saved[rating.id] = rating
        onComplete(Result.success(null))
    }
}

class CampsiteRatingSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingAStar_savesTheRatingAndShowsTheNewAverage() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeRatingsForUi())
        composeRule.setContent {
            CampsiteRatingSection(model, controller, "c1", "u1")
        }
        composeRule.onNodeWithText("No ratings yet").assertIsDisplayed()
        composeRule.onNodeWithTag("star_4").performClick()
        composeRule.onNodeWithText("4.0 (1 rating)").assertIsDisplayed()
    }

    @Test
    fun ratingAgain_replacesTheOldRating() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeRatingsForUi())
        composeRule.setContent {
            CampsiteRatingSection(model, controller, "c1", "u1")
        }
        composeRule.onNodeWithTag("star_5").performClick()
        composeRule.onNodeWithTag("star_2").performClick()
        composeRule.onNodeWithText("2.0 (1 rating)").assertIsDisplayed()
    }
}