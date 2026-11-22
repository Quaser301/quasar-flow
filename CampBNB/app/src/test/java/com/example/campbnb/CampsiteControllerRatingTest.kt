package com.example.campbnb

import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.Rating
import com.example.campbnb.ui.campsite.ratingSummary
import com.example.campbnb.ui.campsite.starsFor
import org.junit.Assert.assertEquals
import org.junit.Test

/** A repository that keeps ratings in memory and never touches Firebase. */
class FakeRatingRepository : CampsiteRepository() {
    val saved = mutableMapOf<String, Rating>()
    var failSaves = false

    override fun getAll(onSuccess: (List<Campsite>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(emptyList())
    }

    override fun getAllRatings(onSuccess: (List<Rating>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(saved.values.toList())
    }

    override fun saveRating(rating: Rating, onComplete: (Result<Exception?>) -> Unit) {
        if (failSaves) {
            onComplete(Result.failure(Exception("offline")))
        } else {
            saved[rating.id] = rating
            onComplete(Result.success(null))
        }
    }
}

class CampsiteControllerRatingTest {

    @Test
    fun saveRating_withValidStars_savesAndUpdatesModel() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        CampsiteController(model, repo).saveRating("c1", "u1", 4)
        assertEquals(1, repo.saved.size)
        assertEquals(4, model.myRating("c1", "u1"))
        assertEquals("Thanks for rating!", model.statusMessage)
    }

    @Test
    fun saveRating_again_replacesTheOldRating() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        val controller = CampsiteController(model, repo)
        controller.saveRating("c1", "u1", 5)
        controller.saveRating("c1", "u1", 2)
        assertEquals(1, repo.saved.size)
        assertEquals(1, model.ratingCount("c1"))
        assertEquals(2, model.myRating("c1", "u1"))
        assertEquals(2.0, model.averageRating("c1"), 0.001)
    }

    @Test
    fun saveRating_fromTwoUsers_averagesBoth() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        val controller = CampsiteController(model, repo)
        controller.saveRating("c1", "u1", 5)
        controller.saveRating("c1", "u2", 4)
        assertEquals(4.5, model.averageRating("c1"), 0.001)
        assertEquals(2, model.ratingCount("c1"))
    }

    @Test
    fun saveRating_withInvalidStars_isRejected() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        val controller = CampsiteController(model, repo)
        controller.saveRating("c1", "u1", 0)
        controller.saveRating("c1", "u1", 6)
        assertEquals(0, repo.saved.size)
        assertEquals(0, model.ratingCount("c1"))
        assertEquals("Rating must be between 1 and 5 stars", model.statusMessage)
    }

    @Test
    fun saveRating_withNoUser_isRejected() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        CampsiteController(model, repo).saveRating("c1", "", 4)
        assertEquals(0, repo.saved.size)
        assertEquals("Please sign in before rating", model.statusMessage)
    }

    @Test
    fun saveRating_whenTheSaveFails_leavesTheModelAlone() {
        val repo = FakeRatingRepository()
        repo.failSaves = true
        val model = CampsiteModel()
        CampsiteController(model, repo).saveRating("c1", "u1", 4)
        assertEquals(0, model.ratingCount("c1"))
        assertEquals("Could not save your rating", model.statusMessage)
    }

    @Test
    fun loadRatings_fillsTheModelFromTheRepository() {
        val repo = FakeRatingRepository()
        val rating = Rating("c1", "u1", 5)
        repo.saved[rating.id] = rating
        val model = CampsiteModel()
        CampsiteController(model, repo).loadRatings()
        assertEquals(1, model.ratingCount("c1"))
        assertEquals(5.0, model.averageRating("c1"), 0.001)
    }

    @Test
    fun unrated_campsite_reads_zero_everywhere() {
        val model = CampsiteModel()
        assertEquals(0.0, model.averageRating("never-rated"), 0.001)
        assertEquals(0, model.ratingCount("never-rated"))
        assertEquals(0, model.myRating("never-rated", "u1"))
    }

    @Test
    fun saveRating_at_the_one_and_five_boundaries_is_valid() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        val controller = CampsiteController(model, repo)
        controller.saveRating("c1", "u-one", 1)
        controller.saveRating("c1", "u-five", 5)
        assertEquals(2, repo.saved.size)
        assertEquals(1, model.myRating("c1", "u-one"))
        assertEquals(5, model.myRating("c1", "u-five"))
    }

    // The rating DISPLAY helpers are pure (ui.campsite): the star count uses Java's
    // Math.round semantics (-0.5 rounds toward zero) and clamps to 0..5, and the
    // summary copy is exact with the US decimal separator.
    @Test
    fun starsFor_rounds_and_clamps_like_java() {
        assertEquals(0, starsFor(-0.5))
        assertEquals(0, starsFor(-1.0))
        assertEquals(4, starsFor(4.4))
        assertEquals(5, starsFor(4.5))
        assertEquals(5, starsFor(5.5))
    }

    @Test
    fun ratingSummary_uses_the_exact_copies() {
        assertEquals("No ratings yet", ratingSummary(0.0, 0))
        assertEquals("4.0 (1 rating)", ratingSummary(4.0, 1))
        assertEquals("2.0 (3 ratings)", ratingSummary(2.0, 3))
    }
}