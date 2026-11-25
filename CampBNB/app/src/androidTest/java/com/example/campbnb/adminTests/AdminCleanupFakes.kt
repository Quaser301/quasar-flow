package com.example.campbnb

import com.example.campbnb.data.CampsiteRemovalRepository
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.WaitingListEntry

/** A removal repository holding nothing; never touches Firebase. */
class EmptyRemovalRepository : CampsiteRemovalRepository() {
    override fun getActiveReservations(
        campsiteId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(emptyList())
    }

    override fun getActiveReservationsOfEntrant(
        entrantId: String,
        onSuccess: (List<ReservationModel>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(emptyList())
    }

    override fun cancelReservation(reservationId: String, onComplete: (Result<Unit>) -> Unit) {
        onComplete(Result.success(Unit))
    }

    override fun deleteLottery(lotteryId: String, onComplete: (Result<Exception?>) -> Unit) {
        onComplete(Result.success(null))
    }

    override fun saveLottery(lottery: Lottery, onComplete: (Result<Exception?>) -> Unit) {
        onComplete(Result.success(null))
    }
}

/** A lottery repository holding no lotteries and no waiting lists; never touches Firebase. */
class EmptyLotteryRepository : LotteryRepository() {
    override fun getAllLotteries(onSuccess: (List<Lottery>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(emptyList())
    }

    override fun getEntries(
        lotteryId: String,
        onSuccess: (List<WaitingListEntry>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(emptyList())
    }

    override fun getEntriesByUser(
        userId: String,
        onSuccess: (List<WaitingListEntry>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(emptyList())
    }

    override fun removeEntry(
        lotteryId: String,
        userId: String,
        onComplete: (Result<Exception?>) -> Unit
    ) {
        onComplete(Result.success(null))
    }
}