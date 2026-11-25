package com.example.campbnb.data

import com.example.campbnb.database.Database
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.ReservationModel

/**
 * Boundary to Firestore for the clean-up that follows an admin deleting a campsite
 * (US 03.01.01): cancelling the campsite's reservations and deleting or updating the
 * lotteries that include it. It wraps existing repositories and engines rather than
 * changing them. Methods are 'open' so tests can override them with a fake.
 */
open class CampsiteRemovalRepository {

    // Created on first use so that building this class never touches Firebase.
    private val reservations: ReservationRepository by lazy {
        ReservationRepository(
            Database.Reservations.readEngine(),
            Database.Reservations.writeEngine(),
            Database.BookedDates.writeEngine()
        )
    }

    /** Reads the campsite's active reservations that have not ended yet. */
    open fun getActiveReservations(
        campsiteId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        reservations.getActive(campsiteId, onSuccess, onFailure)
    }

    /** Cancels one reservation (its id is the lottery entry id), which frees its dates. */
    open fun cancelReservation(reservationId: String, onComplete: (Result<Unit>) -> Unit) {
        reservations.markCancelled(
            reservationId,
            onSuccess = { onComplete(Result.success(Unit)) },
            onFailure = { error -> onComplete(Result.failure(error)) }
        )
    }

    /** Deletes a lottery document. */
    open fun deleteLottery(lotteryId: String, onComplete: (Result<Exception?>) -> Unit) {
        Database.Lotteries.writeEngine().delete(lotteryId, onComplete)
    }

    /** Saves a lottery; the lottery's id must already be set so it replaces the old one. */
    open fun saveLottery(lottery: Lottery, onComplete: (Result<Exception?>) -> Unit) {
        Database.Lotteries.writeEngine().save(lottery, onComplete)
    }
}