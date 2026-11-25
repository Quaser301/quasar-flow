package com.example.campbnb

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.Rating
import com.example.campbnb.ui.campsite.CampsiteRatingSection
import org.junit.Assert.assertEquals
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
    fun tappingAStar_savesTheRating() {
        val model = CampsiteModel()
        val repo = FakeRatingsForUi()
        val controller = CampsiteController(model, repo)
        composeRule.setContent {
            CampsiteRatingSection(model, controller, "c1", "u1", "Sam")
        }
        composeRule.onNodeWithTag("star_4").performClick()
        composeRule.waitForIdle()
        assertEquals(4, repo.saved[Rating.makeId("c1", "u1")]?.stars)
    }

    @Test
    fun ratingAgain_replacesTheOldRating() {
        val model = CampsiteModel()
        val repo = FakeRatingsForUi()
        val controller = CampsiteController(model, repo)
        composeRule.setContent {
            CampsiteRatingSection(model, controller, "c1", "u1", "Sam")
        }
        composeRule.onNodeWithTag("star_5").performClick()
        composeRule.onNodeWithTag("star_2").performClick()
        composeRule.waitForIdle()
        assertEquals(1, repo.saved.size)
        assertEquals(2, repo.saved[Rating.makeId("c1", "u1")]?.stars)
    }

    @Test
    fun postingAReview_afterRating_savesItWithTheName() {
        val model = CampsiteModel()
        val repo = FakeRatingsForUi()
        val controller = CampsiteController(model, repo)
        composeRule.setContent {
            CampsiteRatingSection(model, controller, "c1", "u1", "Sam")
        }
        composeRule.onNodeWithTag("star_5").performClick()
        composeRule.onNodeWithTag("review_input").performTextInput("Loved it")
        composeRule.onNodeWithTag("post_review").performClick()
        composeRule.waitForIdle()
        val saved = repo.saved[Rating.makeId("c1", "u1")]
        assertEquals("Loved it", saved?.review)
        assertEquals("Sam", saved?.userName)
        composeRule.onNodeWithText("Review posted!").assertIsDisplayed()
    }

    @Test
    fun postingAReview_beforeRating_isRefused() {
        val model = CampsiteModel()
        val repo = FakeRatingsForUi()
        val controller = CampsiteController(model, repo)
        composeRule.setContent {
            CampsiteRatingSection(model, controller, "c1", "u1", "Sam")
        }
        composeRule.onNodeWithTag("review_input").performTextInput("Nice")
        composeRule.onNodeWithTag("post_review").performClick()
        composeRule.waitForIdle()
        assertEquals(0, repo.saved.size)
        composeRule.onNodeWithText("Tap a star rating first, then post your review").assertIsDisplayed()
    }
}