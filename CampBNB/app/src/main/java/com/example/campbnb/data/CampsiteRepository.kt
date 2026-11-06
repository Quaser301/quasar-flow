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

    open fun getAll(onSuccess: (List<Campsite>) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Campsites.readEngine().query().execute(onSuccess, onFailure)
    }

    /** Reads every rating, so the list can show each campsite's average (US 01.01.04). */
    open fun getAllRatings(onSuccess: (List<Rating>) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Ratings.readEngine().query().execute(onSuccess, onFailure)
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