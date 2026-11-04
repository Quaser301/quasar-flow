package com.example.campbnb.data

import com.example.campbnb.database.readEngineFor
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists reservations and serves every query the owner and entrant screens need.
 *
 * This is the store that [com.example.campbnb.controller.ReservationController] depends on.
 * Reads go through [QueryEngine]. Writes go straight to Firestore inside transactions, so
 * "one reservation per entry" and "no double-booked dates" hold even when two entrants
 * book at the same moment. It stores data and enforces storage rules; deciding who may
 * book is the controller's job.
 *
 * Dates are ISO strings (yyyy-MM-dd). They sort correctly as text, so range queries on
 * `endDate` work without any date library.
 *
 * Document IDs in the `Reservations` collection are the lottery entry ID, which enforces one
 * reservation per entry. The `BookedDates` collection holds one document per campsite and
 * date (`campsiteId_yyyy-MM-dd`), so a transaction can claim dates atomically.
 *
 * Owner queries:
 *  - [getActive]: active reservations for a campsite that haven't ended yet.
 *  - [getPast]: active reservations for a campsite whose end date has passed.
 *  - [getCancelled]: cancelled and rejected reservations for a campsite.
 *  - [getBookedDates]: every date currently booked at a campsite.
 *
 * Entrant queries:
 *  - [getByLotteryEntry]: the reservation for a lottery entry, or null.
 *  - [hasReservation]: whether an entry already has one (drives the "already reserved" badge).
 *  - [getForEntrant]: all of an entrant's reservations.
 *
 * Commands:
 *  - [save]: creates the reservation and books its dates in one transaction.
 *  - [markCancelled]: sets a reservation to cancelled, frees its dates, records the refund.
 *  - [markRejected]: sets a reservation to rejected, frees its dates, records the refund.
 *
 * @property db The Firestore instance, used for writes and to build the read engine.
 * @author Zino
 * @see ReservationModel
 * @see QueryEngine
 */
class ReservationRepository(private val db: FirebaseFirestore) {

    private val reservations = db.collection("Reservations")
    private val bookedDates = db.collection("BookedDates")
    private val engine = db.readEngineFor<ReservationModel>("Reservations")

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
        today: String = todayString()
    ) {
        engine.query()
            .whereEquals(ReservationModel::campsiteId, campsiteId)
            .whereEquals(ReservationModel::status, ReservationStatus.ACTIVE.name)
            .whereGreaterThan(ReservationModel::endDate, today) // might need a whereGreaterThanOrEqual function
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
        today: String = todayString()
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
    ) {
        reservations.document(lotteryEntryId).get()
            .addOnSuccessListener { onSuccess(it.toObject(ReservationModel::class.java)) }
            .addOnFailureListener(onFailure)
    }

    /**
     * Checks whether a lottery entry already has a reservation.
     *
     * @param lotteryEntryId The entry to check.
     * @param onSuccess Receives true if a reservation exists.
     * @param onFailure Receives any Firestore error.
     */
    fun hasReservation(
        lotteryEntryId: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        reservations.document(lotteryEntryId).get()
            .addOnSuccessListener { onSuccess(it.exists()) }
            .addOnFailureListener(onFailure)
    }

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
     * Creates the reservation and books each of its dates atomically.
     *
     * Fails if the entry already has a reservation or any date is already booked, in which
     * case nothing is written.
     *
     * @param reservation The reservation to store. Its `lotteryEntryId` becomes the document ID.
     * @param onSuccess Called once everything is written.
     * @param onFailure Receives the failure, including conflicts.
     */
    fun save(
        reservation: ReservationModel,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val resRef = reservations.document(reservation.lotteryEntryId)
        val dateRefs = reservation.dates.map { bookedDates.document("${reservation.campsiteId}_$it") }

        db.runTransaction { tx ->
            // Firestore transactions require all reads before any writes.
            check(!tx.get(resRef).exists()) { "Entry already has a reservation" }
            dateRefs.forEach { check(!tx.get(it).exists()) { "One or more dates are already booked" } }

            tx.set(resRef, reservation)
            dateRefs.forEach {
                tx.set(it, mapOf(
                    "campsiteId" to reservation.campsiteId,
                    "reservationId" to reservation.lotteryEntryId
                ))
            }
        }.addOnSuccessListener { onSuccess() }.addOnFailureListener(onFailure)
    }

    /**
     * Sets a reservation to cancelled, frees its dates, and records the refund.
     *
     * @param reservationId The reservation's ID (the lottery entry ID).
     * @param onSuccess Called once the change is written.
     * @param onFailure Receives the failure, including "not found" and "already closed".
     */
    fun markCancelled(reservationId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) =
        close(reservationId, ReservationStatus.CANCELLED, onSuccess, onFailure)

    /**
     * Sets a reservation to rejected, frees its dates, and records the refund.
     *
     * @param reservationId The reservation's ID (the lottery entry ID).
     * @param onSuccess Called once the change is written.
     * @param onFailure Receives the failure, including "not found" and "already closed".
     */
    fun markRejected(reservationId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) =
        close(reservationId, ReservationStatus.REJECTED, onSuccess, onFailure)

    // ---------- Helpers ----------

    private fun close(
        reservationId: String,
        newStatus: ReservationStatus,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val resRef = reservations.document(reservationId)

        db.runTransaction { tx ->
            val reservation = tx.get(resRef).toObject(ReservationModel::class.java)
                ?: throw NoSuchElementException("No reservation $reservationId")
            check(reservation.status == ReservationStatus.ACTIVE) {
                "Reservation is already ${reservation.status}"
            }

            tx.update(resRef, mapOf("status" to newStatus.name, "refundRecorded" to true))
            reservation.dates.forEach { tx.delete(bookedDates.document("${reservation.campsiteId}_$it")) }
        }.addOnSuccessListener { onSuccess() }.addOnFailureListener(onFailure)
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}