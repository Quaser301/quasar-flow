package com.example.campbnb.controller

import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.models.Availability
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which slice of a campsite's reservations an owner wants to see. */
enum class ReservationFilter { ACTIVE, PAST, CANCELLED }

/**
 * Coordinates everything that happens after an entrant wins a campsite lottery:
 * checking they're allowed to book, showing them which dates are open, creating
 * the reservation, and handling cancellations and rejections afterward. It is the
 * single point the UI talks to for reservation logic, and it delegates to its
 * collaborators rather than doing their jobs itself.
 *
 * Every operation is asynchronous and reports back through [onSuccess] and [onFailure]
 * callbacks, matching [com.example.campbnb.database.QueryEngine].
 *
 * Commands:
 *  - [checkEligibility]: Verifies the entrant won the lottery for this campsite and
 *    that the booking period is open. Nothing else proceeds if this fails.
 *  - [getAvailableDates]: Returns the dates the entrant may reserve (the campsite's open
 *    dates minus dates already booked). Only returns data if [checkEligibility] passes.
 *  - [confirmPolicies]: Records that the entrant accepted the rules and policies.
 *    A reservation cannot be created until this has happened.
 *  - [createReservation]: Re-checks the selected dates at booking time, then creates
 *    the [ReservationModel], saves it, marks the dates as booked, and notifies the owner.
 *  - [cancelReservation]: Entrant action. Sets a reservation to cancelled, frees its dates,
 *    records the refund, and notifies the owner.
 *  - [rejectReservation]: Owner action. Sets a reservation to rejected, frees its dates,
 *    records the refund, and notifies the entrant.
 *  - [listReservations]: Returns an entrant's reservations, or an owner's reservations for
 *    one campsite filtered by [ReservationFilter].
 *  - [generateReceiptCsv]: Builds the CSV text for a confirmation or cancellation.
 *  - [sendNotification]: Asks [NotificationController] to notify the other party, including
 *    name, date, duration and location.
 *
 * @author Zino
 * @constructor Creates a controller wired to its collaborators.
 * @property lotteryController Used to confirm that an entrant won a given campsite lottery
 * and that its booking period is open.
 * @property availability Provides the dates a campsite is open for booking.
 * @property reservations Persists and queries reservations, and books or frees dates.
 * @property notifications Sends confirmation, cancellation and rejection messages.
 * @see LotteryController
 * @see Availability
 * @see ReservationRepository
 * @see ReservationModel
 * @see NotificationController
 */
