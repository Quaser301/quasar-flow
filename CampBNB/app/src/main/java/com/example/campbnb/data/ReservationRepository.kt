
package com.example.campbnb.data

import com.example.campbnb.database.QueryEngine
import com.example.campbnb.database.WriteEngine
import com.example.campbnb.models.BookedDate
import com.example.campbnb.models.DateUtils
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus

/**
 * Persists reservations and serves the queries needed by owner and entrant screens.
 *
 * Reservation document IDs are the lottery entry IDs, allowing one reservation per entry.
 *
 * BookedDates contains one document per campsite and date:
 * campsiteId_yyyy-MM-dd
 *
 * Both PENDING and ACTIVE reservations hold their dates.
 */
class ReservationRepository(
    private val engine: QueryEngine<ReservationModel>,
    private val reservationWriter: WriteEngine<ReservationModel>,
    private val bookedDateWriter: WriteEngine<BookedDate>
) {

    // ---------- Owner queries ----------

    /**
     * Gets reservations that have started and have not ended.
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
     * Gets pending reservations whose dates have not started yet.
     *
     * The controller/status-update process must promote reservations to ACTIVE
     * when their start date arrives.
     */
    fun getPending(
        campsiteId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        engine.query()
            .whereEquals(ReservationModel::campsiteId, campsiteId)
            .whereEquals(ReservationModel::status, ReservationStatus.PENDING.name)
            .orderBy(ReservationModel::startDate)
            .execute(onSuccess, onFailure)
    }

    /**
     * Gets active reservations whose end date has passed.
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
     * Gets cancelled and rejected reservations.
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
                listOf(
                    ReservationStatus.CANCELLED.name,
                    ReservationStatus.REJECTED.name
                )
            )
            .execute(onSuccess, onFailure)
    }

    /**
     * Gets every date held by a pending or active reservation.
     */
    fun getBookedDates(
        campsiteId: String,
        onSuccess: (Set<String>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        engine.query()
            .whereEquals(ReservationModel::campsiteId, campsiteId)
            .whereIn(
                ReservationModel::status,
                listOf(
                    ReservationStatus.PENDING.name,
                    ReservationStatus.ACTIVE.name
                )
            )
            .execute(
                onSuccess = { reservations ->
                    onSuccess(reservations.flatMap { it.dates }.toSet())
                },
                onFailure = onFailure
            )
    }

    /**
     * Gets all reservations across an owner's campsites.
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
     * Gets the reservation belonging to a lottery entry.
     */
    fun getByLotteryEntry(
        lotteryEntryId: String,
        onSuccess: (ReservationModel?) -> Unit,
        onFailure: (Exception) -> Unit
    ) = engine.getById(lotteryEntryId, onSuccess, onFailure)

    /**
     * Checks whether a lottery entry already has a reservation.
     */
    fun CheckIfReserved(
        lotteryEntryId: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) = engine.exists(lotteryEntryId, onSuccess, onFailure)

    /**
     * Gets all reservations belonging to an entrant.
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
     * Claims the reservation dates and then saves the reservation.
     *
     * New reservations are always saved as PENDING.
     */
    fun save(
        reservation: ReservationModel,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        reservation.id = reservation.lotteryEntryId
        reservation.status = ReservationStatus.PENDING

        CheckIfReserved(
            reservation.lotteryEntryId,
            onSuccess = { exists ->
                if (exists) {
                    onFailure(
                        IllegalStateException("Entry already has a reservation")
                    )
                } else {
                    claimDates(
                        reservation = reservation,
                        index = 0,
                        onClaimed = {
                            reservationWriter.save(reservation) { result ->
                                result.onSuccess {
                                    onSuccess()
                                }.onFailure { error ->
                                    // If saving the reservation fails,
                                    // release the dates claimed by this attempt.
                                    releaseDates(
                                        reservation.campsiteId,
                                        reservation.dates
                                    ) {
                                        onFailure(error.asException())
                                    }
                                }
                            }
                        },
                        onConflict = onFailure
                    )
                }
            },
            onFailure = onFailure
        )
    }

    /**
     * Cancels a pending or active reservation and releases its dates.
     */
    fun markCancelled(
        reservationId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) = close(
        reservationId,
        ReservationStatus.CANCELLED,
        onSuccess,
        onFailure
    )

    /**
     * Retained for compatibility with existing callers.
     *
     * The intended owner workflow is read-only for pending reservations;
     * owners should not use this to approve or reject a reservation.
     */
    fun markRejected(
        reservationId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) = close(
        reservationId,
        ReservationStatus.REJECTED,
        onSuccess,
        onFailure
    )

    // ---------- Helpers ----------

    /**
     * Claims dates one at a time.
     * If a claim fails, releases the dates already claimed by this attempt.
     */
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

        val claim = BookedDate(
            reservation.campsiteId,
            reservation.lotteryEntryId
        ).also {
            it.id = bookedKey(
                reservation.campsiteId,
                reservation.dates[index]
            )
        }

        bookedDateWriter.save(claim) { result ->
            result.onSuccess {
                claimDates(
                    reservation,
                    index + 1,
                    onClaimed,
                    onConflict
                )
            }.onFailure {
                releaseDates(
                    reservation.campsiteId,
                    reservation.dates.take(index)
                ) {
                    onConflict(
                        IllegalStateException(
                            "One or more dates are already booked"
                        )
                    )
                }
            }
        }
    }

    /**
     * Deletes booked-date documents one at a time.
     */
    private fun releaseDates(
        campsiteId: String,
        dates: List<String>,
        onDone: () -> Unit
    ) {
        if (dates.isEmpty()) {
            onDone()
            return
        }

        bookedDateWriter.delete(
            bookedKey(campsiteId, dates.first())
        ) {
            releaseDates(campsiteId, dates.drop(1), onDone)
        }
    }

    /**
     * Changes the reservation status and releases its dates.
     */
    private fun close(
        reservationId: String,
        newStatus: ReservationStatus,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        getByLotteryEntry(
            reservationId,
            onSuccess = { reservation ->
                when {
                    reservation == null -> {
                        onFailure(
                            NoSuchElementException(
                                "No reservation $reservationId"
                            )
                        )
                    }

                    reservation.status != ReservationStatus.PENDING &&
                            reservation.status != ReservationStatus.ACTIVE -> {
                        onFailure(
                            IllegalStateException(
                                "Reservation is already ${reservation.status}"
                            )
                        )
                    }

                    else -> {
                        val changes = mapOf(
                            "status" to newStatus.name,
                            "refundRecorded" to true
                        )

                        reservationWriter.update(reservationId, changes) { result ->
                            result.onSuccess {
                                releaseDates(
                                    reservation.campsiteId,
                                    reservation.dates
                                ) {
                                    onSuccess()
                                }
                            }.onFailure { error ->
                                onFailure(error.asException())
                            }
                        }
                    }
                }
            },
            onFailure = onFailure
        )
    }

    private fun bookedKey(campsiteId: String, date: String): String =
        "${campsiteId}_$date"

    private fun Throwable.asException(): Exception =
        this as? Exception ?: Exception(this)

    fun activateDueReservations(
        today: String = DateUtils.today(),
        onComplete: (Result<Int>) -> Unit
    ) {
        engine.query()
            .whereEquals(
                ReservationModel::status,
                ReservationStatus.PENDING.name
            )
            .execute(
                onSuccess = { pendingReservations ->
                    val dueReservations = pendingReservations.filter {
                        it.startDate.isNotBlank() && it.startDate <= today
                    }

                    updateDueReservationStatuses(
                        reservations = dueReservations,
                        index = 0,
                        updatedCount = 0,
                        onComplete = onComplete
                    )
                },
                onFailure = { exception ->
                    onComplete(Result.failure(exception))
                }
            )
    }

    private fun updateDueReservationStatuses(
        reservations: List<ReservationModel>,
        index: Int,
        updatedCount: Int,
        onComplete: (Result<Int>) -> Unit
    ) {
        if (index >= reservations.size) {
            onComplete(Result.success(updatedCount))
            return
        }

        val reservation = reservations[index]
        val today = DateUtils.today()

        val newStatus = when (reservation.status) {
            ReservationStatus.PENDING -> {
                if (reservation.startDate <= today) {
                    ReservationStatus.ACTIVE
                } else {
                    null
                }
            }

            ReservationStatus.ACTIVE -> {
                if (reservation.endDate < today) {
                    ReservationStatus.PAST
                } else {
                    null
                }
            }

            else -> null
        }

        // No transition is due; move to the next reservation.
        if (newStatus == null) {
            updateDueReservationStatuses(
                reservations = reservations,
                index = index + 1,
                updatedCount = updatedCount,
                onComplete = onComplete
            )
            return
        }

        reservationWriter.update(
            id = reservation.id,
            fieldMap = mapOf(
                "status" to newStatus.name
            )
        ) { result ->
            if (result.isSuccess) {
                updateDueReservationStatuses(
                    reservations = reservations,
                    index = index + 1,
                    updatedCount = updatedCount + 1,
                    onComplete = onComplete
                )
            } else {
                onComplete(
                    Result.failure(
                        result.exceptionOrNull()
                            ?: Exception("Could not update reservation status")
                    )
                )
            }
        }
    }
}
