package com.example.campbnb.models

/**
 * Observable state for the entrant campsite screens: the campsites loaded, the ones that
 * match the current search, the campsite open on Details, and all ratings.
 */
class CampsiteModel : ObservableModel<CampsiteModel>() {
    var allCampsites: List<Campsite> = emptyList()
        private set
    var visibleCampsites: List<Campsite> = emptyList()
        private set
    var selectedCampsite: Campsite? = null
        private set
    var ratings: List<Rating> = emptyList()
        private set
    var statusMessage: String = ""
        private set

    var ownerId: String = ""
        private set

    var ownedCampsites: List<Campsite> = emptyList()
        private set

    /** Stores every campsite and shows all of them. */
    fun setCampsites(list: List<Campsite>) {
        allCampsites = list
        visibleCampsites = list
        notifyViews()
    }

    /** Sets the campsites shown on the Browse screen (after a search). */
    fun setVisibleCampsites(list: List<Campsite>) {
        visibleCampsites = list
        notifyViews()
    }

    /** Sets the campsite shown on the Details screen. */
    fun selectCampsite(campsite: Campsite?) {
        selectedCampsite = campsite
        notifyViews()
    }

    fun setRatings(list: List<Rating>) {
        ratings = list
        notifyViews()
    }

    fun setStatusMessage(message: String) {
        statusMessage = message
        notifyViews()
    }

    /** Average stars for a campsite, or 0.0 if nobody has rated it. */
    fun averageRating(campsiteId: String): Double {
        var total = 0
        var count = 0
        for (rating in ratings) {
            if (rating.campsiteId == campsiteId) {
                total = total + rating.stars
                count = count + 1
            }
        }
        if (count == 0) {
            return 0.0
        }
        return total.toDouble() / count
    }

    /** How many people rated a campsite. */
    fun ratingCount(campsiteId: String): Int {
        var count = 0
        for (rating in ratings) {
            if (rating.campsiteId == campsiteId) {
                count = count + 1
            }
        }
        return count
    }

    /** The stars this user gave a campsite, or 0 if they have not rated it. */
    fun myRating(campsiteId: String, userId: String): Int {
        for (rating in ratings) {
            if (rating.campsiteId == campsiteId && rating.userId == userId) {
                return rating.stars
            }
        }
        return 0
    }

    /** Stores the campsites that belong to the signed-in owner (US 02.03.01). */
    fun setOwnedCampsites(list: List<Campsite>) {
        ownedCampsites = list
        notifyViews()
    }
}