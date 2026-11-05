package com.example.campbnb.models

import com.example.campbnb.database.QueryEngine

/**
 * Works out which dates a campsite is open for booking, reading only through [QueryEngine].
 *
 * It answers "which dates has the owner made available?" and nothing else. Subtracting dates
 * that are already reserved is the job of
 * [com.example.campbnb.controller.ReservationController].
 *
 * Commands:
 *  - [getOpenDates]: The owner's available dates for a campsite, minus blocked and past dates.
 *
 * @property campsites Read access to campsites and their availability settings.
 * @author Zino
 * @see CampsiteModel
 * @see com.example.campbnb.controller.ReservationController
 */
class Availability(private val campsites: QueryEngine<CampsiteModel.CampsiteModel>) {

    /**
     * Gets the dates a campsite is open for booking, before subtracting existing reservations.
     *
     * The open dates are every date from the campsite's available start date to its available
     * end date, minus any owner-blocked dates and any date already in the past. Dates already
     * booked by reservations are removed later, by the controller.
     *
     * @param campsiteId The campsite to look up.
     * @param onSuccess Receives the open dates as yyyy-MM-dd strings. Empty if the campsite
     * doesn't exist or the owner hasn't set an availability range.
     * @param onFailure Receives any Firestore error, or a parse error if a stored date is malformed.
     */
    fun getOpenDates(
        campsiteId: String,
        onSuccess: (Set<String>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        campsites.getById(campsiteId, onSuccess = { campsite ->
            if (campsite == null ||
                campsite.availableStartDate.isEmpty() ||
                campsite.availableEndDate.isEmpty()
            ) {
                onSuccess(emptySet())
                return@getById
            }

            val today = DateUtils.today()
            val open = try {
                DateUtils.datesBetween(campsite.availableStartDate, campsite.availableEndDate)
                    .filter { it >= today && it !in campsite.blockedDates }
                    .toSet()
            } catch (e: Exception) {
                onFailure(e)
                return@getById
            }
            onSuccess(open)
        }, onFailure = onFailure)
    }
}