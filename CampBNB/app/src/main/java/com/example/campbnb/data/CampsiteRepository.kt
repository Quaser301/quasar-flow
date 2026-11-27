package com.example.campbnb.data

import com.example.campbnb.database.Database
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Rating

// Prompted Claude Sonnet 5.5 for implementation and concepts related to this file.

/**
 * Boundary to Firestore for campsites and ratings. Methods are 'open' so tests can
 * override them with a fake if needed.
 */
open class CampsiteRepository {

    /** Reads every campsite. */

    // Saves  campsite to Firestore.
    open fun SaveCampsite(campsite: Campsite, onComplete: (Result<Exception?>) -> Unit) {
        Database.Campsites.writeEngine().save(campsite, onComplete)
    }

    /**
     * Deletes a campsite from Firestore using its document ID.
     */
    open fun deleteCampsite(
        campsiteId: String,
        onComplete: (Result<Exception?>) -> Unit
    ) {
        Database.Campsites.writeEngine().delete(campsiteId, onComplete)
    }

    /**
     * Writes only one campsite's picture text (US 03.03.01), so nothing else on the listing is
     * touched. Empty [image] removes the picture.
     */
    open fun saveCampsiteImage(
        campsiteId: String,
        image: String,
        onComplete: (Result<Exception?>) -> Unit
    ) {
        Database.Campsites.writeEngine().update(campsiteId, mapOf(Campsite::campImg.name to image), onComplete)
    }

    open fun getAll(onSuccess: (List<Campsite>) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Campsites.readEngine().query().execute(onSuccess, onFailure)
    }

    /** Reads every rating, so the list can show each campsite's average (US 01.01.04). */
    open fun getAllRatings(onSuccess: (List<Rating>) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Ratings.readEngine().query().execute(onSuccess, onFailure)
    }

    /** Reads one campsite by its document id; the result is null if it no longer exists. */
    open fun getById(campsiteId: String, onSuccess: (Campsite?) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Campsites.readEngine().getById(campsiteId, onSuccess, onFailure)
    }

    /** Reads the ratings one campsite has received (used when an admin deletes that campsite). */
    open fun getRatingsByCampsite(campsiteId: String, onSuccess: (List<Rating>) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Ratings.readEngine().query()
            .whereEquals(Rating::campsiteId, campsiteId)
            .execute(onSuccess, onFailure)
    }

    /** Reads the ratings one user has given (used when an admin deletes that user's profile). */
    open fun getRatingsByUser(userId: String, onSuccess: (List<Rating>) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Ratings.readEngine().query()
            .whereEquals(Rating::userId, userId)
            .execute(onSuccess, onFailure)
    }

    /** Reads one rating by its document id; the result is null if it no longer exists. */
    open fun getRatingById(ratingId: String, onSuccess: (Rating?) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Ratings.readEngine().getById(ratingId, onSuccess, onFailure)
    }

    /** Deletes one rating by its document id. */
    open fun deleteRating(ratingId: String, onComplete: (Result<Exception?>) -> Unit) {
        Database.Ratings.writeEngine().delete(ratingId, onComplete)
    }

    /** Saves a rating; rating again replaces the old one because the id is the same. */
    open fun saveRating(rating: Rating, onComplete: (Result<Exception?>) -> Unit) {
        Database.Ratings.writeEngine().save(rating, onComplete)
    }

    /** Reads the campsites registered by one owner. */
    open fun getByOwner(ownerId: String, onSuccess: (List<Campsite>) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Campsites.readEngine().query()
            .whereEquals(Campsite::ownerId, ownerId)
            .execute(onSuccess, onFailure)
    }
}