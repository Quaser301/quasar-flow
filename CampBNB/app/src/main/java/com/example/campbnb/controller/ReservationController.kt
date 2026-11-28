
package com.example.campbnb.controller

import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.models.Availability
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.NotificationIntent
import com.example.campbnb.models.ReservationStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Which slice of a campsite's reservations an owner wants to see.
 */
enum class ReservationFilter {
    PENDING,
    ACTIVE,
    PAST,
    CANCELLED
}

/**
 * Coordinates the reservation workflow after an entrant wins a lottery.
 */
class ReservationController(
    private val lotteryController: LotteryController,
    private val availability: Availability,
    private val reservations: ReservationRepository,
    private val notifications: NotificationController
) {

    /**
     * Lottery entries whose policies have been accepted this session.
     */
    private val policiesConfirmed = mutableSetOf<String>()

    /**
     * Parses and formats reservation dates consistently using UTC.
     */
    private fun dateFormatter() =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }

    /**
     * Converts an ISO date into the first millisecond of that UTC day.
     */
    private fun dateStartMillis(date: String): Long {
        return dateFormatter().parse(date)?.time
            ?: throw IllegalArgumentException("Invalid date: $date")
    }

    /**
     * Checks whether a selected date falls inside the lottery booking window.
     * The selected calendar day may overlap either endpoint of the window.
     */
    private fun isInsideBookingWindow(
        date: String,
        bookingStart: Long,
        bookingEnd: Long
    ): Boolean {
        val dayStart = dateStartMillis(date)
        val dayEnd = dayStart + 24L * 60L * 60L * 1000L - 1L

        return dayEnd >= bookingStart && dayStart <= bookingEnd
    }

    /**
     * Checks that selected dates form one uninterrupted sequence.
     */
    private fun areConsecutive(dates: List<String>): Boolean {
        if (dates.isEmpty()) return false

        val formatter = dateFormatter()

        return try {
            for (index in 1 until dates.size) {
                val previous = formatter.parse(dates[index - 1]) ?: return false
                val current = formatter.parse(dates[index]) ?: return false

                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.time = previous
                calendar.add(Calendar.DAY_OF_MONTH, 1)

                if (formatter.format(calendar.time) != formatter.format(current)) {
                    return false
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verifies that the entrant won this lottery for this campsite
     * and that its booking period is currently open.
     */

    private fun checkEligibility(
        lotteryId: String,
        lotteryEntryId: String,
        entrantId: String,
        campsiteId: String,
        onEligible: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        lotteryController.checkEntrantAccepted(
            lotteryId = lotteryId,
            userId = entrantId,
            onSuccess = { accepted ->
                if (!accepted || lotteryEntryId !=
                    com.example.campbnb.models.WaitingListEntry.makeId(
                        lotteryId,
                        entrantId
                    )
                ) {
                    onFailure(
                        IllegalStateException(
                            "You must accept your lottery win before selecting dates"
                        )
                    )
                } else {
                    lotteryController.checkIfWon(
                        lotteryId,
                        entrantId,
                        campsiteId,
                        onSuccess = { won ->
                            if (!won) {
                                onFailure(
                                    IllegalStateException(
                                        "You did not win this lottery for this campsite"
                                    )
                                )
                            } else {
                                lotteryController.isBookingOpen(
                                    lotteryId,
                                    onSuccess = { open ->
                                        if (open) {
                                            onEligible()
                                        } else {
                                            onFailure(
                                                IllegalStateException(
                                                    "Booking period is not open"
                                                )
                                            )
                                        }
                                    },
                                    onFailure = onFailure
                                )
                            }
                        },
                        onFailure = onFailure
                    )
                }
            },
            onFailure = onFailure
        )
    }

    /**
     * Returns dates the entrant can reserve.
     *
     * lotteryId and lotteryEntryId are different:
     * - lotteryId identifies the lottery.
     * - lotteryEntryId identifies the entrant's entry in that lottery.
     */
    fun getAvailableDates(
        lotteryId: String,
        lotteryEntryId: String,
        entrantId: String,
        campsiteId: String,
        onSuccess: (Set<String>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        lotteryController.getLottery(
            lotteryId,
            onSuccess = { lottery ->
                if (lottery == null) {
                    onFailure(NoSuchElementException("Lottery not found"))
                    return@getLottery
                }

                if (lottery.bookingStart <= 0L ||
                    lottery.bookingEnd < lottery.bookingStart
                ) {
                    onFailure(
                        IllegalStateException("This lottery has an invalid booking window")
                    )
                    return@getLottery
                }


                checkEligibility(
                    lotteryId = lotteryId,
                    lotteryEntryId = lotteryEntryId,
                    entrantId = entrantId,
                    campsiteId = campsiteId,
                    onEligible = {
                        availability.getOpenDatesForLottery(
                            lottery,
                            campsiteId,
                            onSuccess = { openDates ->
                                reservations.getBookedDates(
                                    campsiteId,
                                    onSuccess = { bookedDates ->
                                        onSuccess(openDates - bookedDates)
                                    },
                                    onFailure = onFailure
                                )
                            },
                            onFailure = onFailure
                        )
                    },
                    onFailure = onFailure
                )
            },
            onFailure = onFailure
        )
    }

    /**
     * Records that the entrant accepted the rules and policies
     * for this lottery entry during the current app session.
     */
    fun confirmPolicies(lotteryEntryId: String) {
        policiesConfirmed.add(lotteryEntryId)
    }

    /**
     * Re-checks eligibility and selected dates before creating a reservation.
     *
     * A reservation is initially PENDING. It is not immediately ACTIVE.
     */
    fun createReservation(
        lotteryId: String,
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
            onFailure(
                IllegalStateException(
                    "Policies must be confirmed before reserving"
                )
            )
            return
        }

        if (!rules) {
            onFailure(
                IllegalStateException(
                    "You must accept the campsite rules and policies"
                )
            )
            return
        }

        if (dates.isEmpty()) {
            onFailure(
                IllegalArgumentException("Select at least one date")
            )
            return
        }

        lotteryController.getLottery(
            lotteryId,
            onSuccess = { lottery ->
                if (lottery == null) {
                    onFailure(
                        NoSuchElementException("Lottery not found")
                    )
                } else if (lottery.maxBookingDays < 1) {
                    onFailure(
                        IllegalStateException(
                            "This lottery has an invalid maximum booking length"
                        )
                    )
                } else {
                    // Verify the campsite and its actual owner.
                    availability.getCampsite(
                        campsiteId,
                        onSuccess = { campsite ->
                            if (campsite == null) {
                                onFailure(
                                    NoSuchElementException("Campsite not found")
                                )
                            } else if (campsite.ownerId != ownerId) {
                                onFailure(
                                    SecurityException(
                                        "The selected owner does not own this campsite"
                                    )
                                )
                            } else {
                                val sortedDates = dates.sorted()

                                if (sortedDates.size > lottery.maxBookingDays) {
                                    onFailure(
                                        IllegalArgumentException(
                                            "You can select at most ${lottery.maxBookingDays} consecutive days"
                                        )
                                    )
                                } else if (!areConsecutive(sortedDates)) {
                                    onFailure(
                                        IllegalArgumentException(
                                            "Your reservation dates must be consecutive"
                                        )
                                    )
                                } else {
                                    getAvailableDates(
                                        lotteryId = lotteryId,
                                        lotteryEntryId = lotteryEntryId,
                                        entrantId = entrantId,
                                        campsiteId = campsiteId,
                                        onSuccess = { available ->
                                            if (!available.containsAll(sortedDates)) {
                                                onFailure(
                                                    IllegalStateException(
                                                        "One or more selected dates are no longer available"
                                                    )
                                                )
                                            } else {
                                                val reservation = ReservationModel(
                                                    lotteryEntryId = lotteryEntryId,
                                                    campsiteId = campsiteId,
                                                    entrantId = entrantId,
                                                    ownerId = ownerId,
                                                    dates = sortedDates,
                                                    startDate = sortedDates.first(),
                                                    endDate = sortedDates.last(),
                                                    status = ReservationStatus.PENDING,
                                                    guests = guests,
                                                    refundRecorded = refundRecorded,
                                                    createdAt = createdAt,
                                                    rules = rules
                                                )

                                                reservations.save(
                                                    reservation,
                                                    onSuccess = {
                                                        notifications.notify(
                                                            NotificationIntent.ReservationConfirmed(
                                                                reservation
                                                            )
                                                        )
                                                        onSuccess(reservation)
                                                    },
                                                    onFailure = onFailure
                                                )
                                            }
                                        },
                                        onFailure = onFailure
                                    )
                                }
                            }
                        },
                        onFailure = onFailure
                    )
                }
            },
            onFailure = onFailure
        )
    }

    /**
     * Entrant cancels their own reservation.
     * Frees its dates and notifies the owner.
     */
    fun cancelReservation(
        reservationId: String,
        requesterId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        reservations.getByLotteryEntry(
            reservationId,
            onSuccess = { reservation ->
                when {
                    reservation == null ->
                        onFailure(NoSuchElementException("No reservation $reservationId"))

                    reservation.entrantId != requesterId ->
                        onFailure(
                            SecurityException("Only the entrant can cancel this reservation")
                        )

                    else -> reservations.markCancelled(
                        reservationId,
                        onSuccess = {
                            notifications.notify(
                                NotificationIntent.ReservationCancelled(reservation)
                            )
                            onSuccess()
                        },
                        onFailure = onFailure
                    )
                }
            },
            onFailure = onFailure
        )
    }

    /**
     * Legacy owner rejection operation.
     *
     * The current owner workflow is read-only for pending reservations.
     * Keep this method temporarily so existing callers do not break.
     */
    fun rejectReservation(
        reservationId: String,
        requesterId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        reservations.getByLotteryEntry(
            reservationId,
            onSuccess = { reservation ->
                when {
                    reservation == null ->
                        onFailure(NoSuchElementException("No reservation $reservationId"))

                    reservation.ownerId != requesterId ->
                        onFailure(
                            SecurityException(
                                "Only the campsite owner can reject this reservation"
                            )
                        )

                    else -> reservations.markRejected(
                        reservationId,
                        onSuccess = {
                            notifications.notify(
                                NotificationIntent.ReservationRejected(reservation)
                            )
                            onSuccess()
                        },
                        onFailure = onFailure
                    )
                }
            },
            onFailure = onFailure
        )
    }

    /**
     * Lists every reservation an entrant has made, regardless of status.
     */
    fun listReservations(
        entrantId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) = reservations.getForEntrant(entrantId, onSuccess, onFailure)

    /**
     * Lists an owner's reservations for one campsite by status.
     */
    fun listReservations(
        campsiteId: String,
        filter: ReservationFilter,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) = when (filter) {
        ReservationFilter.PENDING ->
            reservations.getPending(campsiteId, onSuccess, onFailure)

        ReservationFilter.ACTIVE ->
            reservations.getActive(campsiteId, onSuccess, onFailure)

        ReservationFilter.PAST ->
            reservations.getPast(campsiteId, onSuccess, onFailure)

        ReservationFilter.CANCELLED ->
            reservations.getCancelled(campsiteId, onSuccess, onFailure)
    }

    /**
     * Builds the CSV text for a confirmation or cancellation receipt.
     */
    fun generateReceiptCsv(reservation: ReservationModel): String {
        fun esc(v: String) = "\"" + v.replace("\"", "\"\"") + "\""

        val header =
            "reservationId,campsiteId,entrantId,ownerId,startDate,endDate,nights,status,refundRecorded,generatedOn"

        val row = listOf(
            reservation.lotteryEntryId,
            reservation.campsiteId,
            reservation.entrantId,
            reservation.ownerId,
            reservation.startDate,
            reservation.endDate,
            reservation.dates.size.toString(),
            reservation.status.name,
            reservation.refundRecorded.toString(),
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        ).joinToString(",") { esc(it) }

        return "$header\n$row"
    }
}
