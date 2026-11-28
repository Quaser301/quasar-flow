package com.example.campbnb.models

import com.example.campbnb.database.QueryEngine
import com.example.campbnb.models.Campsite

/**
 * Works out which dates a campsite is open for booking, reading only through [QueryEngine].
 *
 * It answers "which dates has the owner made available?" and nothing else. Subtracting dates
 * that are already reserved is the job of
 * [com.example.campbnb.controller.ReservationController].
 *
 * Commands:
 *  - [getOpenDates]: The campsite's own available dates, minus blocked and past dates.
 *  - [getOpenDatesForLottery]: The dates a lottery winner may choose from at one of its campsites.
 *  - [getCampsite]: Looks up one campsite, for checks like who owns it.
 *
 * @property campsites Read access to campsites and their availability settings.
 * @author Zino
 * @see CampsiteModel
 * @see com.example.campbnb.controller.ReservationController
 */
class Availability(private val campsites: QueryEngine<Campsite>) {

    // getOpenDates(campsiteId, onSuccess, onFailure): unchanged, keep your existing version here.

    /**
     * Gets the dates a lottery winner may choose from at a campsite, before subtracting
     * existing reservations.
     *
     * The open dates are the lottery's available range, minus the lottery's blocked dates,
     * minus any dates blocked on the campsite itself, minus anything in the past or not after
     * the booking period. Empty if the lottery doesn't cover the campsite or has no range.
     *
     * @param lottery The lottery the entrant won, already loaded by the caller.
     * @param campsiteId The campsite they want to reserve. Must be one of the lottery's campsites.
     * @param onSuccess Receives the open dates as yyyy-MM-dd strings.
     * @param onFailure Receives any Firestore error, or a parse error if a stored date is malformed.
     */
    fun getOpenDatesForLottery(
        lottery: Lottery,
        campsiteId: String,
        onSuccess: (Set<String>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (campsiteId !in lottery.campsiteIds ||
            lottery.availableStartDate.isEmpty() ||
            lottery.availableEndDate.isEmpty()
        ) {
            onSuccess(emptySet())
            return
        }

        campsites.getById(campsiteId, onSuccess = { campsite ->
            val open = try {
                val today = DateUtils.today()
                val bookingEndDate = DateUtils.utcDate(lottery.bookingEnd)
                val blocked = lottery.blockedDates.toSet() + campsite?.blockedDates.orEmpty()
                DateUtils.datesBetween(lottery.availableStartDate, lottery.availableEndDate)
                    .filter { it >= today && it > bookingEndDate && it !in blocked }
                    .toSet()
            } catch (e: Exception) {
                onFailure(e)
                return@getById
            }
            onSuccess(open)
        }, onFailure = onFailure)
    }

    fun getCampsite(
        campsiteId: String,
        onSuccess: (Campsite?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        campsites.getById(
            id = campsiteId,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }
}