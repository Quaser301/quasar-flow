package com.example.campbnb

import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.Rating
import org.junit.Assert.assertEquals
import org.junit.Test

/** A repository that keeps ratings in memory and never touches Firebase. */
class FakeRatingRepository : CampsiteRepository() {
    val saved = mutableMapOf<String, Rating>()
    var failSaves = false
    var failLoads = false

    override fun getAll(onSuccess: (List<Campsite>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(emptyList())
    }

    override fun getAllRatings(onSuccess: (List<Rating>) -> Unit, onFailure: (Exception) -> Unit) {
        if (failLoads) {
            onFailure(Exception("offline"))
        } else {
            onSuccess(saved.values.toList())
        }
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

    // Adversarial additions: repeated replacement, empty and loaded-to-failure paths,
    // isolation across campsites, and a bounded crowd of raters
    @Test
    fun saveRating_repeatedFiveTimesByOneUser_keepsOnlyTheLatest() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        val controller = CampsiteController(model, repo)
        for (stars in 1..5) {
            controller.saveRating("c1", "u1", stars)
        }
        assertEquals(1, repo.saved.size)
        assertEquals(1, model.ratingCount("c1"))
        assertEquals(5, model.myRating("c1", "u1"))
    }

    @Test
    fun averageRating_whenNobodyHasRated_isZeroAndNotAnError() {
        val model = CampsiteModel()
        assertEquals(0.0, model.averageRating("c1"), 0.001)
        assertEquals(0, model.ratingCount("c1"))
    }

    @Test
    fun saveRating_atTwoCampsites_doNotCrossTalk() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        val controller = CampsiteController(model, repo)
        controller.saveRating("c1", "u1", 4)
        controller.saveRating("c2", "u1", 1)
        assertEquals(4.0, model.averageRating("c1"), 0.001)
        assertEquals(1, model.ratingCount("c1"))
        assertEquals(1.0, model.averageRating("c2"), 0.001)
        assertEquals(1, model.ratingCount("c2"))
    }

    @Test
    fun averageRating_withTwentyFiveRaters_isTheExactAverage() {
        val repo = FakeRatingRepository()
        val model = CampsiteModel()
        val controller = CampsiteController(model, repo)
        for (userIndex in 1..25) {
            controller.saveRating("c1", "u-$userIndex", ((userIndex - 1) % 5) + 1)
        }
        assertEquals(25, model.ratingCount("c1"))
        assertEquals(3.0, model.averageRating("c1"), 0.001)
    }

    @Test
    fun loadRatings_whenTheLoadFails_leavesTheModelAloneAndSaysSo() {
        val repo = FakeRatingRepository()
        repo.failLoads = true
        val model = CampsiteModel()
        CampsiteController(model, repo).loadRatings()
        assertEquals(0, model.ratingCount("c1"))
        assertEquals("Could not load ratings: offline", model.statusMessage)
    }
}