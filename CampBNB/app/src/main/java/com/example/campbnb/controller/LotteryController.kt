package com.example.campbnb.controller

import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.QueryEngine
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel

/**
 * Answers lottery questions for the rest of the app, reading data only through [QueryEngine].
 *
 * @property model The lottery state shown by the views.
 * @property repo The lottery repository.
 * @property lotteries Read access to lotteries, including their winners and booking windows.
 * @property clock Source of the current time in epoch milliseconds.
 */
class LotteryController(
    private val model: LotteryModel,
    private val repo: LotteryRepository,
    private val lotteries: QueryEngine<Lottery>,
    private val clock: () -> Long = System::currentTimeMillis
) {
    // E, D, A, F
    fun onBackMonitorClicked() {
        TODO()
    }

    /**
     * Checks whether an entrant won a lottery and that the lottery covers a given campsite.
     *
     * Returns false, not an error, if the lottery doesn't exist, hasn't been drawn, doesn't
     * include the campsite, or the entrant isn't a winner. This is the "if and only if I won
     * for that particular campsite" check the reservation flow depends on.
     *
     * @param lotteryId The lottery to check.
     * @param entrantId The entrant claiming the win.
     * @param campsiteId The campsite they want to reserve. Must be one of the lottery's campsites.
     * @param onSuccess Receives true only if the lottery is drawn, covers the campsite, and lists the entrant as a winner.
     * @param onFailure Receives any Firestore error.
     * @author Zino
     */
    fun checkIfWon(
        lotteryId: String,
        entrantId: String,
        campsiteId: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        lotteries.getById(lotteryId, onSuccess = { lottery ->
            onSuccess(
                lottery != null &&
                        lottery.drawn &&
                        campsiteId in lottery.campsiteIds &&
                        entrantId in lottery.winnerIds
            )
        }, onFailure = onFailure)
    }

    /**
     * Checks whether a lottery's booking window is open right now.
     *
     * The window is inclusive of both ends. A lottery that doesn't exist or hasn't been
     * drawn is treated as closed, and so is one whose window was never set (both times 0).
     *
     * @param lotteryId The lottery to check.
     * @param onSuccess Receives true if the current time falls inside the booking window.
     * @param onFailure Receives any Firestore error.
     */
    fun isBookingOpen(
        lotteryId: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        lotteries.getById(lotteryId, onSuccess = { lottery ->
            val now = clock()
            onSuccess(
                lottery != null &&
                        lottery.drawn &&
                        now >= lottery.bookingStart &&
                        now <= lottery.bookingEnd
            )
        }, onFailure = onFailure)
    }
}