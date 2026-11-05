package com.example.campbnb.controller

import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.models.Availability
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which slice of a campsite's reservations an owner wants to see. */
enum class ReservationFilter {
    /** Active reservations that haven't ended yet. */
    ACTIVE,

    /** Active reservations whose end date has passed. */
    PAST,

    /** Cancelled and rejected reservations. */
    CANCELLED
}

/**
 * Coordinates everything that happens after an entrant wins a campsite lottery:
 * checking they're allowed to book, showing them which dates are open, creating
 * the reservation, and handling cancellations and rejections afterward. It is the
 * single point the UI talks to for reservation logic, and it delegates to its
 * collaborators rather than doing their jobs itself.
 *
 * Every operation is asynchronous and reports back through `onSuccess` and `onFailure`
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
 *    the campsite, start and end dates, and duration.
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
     * Verifies the entrant won this lottery for this campsite and the booking period is open.
     *
     * @param lotteryId The lottery being booked against.
     * @param entrantId The entrant making the request.
     * @param campsiteId The campsite the entrant wants to reserve.
     * @param onEligible Called only if both checks pass.
     * @param onFailure Called if the entrant did not win, booking is closed, or a lookup fails.
     */
    private fun checkEligibility(
        lotteryId: String,
        entrantId: String,
        campsiteId: String,
        onEligible: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        lotteryController.checkIfWon(lotteryId, entrantId, campsiteId, onSuccess = { won ->
            if (!won) onFailure(IllegalStateException("Entrant did not win this lottery for this campsite"))
            else lotteryController.isBookingOpen(lotteryId, onSuccess = { open ->
                if (open) onEligible() else onFailure(IllegalStateException("Booking period is not open"))
            }, onFailure = onFailure)
        }, onFailure = onFailure)
    }

    /**
     * Returns the dates the entrant can reserve: the campsite's open dates minus booked dates.
     *
     * @param lotteryEntryId The lottery entry being booked against.
     * @param entrantId The entrant asking for dates. Must have won.
     * @param campsiteId The campsite to list dates for.
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

    /**
     * Records that the entrant accepted the rules and policies for this lottery entry.
     *
     * Kept in memory only, so it resets when the app restarts.
     *
     * @param lotteryEntryId The lottery entry the entrant accepted the policies for.
     */
    fun confirmPolicies(lotteryEntryId: String) {
        policiesConfirmed.add(lotteryEntryId)
    }

    /**
     * Asks [NotificationController] to notify [recipientId] about a reservation event.
     *
     * Notification failures never fail the reservation itself.
     *
     * @param recipientId The ID of the user to notify.
     * @param type What happened: `"CONFIRMATION"`, `"CANCELLATION"` or `"REJECTION"`.
     * @param reservation The reservation the event is about. Its campsite, start and end
     * dates, and number of dates are sent as the notification details.
     */
    fun sendNotification(recipientId: String, type: String, reservation: ReservationModel) {
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

    /**
     * Re-checks the chosen dates, then creates and saves the reservation.
     *
     * The repository's database rules are the final guard against two entrants booking the
     * same date at once; the re-check here gives a cleaner error in the normal case. On
     * success the owner is sent a confirmation.
     *
     * @param lotteryEntryId The lottery entry the reservation belongs to. Becomes the
     * reservation's ID, so an entry can only be reserved once.
     * @param entrantId The entrant making the reservation.
     * @param ownerId The owner of the campsite, who receives the confirmation.
     * @param campsiteId The campsite being reserved.
     * @param dates The selected dates as ISO strings. Must not be empty.
     * @param onSuccess Receives the saved [ReservationModel].
     * @param onFailure Called if policies weren't confirmed, the entrant is ineligible,
     * any date is no longer available, or the save fails.
     * @param guests How many guests are staying.
     * @param refundRecorded Whether a refund is already recorded. New reservations pass false.
     * @param createdAt When the reservation is made, in epoch milliseconds. Defaults to now.
     * @param rules Whether the entrant accepted the campsite rules and policies.
     */
    fun createReservation(
        lotteryEntryId: String,
        entrantId: String,
        ownerId: String,
        campsiteId: String,
        dates: Set<String>,
        onSuccess: (ReservationModel) -> Unit,
        onFailure: (Exception) -> Unit,
        guests: Int,
        refundRecorded: Boolean,
        createdAt: Long = System.currentTimeMillis(),
        rules: Boolean
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
                guests = guests,
                refundRecorded = refundRecorded,
                createdAt = createdAt,
                rules = rules
            )

            reservations.save(reservation, onSuccess = {
                sendNotification(
                    recipientId = ownerId,
                    type = "CONFIRMATION",
                    reservation = reservation
                )
                onSuccess(reservation)
            }, onFailure = onFailure)
        }, onFailure = onFailure)
    }

    /**
     * Entrant cancels their own reservation. Frees its dates, records the refund, and
     * notifies the owner.
     *
     * @param reservationId The reservation to cancel.
     * @param requesterId Must match the reservation's entrant, or the call fails.
     * @param onSuccess Called once the reservation is cancelled and the owner is notified.
     * @param onFailure Called if the reservation doesn't exist, the requester isn't the
     * entrant, or the repository fails.
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
     * Owner rejects a reservation. Frees its dates, records the refund, and notifies
     * the entrant.
     *
     * @param reservationId The reservation to reject.
     * @param requesterId Must match the reservation's owner, or the call fails.
     * @param onSuccess Called once the reservation is rejected and the entrant is notified.
     * @param onFailure Called if the reservation doesn't exist, the requester isn't the
     * owner, or the repository fails.
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

    /**
     * Lists every reservation an entrant has made, any status.
     *
     * @param entrantId The entrant to list reservations for.
     * @param onSuccess Receives the reservations, earliest start date first.
     * @param onFailure Receives any lookup error.
     */
    fun listReservations(
        entrantId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) = reservations.getForEntrant(entrantId, onSuccess, onFailure)

    /**
     * Lists an owner's reservations for one campsite, filtered to active, past or cancelled.
     *
     * @param campsiteId The campsite to list reservations for.
     * @param filter Which slice to return: [ReservationFilter.ACTIVE], [ReservationFilter.PAST]
     * or [ReservationFilter.CANCELLED].
     * @param onSuccess Receives the matching reservations.
     * @param onFailure Receives any lookup error.
     */
    fun listReservations(
        campsiteId: String,
        filter: ReservationFilter,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) = when (filter) {
        ReservationFilter.ACTIVE -> reservations.getActive(campsiteId, onSuccess, onFailure)
        ReservationFilter.PAST -> reservations.getPast(campsiteId, onSuccess, onFailure)
        ReservationFilter.CANCELLED -> reservations.getCancelled(
            campsiteId,
            onSuccess,
            onFailure
        )
    }

    /**
     * Builds the CSV text for a confirmation or cancellation receipt.
     *
     * Every value is wrapped in quotes, with quotes inside a value doubled, so commas and
     * quotes in IDs can't break the columns.
     *
     * @param reservation The reservation the receipt is for.
     * @return A header row and one data row. Writing it to a file is up to the caller.
     */
    fun generateReceiptCsv(reservation: ReservationModel): String {
        fun esc(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val header =
            "reservationId,campsiteId,entrantId,ownerId,startDate,endDate,nights,status,refundRecorded,generatedOn"
        val row = listOf(
            reservation.lotteryEntryId, reservation.campsiteId, reservation.entrantId,
            reservation.ownerId, reservation.startDate, reservation.endDate,
            reservation.dates.size.toString(), reservation.status.name,
            reservation.refundRecorded.toString(),
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        ).joinToString(",") { esc(it) }
        return "$header\n$row"
    }
}