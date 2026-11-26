package com.example.campbnb

import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.models.CampsiteModel
import org.junit.Assert.assertEquals
import org.junit.Test

// tests created with the help of Claude Sonnet 5.5

/** Unit tests for posting a review attached to a rating (US 01.13.02). Reuses FakeRatingRepository. */
class CampsiteControllerReviewTest {

    @Test
    fun saveReview_afterRating_attachesTheTextAndNameToTheRating() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        val controller = CampsiteController(model, repo)
        controller.saveRating("c1", "u1", 4, "Casey")

        controller.saveReview("c1", "u1", "Casey", "  Lovely spot  ")

        assertEquals("still one rating", 1, repo.saved.size)
        val saved = model.findRating("c1", "u1")!!
        assertEquals(4, saved.stars)
        assertEquals("Lovely spot", saved.review)
        assertEquals("Casey", saved.userName)
        assertEquals("Review posted!", model.statusMessage)
    }

    @Test
    fun saveReview_beforeRating_isRefused() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()

        CampsiteController(model, repo).saveReview("c1", "u1", "Casey", "Lovely spot")

        assertEquals(0, repo.saved.size)
        assertEquals("Tap a star rating first, then post your review", model.statusMessage)
    }

    @Test
    fun saveReview_withBlankText_isRefused() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        val controller = CampsiteController(model, repo)
        controller.saveRating("c1", "u1", 3, "Casey")

        controller.saveReview("c1", "u1", "Casey", "   ")

        assertEquals("", model.findRating("c1", "u1")!!.review)
        assertEquals("Please write a review before posting", model.statusMessage)
    }

    @Test
    fun saveReview_overTheLimit_isRefused() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        val controller = CampsiteController(model, repo)
        controller.saveRating("c1", "u1", 3, "Casey")

        controller.saveReview("c1", "u1", "Casey", "x".repeat(CampsiteController.MAX_REVIEW_LENGTH + 1))

        assertEquals("", model.findRating("c1", "u1")!!.review)
    }

    @Test
    fun ratingAgain_keepsTheReviewAndTheName() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        val controller = CampsiteController(model, repo)
        controller.saveRating("c1", "u1", 5, "Casey")
        controller.saveReview("c1", "u1", "Casey", "Lovely spot")

        controller.saveRating("c1", "u1", 2)

        val saved = model.findRating("c1", "u1")!!
        assertEquals(2, saved.stars)
        assertEquals("Lovely spot", saved.review)
        assertEquals("Casey", saved.userName)
        assertEquals(1, repo.saved.size)
    }

    @Test
    fun ratingsFor_onlyReturnsThatCampsitesRatings() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        val controller = CampsiteController(model, repo)
        controller.saveRating("c1", "u1", 5, "Casey")
        controller.saveRating("c2", "u1", 3, "Casey")

        assertEquals(1, model.ratingsFor("c1").size)
        assertEquals("c1", model.ratingsFor("c1")[0].campsiteId)
    }
}