class ReservationController(
    private val lotteryController: LotteryController,
    private val availability: Availability,
    private val reservations: ReservationRepository,
    private val notifications: NotificationController
) {
    /** Lottery entries whose policies have been accepted this session. */
    private val policiesConfirmed = mutableSetOf<String>()

    /**
     * Verifies the entrant won this lottery entry and the booking period is open.
     *
     * @param lotteryEntryId The entry being booked against.
     * @param entrantId The entrant making the request.
     * @param campsiteId The campsite the lottery was for.
     * @param onEligible Called only if both checks pass.
     * @param onFailure Called if the entrant did not win, booking is closed, or a lookup fails.
     */
    private fun checkEligibility(
        lotteryEntryId: String,
        entrantId: String,
        campsiteId: String,
        onEligible: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        lotteryController.hasWon(
            lotteryEntryId, entrantId,
            onSuccess = { won ->
                if (!won) {
                    onFailure(IllegalStateException("Entrant did not win this lottery"))
                } else {
                    lotteryController.isBookingOpen(
                        campsiteId,
                        onSuccess = { open ->
                            if (open) onEligible()
                            else onFailure(IllegalStateException("Booking period is not open"))
                        },
                        onFailure = onFailure
                    )
                }
            },
            onFailure = onFailure
        )
    }

    /**
     * Returns the dates the entrant can reserve: the campsite's open dates minus booked dates.
     *
     * @param onSuccess Receives the available dates as ISO strings (yyyy-MM-dd).
     * @param onFailure Called if the entrant is not eligible or a lookup fails.
     */
    fun getAvailableDates(
        lotteryEntryId: String,
        entrantId: String,
        campsiteId: String,
        onSuccess: (Set<String>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        checkEligibility(lotteryEntryId, entrantId, campsiteId, onEligible = {
            availability.getOpenDates(
                campsiteId,
                onSuccess = { open ->
                    reservations.getBookedDates(
                        campsiteId,
                        onSuccess = { booked -> onSuccess(open - booked) },
                        onFailure = onFailure
                    )
                },
                onFailure = onFailure
            )
        }, onFailure = onFailure)
    }

    /** Records that the entrant accepted the rules and policies for this lottery entry. */
    fun confirmPolicies(lotteryEntryId: String) {
        policiesConfirmed.add(lotteryEntryId)
    }

    /**
     * Re-checks the chosen dates, then creates and saves the reservation.
     *
     * The repository's transaction is the final guard against two entrants booking the
     * same date at once; the re-check here gives a cleaner error in the normal case.
     *
     * @param dates The selected dates as ISO strings. Must not be empty.
     * @param onSuccess Receives the saved [ReservationModel].
     * @param onFailure Called if policies weren't confirmed, the entrant is ineligible,
     * any date is no longer available, or the save fails.
     */
    fun createReservation(
        lotteryEntryId: String,
        entrantId: String,
        ownerId: String,
        campsiteId: String,
        dates: Set<String>,
        onSuccess: (ReservationModel) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (lotteryEntryId !in policiesConfirmed) {
            onFailure(IllegalStateException("Policies must be confirmed before reserving"))
            return
        }
        if (dates.isEmpty()) {
            onFailure(IllegalArgumentException("Select at least one date"))
            return
        }

        getAvailableDates(lotteryEntryId, entrantId, campsiteId, onSuccess = { available ->
            if (!available.containsAll(dates)) {
                onFailure(IllegalStateException("One or more dates are no longer available"))
                return@getAvailableDates
            }

            val sorted = dates.sorted()
            val reservation = ReservationModel(
                lotteryEntryId = lotteryEntryId,
                campsiteId = campsiteId,
                entrantId = entrantId,
                ownerId = ownerId,
                dates = sorted,
                startDate = sorted.first(),
                endDate = sorted.last(),
                status = ReservationStatus.ACTIVE,
                guests = TODO(),
                refundRecorded = TODO(),
                createdAt = TODO(),
                rules = TODO()
            )

            reservations.save(reservation, onSuccess = {
                sendNotification(recipientId = ownerId, type = "CONFIRMATION", reservation = reservation)
                onSuccess(reservation)
            }, onFailure = onFailure)
        }, onFailure = onFailure)
    }

    /**
     * Entrant cancels their own reservation. Frees its dates and records the refund.
     *
     * @param requesterId Must match the reservation's entrant, or the call fails.
     */
    fun cancelReservation(
        reservationId: String,
        requesterId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        reservations.getByLotteryEntry(reservationId, onSuccess = { reservation ->
            when {
                reservation == null -> onFailure(NoSuchElementException("No reservation $reservationId"))
                reservation.entrantId != requesterId ->
                    onFailure(SecurityException("Only the entrant can cancel this reservation"))
                else -> reservations.markCancelled(reservationId, onSuccess = {
                    sendNotification(reservation.ownerId, "CANCELLATION", reservation)
                    onSuccess()
                }, onFailure = onFailure)
            }
        }, onFailure = onFailure)
    }

    /**
     * Owner rejects a reservation. Frees its dates and records the refund.
     *
     * @param requesterId Must match the reservation's owner, or the call fails.
     */
    fun rejectReservation(
        reservationId: String,
        requesterId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        reservations.getByLotteryEntry(reservationId, onSuccess = { reservation ->
            when {
                reservation == null -> onFailure(NoSuchElementException("No reservation $reservationId"))
                reservation.ownerId != requesterId ->
                    onFailure(SecurityException("Only the campsite owner can reject this reservation"))
                else -> reservations.markRejected(reservationId, onSuccess = {
                    sendNotification(reservation.entrantId, "REJECTION", reservation)
                    onSuccess()
                }, onFailure = onFailure)
            }
        }, onFailure = onFailure)
    }

    /** Lists every reservation an entrant has made, any status. */
    fun listReservations(
        entrantId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) = reservations.getForEntrant(entrantId, onSuccess, onFailure)

    /** Lists an owner's reservations for one campsite, filtered to active, past or cancelled. */
    fun listReservations(
        campsiteId: String,
        filter: ReservationFilter,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) = when (filter) {
        ReservationFilter.ACTIVE -> reservations.getActive(campsiteId, onSuccess, onFailure)
        ReservationFilter.PAST -> reservations.getPast(campsiteId, onSuccess, onFailure)
        ReservationFilter.CANCELLED -> reservations.getCancelled(campsiteId, onSuccess, onFailure)
    }

    /**
     * Builds the CSV text for a confirmation or cancellation receipt.
     *
     * @param reservation The reservation the receipt is for.
     * @return A header row and one data row. Writing it to a file is up to the caller.
     */
    fun generateReceiptCsv(reservation: ReservationModel): String {
        fun esc(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val header = "reservationId,campsiteId,entrantId,ownerId,startDate,endDate,nights,status,refundRecorded,generatedOn"
        val row = listOf(
            reservation.lotteryEntryId, reservation.campsiteId, reservation.entrantId,
            reservation.ownerId, reservation.startDate, reservation.endDate,
            reservation.dates.size.toString(), reservation.status.name,
            reservation.refundRecorded.toString(),
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        ).joinToString(",") { esc(it) }
        return "$header\n$row"
    }

    /**
     * Asks [NotificationController] to notify [recipientId] about a reservation event.
     *
     * Notification failures never fail the reservation itself.
     */
    private fun sendNotification(recipientId: String, type: String, reservation: ReservationModel) {
        notifications.send(
            recipientId = recipientId,
            type = type,
            details = mapOf(
                "campsiteId" to reservation.campsiteId,
                "startDate" to reservation.startDate,
                "endDate" to reservation.endDate,
                "duration" to reservation.dates.size.toString()
            )
        )
    }
}