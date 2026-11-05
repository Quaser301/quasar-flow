package com.example.campbnb.data

import com.example.campbnb.database.QueryEngine
import com.example.campbnb.database.WriteEngine
import com.example.campbnb.models.BookedDate
import com.example.campbnb.models.DateUtils
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus

/**
 * Persists reservations and serves every query the owner and entrant screens need.
 *
 * This is the store that [com.example.campbnb.controller.ReservationController] depends on.
 * Reads go through [QueryEngine] and writes through [WriteEngine]; nothing here touches
 * Firestore directly. It stores data and enforces storage rules; deciding who may book is
 * the controller's job.
 *
 * Dates are ISO strings (yyyy-MM-dd), which sort correctly as text, so range queries on
 * `endDate` work without a date library.
 *
 * Document IDs in the `Reservations` collection are the lottery entry ID, which means one
 * reservation per entry. The `BookedDates` collection holds one document per campsite and
 * date (`campsiteId_yyyy-MM-dd`). Firestore security rules allow creating such a document
 * but not overwriting it, so two people can never claim the same date. See [save].
 *
 * Owner queries:
 *  - [getActive]: active reservations for a campsite that haven't ended yet.
 *  - [getPast]: active reservations for a campsite whose end date has passed.
 *  - [getCancelled]: cancelled and rejected reservations for a campsite.
 *  - [getBookedDates]: every date currently booked at a campsite.
 *  - [getForOwner]: all reservations across an owner's campsites.
 *
 * Entrant queries:
 *  - [getByLotteryEntry]: the reservation for a lottery entry, or null.
 *  - [hasReservation]: whether an entry already has one (drives the "already reserved" badge).
 *  - [getForEntrant]: all of an entrant's reservations.
 *
 * Commands:
 *  - [save]: books the dates, then creates the reservation.
 *  - [markCancelled]: sets a reservation to cancelled, frees its dates, records the refund.
 *  - [markRejected]: sets a reservation to rejected, frees its dates, records the refund.
 *
 * @property engine Reads on the `Reservations` collection.
 * @property reservationWriter Writes on the `Reservations` collection.
 * @property bookedDateWriter Writes on the `BookedDates` collection.
 * @author Zino
 * @see ReservationModel
 * @see QueryEngine
 * @see WriteEngine
 */
