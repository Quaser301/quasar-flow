package com.example.campbnb.controller

import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.models.Reservation
import com.example.campbnb.models.Availability
import com.example.campbnb.database.QueryEngine

/**
 * Coordinates everything that happens after an entrant wins a campsite lottery:
 * checking they're allowed to book, showing them which dates are open, creating
 * the reservation, and handling cancellations and rejections afterward. It is the
 * single point the UI talks to for reservation logic, and it delegates to its
 * collaborators rather than doing their jobs itself.
 *
 * Commands:
 *  - [checkEligibility]: Verifies the entrant won the lottery for this campsite and
 *    that the booking period is open. Nothing else proceeds if this fails.
 *  - [getAvailableDates]: Returns the dates the entrant may reserve. Only returns
 *    data if [checkEligibility] passes.
 *  - [confirmPolicies]: Records that the entrant accepted the rules and policies.
 *    A reservation cannot be created until this has happened.
 *  - [createReservation]: Re-checks the selected dates at booking time, then creates
 *    the [Reservation], saves it, and marks the dates as reserved.
 *  - [cancelReservation]: Sets a reservation to cancelled, frees its dates, records
 *    the refund, and notifies the entrant.
 *  - [rejectReservation]: Sets a reservation to rejected (owner action), frees its
 *    dates, records the refund, and notifies the entrant.
 *  - [listReservations]: Returns all active, past and cancelled reservations for an
 *    entrant or an owner.
 *  - [generateReceiptCsv]: Builds the CSV file for a confirmation or cancellation.
 *  - [sendNotification]: Writes a flag on the relevant document in the database to
 *    notify the owner, including name, date, duration and location.
 *
 * @author Zino
 * @constructor Creates a controller wired to its collaborators.
 * @property lotteryController Used to confirm that an entrant won a given campsite lottery.
 * @property db Persists reservations and marks dates as reserved or freed.
 * @property notifications Sends confirmation, cancellation and rejection messages.
 * @see LotteryController
 * @see Availability
 * @see Reservation
 * @see NotificationController
 */
class ReservationController (
    private val lotteryController: LotteryController,
    //private val db: Reservation,
    private val notifications: NotificationController
) {
    private fun checkEligibility() {
    }

    public fun getAvailableDates() {
    }

    private fun confirmPolicies() {
    }

    public fun createReservation() {
    }

    public fun cancelReservation() {
    }

    public fun rejectReservation() {
    }

    public fun listReservations() {
    }

    public fun generateReceiptCsv() {
    }

    public fun sendNotification() {
    }
}