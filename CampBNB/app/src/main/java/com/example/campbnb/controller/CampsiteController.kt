package com.example.campbnb.controller

import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.Rating

/**
 * Controller for CampsiteModel, campsite search, details, and ratings for entrants.
 * Owner methods like register, update, QR.
 */
class CampsiteController (
    private val model: CampsiteModel,
    private val repo: CampsiteRepository
){
    /** Loads every campsite and every rating into the model (US 01.01.03, 01.01.04). */
    fun loadAll() {
        repo.getAll(
            onSuccess = { campsites -> model.setCampsites(campsites) },
            onFailure = { error -> model.setStatusMessage("Could not load campsites: " + error.message) }
        )
        loadRatings()
    }

    /** Loads every rating into the model, so averages and the user's own rating can be shown. */
    fun loadRatings() {
        repo.getAllRatings(
            onSuccess = { ratings -> model.setRatings(ratings) },
            onFailure = { error -> model.setStatusMessage("Could not load ratings: " + error.message) }
        )
    }

    /**
     * Shows only the campsites whose name, location or a tag contains [query] (ignoring case)
     * and that hold at least [guests] people. Blank text or null guests means no filter
     * (US 01.01.01, 01.01.03).
     */
    fun search(query: String, guests: Int?) {
        val results = mutableListOf<Campsite>()
        for (campsite in model.allCampsites) {
            if (matchesText(campsite, query) && matchesGuests(campsite, guests)) {
                results.add(campsite)
            }
        }
        model.setVisibleCampsites(results)
    }

    /** Case-insensitive match of the query against name, location, or any tag;
     *     an empty query matches everything. */
    private fun matchesText(campsite: Campsite, query: String): Boolean {
        val text = query.trim()
        if (text.isEmpty()) {
            return true
        }
        if (campsite.campName.contains(text, ignoreCase = true)) {
            return true
        }
        if (campsite.campLoc.contains(text, ignoreCase = true)) {
            return true
        }
        for (tag in campsite.tags) {
            if (tag.contains(text, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /** True when the campsite sleeps at least the requested party size;
     *     no size requested means no filter. */
    private fun matchesGuests(campsite: Campsite, guests: Int?): Boolean {
        if (guests == null) {
            return true
        }
        return campsite.maxOccupancy >= guests
    }

    /** Puts the campsite with this id into the model for the Details screen (US 01.01.05). */
    fun selectCampsite(campsiteId: String) {
        var found: Campsite? = null
        for (campsite in model.allCampsites) {
            if (campsite.id == campsiteId) {
                found = campsite
            }
        }
        model.selectCampsite(found)
    }

    /** Saves a 1-5 star rating; rating again replaces the old one (US 01.13.01). */
    fun saveRating(campsiteId: String, userId: String, stars: Int) {
        if (userId.isBlank()) {
            model.setStatusMessage("Please sign in before rating")
            return
        }
        if (stars < 1 || stars > 5) {
            model.setStatusMessage("Rating must be between 1 and 5 stars")
            return
        }
        val rating = Rating(campsiteId, userId, stars)
        repo.saveRating(
            rating,
            onComplete = { result ->
                if (result.isSuccess) {
                    replaceRating(rating)
                    model.setStatusMessage("Thanks for rating!")
                } else {
                    model.setStatusMessage("Could not save your rating")
                }
            }
        )
    }

    /** Puts [rating] into the model's list, replacing the same user's old rating of the campsite. */
    private fun replaceRating(rating: Rating) {
        val updated = mutableListOf<Rating>()
        for (old in model.ratings) {
            if (old.id != rating.id) {
                updated.add(old)
            }
        }
        updated.add(rating)
        model.setRatings(updated)
    }
    // Registers a new campsite for the current user
    /** Publishes a new campsite owned by this user; a blank owner is refused through the
     *     model's status message and reports false.
     * @param campsite the details the owner filled in
     * @param ownerId the signed-in owner publishing it
     * @param onComplete called with whether the registration was saved */
    fun registerCampsite(
        campsite: Campsite,
        ownerId: String,
        onComplete: (boolean: Boolean) -> Unit

    ){
        if (ownerId.isBlank()) {
            model.setStatusMessage("Please log in to register a campsite")
            onComplete(false)
            return
        }
        val newCampsite = Campsite(
            ownerId = ownerId,
            campName = campsite.campName,
            campLoc = campsite.campLoc,
            campDesc = campsite.campDesc,
            campAmenities = campsite.campAmenities,
            geoLocation = campsite.geoLocation,
            campImg = campsite.campImg,
            maxOccupancy = campsite.maxOccupancy,
            tags = campsite.tags,
            cancelPolicy = campsite.cancelPolicy,
            campPrice = campsite.campPrice
        )
        repo.SaveCampsite(newCampsite) { result ->
            if (result.isSuccess) {
                model.setStatusMessage("Campsite registered successfully")
                onComplete(true)
            } else {
                model.setStatusMessage("Failed to register campsite")
                onComplete(false)
            }
        }
    }


    /**
     * Updates a campsite only if it belongs to the current owner.
     */
    fun updateCampsite(
        campsite: Campsite,
        ownerId: String,
        onComplete: (Boolean) -> Unit
    ) {
        if (ownerId.isBlank() || campsite.id.isBlank()) {
            model.setStatusMessage("Invalid owner or campsite")
            onComplete(false)
            return
        }

        repo.getAll(
            onSuccess = { allCampsites ->

                val existing = allCampsites.find {
                    it.id == campsite.id
                }

                // Verify that the campsite belongs to this owner.
                if (existing == null || existing.ownerId != ownerId) {
                    model.setStatusMessage(
                        "You cannot update this campsite"
                    )
                    onComplete(false)
                    return@getAll
                }

                // Prevent changing the original owner.
                if (campsite.ownerId != existing.ownerId) {
                    model.setStatusMessage(
                        "Campsite owner cannot be changed"
                    )
                    onComplete(false)
                    return@getAll
                }

                // Preserve the original owner and document ID.
                val campsiteToSave = Campsite(
                    ownerId = existing.ownerId,
                    campName = campsite.campName,
                    campLoc = campsite.campLoc,
                    campDesc = campsite.campDesc,
                    campAmenities = campsite.campAmenities,
                    geoLocation = campsite.geoLocation,
                    campImg = campsite.campImg,
                    maxOccupancy = campsite.maxOccupancy,
                    tags = campsite.tags,
                    cancelPolicy = campsite.cancelPolicy,
                    campPrice = campsite.campPrice,
                    availableStartDate = campsite.availableStartDate,
                    availableEndDate = campsite.availableEndDate,
                    blockedDates = campsite.blockedDates
                ).apply {
                    id = existing.id
                }

                repo.SaveCampsite(campsiteToSave) { result ->
                    if (result.isSuccess) {
                        model.setStatusMessage(
                            "Campsite updated successfully"
                        )
                        onComplete(true)
                    } else {
                        model.setStatusMessage(
                            "Failed to update campsite"
                        )
                        onComplete(false)
                    }
                }
            },
            onFailure = {
                model.setStatusMessage(
                    "Could not verify campsite owner"
                )
                onComplete(false)
            }
        )
    }
    /** Loads every campsite, then shows only the ones matching [query] (US 01.01.01, 01.01.03). */
    fun loadAndSearch(query: String) {
        repo.getAll(
            onSuccess = { campsites ->
                model.setCampsites(campsites)
                search(query, null)
            },
            onFailure = { error -> model.setStatusMessage("Could not load campsites: " + error.message) }
        )
        loadRatings()
    }

    /** Loads every campsite, then puts the one with [campsiteId] on the Details screen (US 01.01.05). */
    fun loadAndSelect(campsiteId: String) {
        repo.getAll(
            onSuccess = { campsites ->
                model.setCampsites(campsites)
                selectCampsite(campsiteId)
            },
            onFailure = { error -> model.setStatusMessage("Could not load campsites: " + error.message) }
        )
        loadRatings()
    }
}