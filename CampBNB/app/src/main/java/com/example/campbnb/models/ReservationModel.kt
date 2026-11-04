package com.example.campbnb.models

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.campbnb.database.Identifiable
import com.example.campbnb.database.readEngineFor
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

enum class ReservationStatus { ACTIVE, CANCELLED, REJECTED }

/**
 *
 *  A single reservation document, shared by the owner and entrant views.
 *
 *  Dates are stored as ISO strings (yyyy-MM-dd) because Firestore has no LocalDate type and
 *  ISO strings sort correctly, so range queries on [endDate] work. Every property has a default
 *  because Firestore's `toObjects` needs a no-argument constructor.
 *
 *  The document ID is the [lotteryEntryId], so a second reservation for the same entry
 *  is impossible.
 *
 * @property lotteryEntryId:
 * @property campsiteId:
 * @property entrantId:
 * @property ownerId:
 * @property guests:
 * @property dates:
 * @property startDate:
 * @property endDate:
 * @property status:
 * @property refundRecorded:
 * @property createdAt:
 * @property rules:
 */

data class ReservationModel(
    var lotteryEntryId: String = "",
    var campsiteId: String = "",
    var entrantId: String = "",
    var ownerId: String = "",
    var guests: Int,
    var dates: List<String> = emptyList(),   // ISO strings, e.g. "2026-07-14"
    var startDate: String = "",              // earliest date, for range queries
    var endDate: String = "",                // latest date, for range queries
    var status: ReservationStatus = ReservationStatus.ACTIVE,
    var refundRecorded: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    var rules: Boolean
) : Identifiable()

/**
 * The reservation store that [ReservationController] depends on.
 *
 * Persists reservations and serves every query the owner and entrant screens need.
 * Reads go through [QueryEngine]. Writes go straight to Firestore inside transactions so that
 * "one reservation per entry" and "no double-booked dates" hold even when two entrants
 * book at the same moment. It stores data and enforces storage rules; deciding who may book
 * is the controller's job.
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
 * @see ReservationController
 * @see QueryEngine
 */
class ReservationStore(private val db: FirebaseFirestore) {

    private val reservations = db.collection("Reservations")
    private val bookedDates = db.collection("BookedDates") // one doc per campsite+date
    private val engine = db.readEngineFor<ReservationModel>("Reservations")

    // ---------- Owner queries ----------

    @RequiresApi(Build.VERSION_CODES.O) // an annotation that tells the compiler and Android Studio: “this function or class only works on Android 8.0 (API 26) and above.”
    fun getActive(
        campsiteId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit,
        today: LocalDate = LocalDate.now()
    ) {
        engine.query()
            .whereEquals(ReservationModel::campsiteId, campsiteId)
            .whereEquals(ReservationModel::status, ReservationStatus.ACTIVE.name)
            .whereGreaterThan(ReservationModel::endDate, today.minusDays(1).toString()) // endDate >= today
            .orderBy(ReservationModel::endDate)
            .execute(onSuccess, onFailure)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getPast(
        campsiteId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit,
        today: LocalDate = LocalDate.now()
    ) {
        engine.query()
            .whereEquals(ReservationModel::campsiteId, campsiteId)
            .whereEquals(ReservationModel::status, ReservationStatus.ACTIVE.name)
            .whereLessThan(ReservationModel::endDate, today.toString())
            .orderBy(ReservationModel::endDate)
            .execute(onSuccess, onFailure)
    }

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

    @RequiresApi(Build.VERSION_CODES.O)
    fun getBookedDates(
        campsiteId: String,
        onSuccess: (Set<LocalDate>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        engine.query()
            .whereEquals(ReservationModel::campsiteId, campsiteId)
            .whereEquals(ReservationModel::status, ReservationStatus.ACTIVE.name)
            .execute(
                onSuccess = { list -> onSuccess(list.flatMap { it.dates }.map(LocalDate::parse).toSet()) },
                onFailure = onFailure
            )
    }

    // ---------- Entrant queries ----------

    fun getByLotteryEntry(
        lotteryEntryId: String,
        onSuccess: (ReservationModel?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        reservations.document(lotteryEntryId).get()
            .addOnSuccessListener { onSuccess(it.toObject(ReservationModel::class.java)) }
            .addOnFailureListener(onFailure)
    }

    fun hasReservation(
        lotteryEntryId: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        reservations.document(lotteryEntryId).get()
            .addOnSuccessListener { onSuccess(it.exists()) }
            .addOnFailureListener(onFailure)
    }

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
     * Fails (via [onFailure]) if the entry already has a reservation or any date is already booked.
     */
    fun save(
        record: ReservationModel,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val resRef = reservations.document(record.lotteryEntryId)
        val dateRefs = record.dates.map { bookedDates.document("${record.campsiteId}_$it") }

        db.runTransaction { tx ->
            // Firestore transactions require all reads before any writes.
            check(!tx.get(resRef).exists()) { "Entry already has a reservation" }
            dateRefs.forEach { check(!tx.get(it).exists()) { "One or more dates are already booked" } }

            tx.set(resRef, record)
            dateRefs.forEach {
                tx.set(it, mapOf("campsiteId" to record.campsiteId, "reservationId" to record.lotteryEntryId))
            }
        }.addOnSuccessListener { onSuccess() }.addOnFailureListener(onFailure)
    }

    fun markCancelled(reservationId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) =
        close(reservationId, ReservationStatus.CANCELLED, onSuccess, onFailure)

    fun markRejected(reservationId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) =
        close(reservationId, ReservationStatus.REJECTED, onSuccess, onFailure)

    private fun close(
        reservationId: String,
        newStatus: ReservationStatus,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val resRef = reservations.document(reservationId)

        db.runTransaction { tx ->
            val record = tx.get(resRef).toObject(ReservationModel::class.java)
                ?: throw NoSuchElementException("No reservation $reservationId")
            check(record.status == ReservationStatus.ACTIVE) { "Reservation is already ${record.status}" }

            tx.update(resRef, mapOf("status" to newStatus.name, "refundRecorded" to true))
            record.dates.forEach { tx.delete(bookedDates.document("${record.campsiteId}_$it")) }
        }.addOnSuccessListener { onSuccess() }.addOnFailureListener(onFailure)
    }
}