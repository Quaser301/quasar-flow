package com.example.campbnb.controller

import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.QueryEngine
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.LotteryStatus
import com.example.campbnb.models.WaitingListEntry

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

    /** Finds the open lottery for a campsite and whether the user is on its waiting list (US 01.01.06). */
    fun loadEntrantStatus(campsiteId: String, userId: String) {
        repo.getAllLotteries(
            onSuccess = { allLotteries ->
                val open = findOpenLottery(allLotteries, campsiteId)
                if (open == null) {
                    model.setEntrantStatus(null, false, 0)
                } else {
                    loadWaitlist(open, userId)
                }
            },
            onFailure = { error -> model.setStatusMessage("Could not load lotteries: " + error.message) }
        )
    }

    /** The first lottery that includes this campsite and is open for entries, or null. */
    fun findOpenLottery(allLotteries: List<Lottery>, campsiteId: String): Lottery? {
        val now = clock()
        for (lottery in allLotteries) {
            if (lottery.campsiteIds.contains(campsiteId) && isRegistrationOpen(lottery, now)) {
                return lottery
            }
        }
        return null
    }

    /**
     * Puts the user on the waiting list if registration is open, there is room, and they
     * are not already on it (US 01.01.06, 01.02.01). The result is reported through the
     * model's status message.
     */
    fun join(lottery: Lottery, userId: String) {
        val now = clock()
        if (!isRegistrationOpen(lottery, now)) {
            model.setStatusMessage("Registration is closed")
            return
        }
        repo.getEntries(
            lottery.id,
            onSuccess = { entries ->
                if (containsUser(entries, userId)) {
                    model.setStatusMessage("You are already on the waiting list")
                } else if (isWaitlistFull(lottery, entries.size)) {
                    model.setStatusMessage("The waiting list is full")
                } else {
                    addToWaitlist(lottery, userId, now)
                }
            },
            onFailure = { error -> model.setStatusMessage("Could not join: " + error.message) }
        )
    }

    /** Takes the user off the waiting list (US 01.02.02). */
    fun leave(lottery: Lottery, userId: String) {
        repo.removeEntry(
            lottery.id,
            userId,
            onComplete = { result ->
                if (result.isSuccess) {
                    model.setStatusMessage("You left the waiting list")
                    loadWaitlist(lottery, userId)
                } else {
                    model.setStatusMessage("Could not leave the waiting list")
                }
            }
        )
    }

    private fun addToWaitlist(lottery: Lottery, userId: String, now: Long) {
        val entry = WaitingListEntry(lottery.id, userId, now)
        repo.addEntry(
            entry,
            onComplete = { result ->
                if (result.isSuccess) {
                    model.setStatusMessage("You joined the waiting list")
                    loadWaitlist(lottery, userId)
                } else {
                    model.setStatusMessage("Could not join the waiting list")
                }
            }
        )
    }

    private fun loadWaitlist(lottery: Lottery, userId: String) {
        repo.getEntries(
            lottery.id,
            onSuccess = { entries ->
                model.setEntrantStatus(lottery, containsUser(entries, userId), entries.size)
            },
            onFailure = { error -> model.setStatusMessage("Could not load waiting list: " + error.message) }
        )
    }

    private fun isRegistrationOpen(lottery: Lottery, now: Long): Boolean {
        if (lottery.drawn) {
            return false
        }
        return lottery.statusAt(now) == LotteryStatus.OPEN
    }

    private fun containsUser(entries: List<WaitingListEntry>, userId: String): Boolean {
        for (entry in entries) {
            if (entry.userId == userId) {
                return true
            }
        }
        return false
    }

    private fun isWaitlistFull(lottery: Lottery, size: Int): Boolean {
        val capacity = lottery.waitlistCapacity
        if (capacity == null) {
            return false
        }
        return size >= capacity
    }
    fun loadOwnerCampsites(ownerId: String, onResult: (List<Campsite>) -> Unit) {
        repo.loadCampsitesByOwner(
            ownerId = ownerId,
            onSuccess = { campsites -> onResult(campsites) },
            onFailure = { onResult(emptyList()) }
        )
    }
    fun saveLottery(lottery: Lottery, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        repo.save(lottery, onSuccess, onFailure)
    }
    fun loadOwnerLotteries(ownerId : String){
        repo.getLotteriesForOwner(ownerId,
            onSuccess = { lotteries -> model.setLotteries(lotteries) },
            onFailure = { error -> model.setStatusMessage("Could not load lotteries: " + error.message) }
        )
    }


}