class ReservationRepository(
    private val engine: QueryEngine<ReservationModel>,
    private val reservationWriter: WriteEngine<ReservationModel>,
    private val bookedDateWriter: WriteEngine<BookedDate>
) {

    // ---------- Owner queries ----------

    /**
     * Gets active reservations for a campsite whose end date is [today] or later.
     *
     * @param campsiteId The campsite to look up.
     * @param onSuccess Receives the reservations, soonest ending first.
     * @param onFailure Receives any Firestore error.
     * @param today Today as yyyy-MM-dd. Defaults to the device's current date.
     */
    fun getActive(
        campsiteId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit,
        today: String = DateUtils.today()
    ) {
        engine.query()
            .whereEquals(ReservationModel::campsiteId, campsiteId)
            .whereEquals(ReservationModel::status, ReservationStatus.ACTIVE.name)
            .whereGreaterThanOrEqual(ReservationModel::endDate, today)
            .orderBy(ReservationModel::endDate)
            .execute(onSuccess, onFailure)
    }

    /**
     * Gets active reservations for a campsite whose end date is before [today].
     *
     * @param campsiteId The campsite to look up.
     * @param onSuccess Receives the reservations, oldest first.
     * @param onFailure Receives any Firestore error.
     * @param today Today as yyyy-MM-dd. Defaults to the device's current date.
     */
    fun getPast(
        campsiteId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit,
        today: String = DateUtils.today()
    ) {
        engine.query()
            .whereEquals(ReservationModel::campsiteId, campsiteId)
            .whereEquals(ReservationModel::status, ReservationStatus.ACTIVE.name)
            .whereLessThan(ReservationModel::endDate, today)
            .orderBy(ReservationModel::endDate)
            .execute(onSuccess, onFailure)
    }

    /**
     * Gets cancelled and rejected reservations for a campsite.
     *
     * @param campsiteId The campsite to look up.
     * @param onSuccess Receives the reservations.
     * @param onFailure Receives any Firestore error.
     */
    fun getCancelled(
        campsiteId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        engine.query()
            .whereEquals(ReservationModel::campsiteId, campsiteId)
            .whereIn(
                ReservationModel::status,
                listOf(ReservationStatus.CANCELLED.name, ReservationStatus.REJECTED.name)
            )
            .execute(onSuccess, onFailure)
    }

    /**
     * Gets every date currently booked by an active reservation at a campsite.
     *
     * @param campsiteId The campsite to look up.
     * @param onSuccess Receives the booked dates as yyyy-MM-dd strings.
     * @param onFailure Receives any Firestore error.
     */
    fun getBookedDates(
        campsiteId: String,
        onSuccess: (Set<String>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        engine.query()
            .whereEquals(ReservationModel::campsiteId, campsiteId)
            .whereEquals(ReservationModel::status, ReservationStatus.ACTIVE.name)
            .execute(
                onSuccess = { list -> onSuccess(list.flatMap { it.dates }.toSet()) },
                onFailure = onFailure
            )
    }

    /**
     * Gets every reservation across all of an owner's campsites, any status.
     *
     * @param ownerId The owner to look up.
     * @param onSuccess Receives the reservations, earliest start date first.
     * @param onFailure Receives any Firestore error.
     */
    fun getForOwner(
        ownerId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        engine.query()
            .whereEquals(ReservationModel::ownerId, ownerId)
            .orderBy(ReservationModel::startDate)
            .execute(onSuccess, onFailure)
    }

    // ---------- Entrant queries ----------

    /**
     * Gets the reservation for a lottery entry.
     *
     * @param lotteryEntryId The entry to look up (also the reservation's document ID).
     * @param onSuccess Receives the reservation, or null if the entrant hasn't reserved yet.
     * @param onFailure Receives any Firestore error.
     */
    fun getByLotteryEntry(
        lotteryEntryId: String,
        onSuccess: (ReservationModel?) -> Unit,
        onFailure: (Exception) -> Unit
    ) = engine.getById(lotteryEntryId, onSuccess, onFailure)

    /**
     * Checks whether a lottery entry already has a reservation.
     *
     * @param lotteryEntryId The entry to check.
     * @param onSuccess Receives true if a reservation exists.
     * @param onFailure Receives any Firestore error.
     */
    fun CheckIfReserved(
        lotteryEntryId: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) = engine.exists(lotteryEntryId, onSuccess, onFailure)

    /**
     * Gets every reservation an entrant has made, any status.
     *
     * @param entrantId The entrant to look up.
     * @param onSuccess Receives the reservations, earliest start date first.
     * @param onFailure Receives any Firestore error.
     */
    fun getForEntrant(
        entrantId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        engine.query()
            .whereEquals(ReservationModel::entrantId, entrantId)
            .orderBy(ReservationModel::startDate)
            .execute(onSuccess, onFailure)
    }

    // ---------- Commands ----------

    /**
     * Books the reservation's dates one by one, then creates the reservation.
     *
     * Each date is claimed by creating its `BookedDates` document. The security rules reject
     * a second claim on the same date, so a conflict surfaces as a failed write. If any date
     * fails, the dates already claimed are released and nothing else is written.
     *
     * @param reservation The reservation to store. Its `id` is set to its `lotteryEntryId`.
     * @param onSuccess Called once the dates are booked and the reservation is saved.
     * @param onFailure Receives the failure. A failed date claim is reported as
     * "One or more dates are already booked", so callers should refresh the available dates.
     */
    fun save(
        reservation: ReservationModel,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        reservation.id = reservation.lotteryEntryId

        CheckIfReserved(reservation.lotteryEntryId, onSuccess = { exists ->
            if (exists) {
                onFailure(IllegalStateException("Entry already has a reservation"))
            } else {
                claimDates(reservation, index = 0, onClaimed = {
                    reservationWriter.save(reservation) { result ->
                        result.onSuccess { onSuccess() }
                            .onFailure { e ->
                                // Couldn't save the reservation: give the dates back.
                                releaseDates(reservation.campsiteId, reservation.dates) {
                                    onFailure(e.asException())
                                }
                            }
                    }
                }, onConflict = onFailure)
            }
        }, onFailure = onFailure)
    }

    /**
     * Sets a reservation to cancelled, frees its dates, and records the refund.
     *
     * @param reservationId The reservation's ID (the lottery entry ID).
     * @param onSuccess Called once the change is written and the dates are freed.
     * @param onFailure Receives the failure, including "not found" and "already closed".
     */
    fun markCancelled(reservationId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) =
        close(reservationId, ReservationStatus.CANCELLED, onSuccess, onFailure)

    /**
     * Sets a reservation to rejected, frees its dates, and records the refund.
     *
     * @param reservationId The reservation's ID (the lottery entry ID).
     * @param onSuccess Called once the change is written and the dates are freed.
     * @param onFailure Receives the failure, including "not found" and "already closed".
     */
    fun markRejected(reservationId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) =
        close(reservationId, ReservationStatus.REJECTED, onSuccess, onFailure)

    // ---------- Helpers ----------

    /** Claims `dates[index]` and everything after it, one at a time. Rolls back on a conflict. */
    private fun claimDates(
        reservation: ReservationModel,
        index: Int,
        onClaimed: () -> Unit,
        onConflict: (Exception) -> Unit
    ) {
        if (index == reservation.dates.size) {
            onClaimed()
            return
        }

        val claim = BookedDate(reservation.campsiteId, reservation.lotteryEntryId).also {
            it.id = bookedKey(reservation.campsiteId, reservation.dates[index])
        }
        bookedDateWriter.save(claim) { result ->
            result.onSuccess {
                claimDates(reservation, index + 1, onClaimed, onConflict)
            }.onFailure {
                // Release only the dates this call already claimed, then report the conflict.
                releaseDates(reservation.campsiteId, reservation.dates.take(index)) {
                    onConflict(IllegalStateException("One or more dates are already booked"))
                }
            }
        }
    }

    /** Deletes the booked-date documents for [dates], one at a time. Carries on if one fails. */
    private fun releaseDates(campsiteId: String, dates: List<String>, onDone: () -> Unit) {
        if (dates.isEmpty()) {
            onDone()
            return
        }
        bookedDateWriter.delete(bookedKey(campsiteId, dates.first())) {
            releaseDates(campsiteId, dates.drop(1), onDone)
        }
    }

    private fun close(
        reservationId: String,
        newStatus: ReservationStatus,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        getByLotteryEntry(reservationId, onSuccess = { reservation ->
            when {
                reservation == null ->
                    onFailure(NoSuchElementException("No reservation $reservationId"))
                reservation.status != ReservationStatus.ACTIVE ->
                    onFailure(IllegalStateException("Reservation is already ${reservation.status}"))
                else -> {
                    // Status first: if freeing the dates fails, the reservation is still closed.
                    val changes = mapOf("status" to newStatus.name, "refundRecorded" to true)
                    reservationWriter.update(reservationId, changes) { result ->
                        result.onSuccess {
                            releaseDates(reservation.campsiteId, reservation.dates) { onSuccess() }
                        }.onFailure { e -> onFailure(e.asException()) }
                    }
                }
            }
        }, onFailure = onFailure)
    }

    private fun bookedKey(campsiteId: String, date: String) = "${campsiteId}_$date"

    private fun Throwable.asException(): Exception = this as? Exception ?: Exception(this)
}