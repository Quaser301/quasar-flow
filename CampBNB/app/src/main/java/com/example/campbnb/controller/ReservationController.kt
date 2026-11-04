package com.example.campbnb.controller


import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.models.Reservation
import com.example.campbnb.models.Availability
import com.example.campbnb.database.QueryEngine

/**
 * ReservationController - Zino
 *
 * Coordinates everything that happens after an entrant wins a campsite lottery:
 * checking they're allowed to book, showing them which dates are open, creating
 * the reservation, and handling cancellations and rejections afterward. It is the
 * single point the UI talks to for reservation logic, and it delegates to its
 * collaborators rather than doing their jobs itself.
 *
 * Collaborators:
 *  - [LotteryController]: confirms whether an entrant won a given campsite lottery
 *  - [Availability]: knows which dates are open or already reserved
 *  - [Reservation]: the reservation model (dates, status, refund info)
 *  - DataBase: persists reservations and marks dates as reserved or freed
 *  - [NotificationController]: sends confirmation, cancellation and rejection messages
 *
 * Commands:
 *
 *  - [checkEligibility]: Verifies the entrant won the lottery for this campsite and
 *    that the booking period is currently open. Nothing else should proceed if this fails.
 *
 *  - [getAvailableDates]: Returns the dates the entrant may reserve for the campsite.
 *    Only returns data if [checkEligibility] passes.
 *
 *  - [confirmPolicies]: Records that the entrant has accepted the rules and policies.
 *    A reservation cannot be created until this has happened.
 *
 *  - [createReservation]: Re-checks the selected dates at booking time (in case someone
 *    else took them), then creates the [Reservation], saves it, and marks the dates as
 *    reserved. Fails if policies were not confirmed or any date is no longer available.
 *
 *  - [cancelReservation]: Called when an entrant cancels. Sets the reservation to
 *    cancelled, frees its dates, records the refund, and notifies the entrant.
 *
 *  - [rejectReservation]: Called when the owner rejects a reservation. Sets it to
 *    rejected, frees its dates, records the refund, and notifies the entrant.
 *
 *  - [listReservations]: Returns all active, past and cancelled reservations for a
 *    given entrant or owner.
 *
 *  - [generateReceiptCsv]: Builds the CSV file attached to a confirmation or
 *    cancellation notification.
 *
 *  - [sendNotification]: Write a flag on the specific document in the database
 *    to notify the owner with the necessary info: (name date duration location)
 */
class ReservationController (
    private val lotteryController: LotteryController,
    //private val db: Reservation,
    private val notifications: NotificationController
) {
    public fun checkEligibility() {
    }

    public fun getAvailableDates() {
    }

    public fun confirmPolicies() {
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