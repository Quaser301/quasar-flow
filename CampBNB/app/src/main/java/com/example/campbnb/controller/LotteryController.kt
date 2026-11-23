package com.example.campbnb.controller

import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.QueryEngine
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.DateUtils
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.LotteryStatus
import com.example.campbnb.models.NotificationIntent
import com.example.campbnb.models.WaitingListEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val DAY_MILLIS = 24L * 60L * 60L * 1000L

/**
 * Answers lottery questions for the rest of the app, reading data only through [QueryEngine].
 *
 * @property model The lottery state shown by the views.
 * @property repo The lottery repository.
 * @property lotteries Read access to lotteries, including their winners and booking windows.
 * @property notifications Reports invitations to join a lottery, to whoever is concerned.
 * @property clock Source of the current time in epoch milliseconds.
 */
class LotteryController(
    private val model: LotteryModel,
    private val repo: LotteryRepository,
    private val lotteries: QueryEngine<Lottery>,
    private val notifications: NotificationController,
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
        if (userId.isBlank()) {
            model.setStatusMessage("Please sign in before joining a lottery")
            return
        }
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
        if (userId.isBlank()) {
            model.setStatusMessage("Please sign in before leaving a lottery")
            return
        }
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
    /**
     * Puts the lotteries an owner may see into the model: the ones they created plus the
     * ones they joined by accepting an invitation (US 02.04.08). The boundary reads every
     * lottery and this filters by [Lottery.visibleTo], because Firestore cannot express
     * "created by or joined by" in one query through [QueryEngine].
     *
     * @param ownerId The owner whose lotteries view is loaded.
     */
    fun loadOwnerLotteries(ownerId: String) {
        repo.getAllLotteries(
            onSuccess = { all -> model.setLotteries(all.filter { it.visibleTo(ownerId) }) },
            onFailure = { error -> model.setStatusMessage("Could not load lotteries: " + error.message) }
        )
    }

    /**
     * Saves a lottery the owner created or updated, and when it names owners to invite,
     * reports one invitation event once the save succeeded (US 02.04.08): an invitation is
     * only for a lottery that exists, so a failed save reports nothing. Updating a lottery
     * reports its pending invitations again; the records carry ids naming the lottery and
     * the invited owner, so re-reporting stores one record per invited owner rather than
     * copies.
     *
     * @param lottery The lottery to save, with its invited owners listed.
     * @param onSuccess Called once the lottery is stored.
     * @param onFailure Receives the store's error.
     */
    fun saveLottery(lottery: Lottery, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        repo.save(
            lottery,
            onSuccess = {
                notifications.notify(NotificationIntent.LotteryInvited(lottery))
                onSuccess()
            },
            onFailure = onFailure
        )
    }

    /**
     * An invited owner accepts and joins the lottery (US 02.04.08): they move from invited
     * to joining, and the lottery's parameters are not touched, so dates, winner count, and
     * waiting list capacity stay as the owner set them. The result is reported through the
     * model's status message, as joining a waiting list is, and handed to the optional
     * completion callbacks (US 02.04.08): exactly one of [onSuccess] and [onFailure] runs,
     * including on the refusals below, so a caller's next step waits for the store and
     * never races it.
     *
     * @param lottery The lottery the invitation belongs to.
     * @param userId The invited owner accepting.
     * @param onSuccess Called once the joined lottery is stored.
     * @param onFailure Receives the store's error, or the reason for a refusal.
     */
    fun acceptInvitation(
        lottery: Lottery,
        userId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (userId.isBlank()) {
            val reason = "Please sign in before accepting an invitation"
            model.setStatusMessage(reason)
            onFailure(IllegalArgumentException(reason))
            return
        }
        if (userId !in lottery.invitedOwnerIds) {
            val reason = "This lottery does not invite you"
            model.setStatusMessage(reason)
            onFailure(IllegalArgumentException(reason))
            return
        }
        repo.save(
            lottery.withInvitationAcceptedBy(userId),
            onSuccess = {
                model.setStatusMessage("You joined this lottery")
                onSuccess()
            },
            onFailure = { error ->
                model.setStatusMessage("Could not join the lottery: " + error.message)
                onFailure(error)
            }
        )
    }

    /**
     * Saves the campsite list as a joining owner edited it (US 02.04.08): the campsites of
     * other owners are kept, the campsites of [editorId] become exactly
     * [selectedCampsiteIds], and everything else about the lottery, its dates, winner
     * settings, invited and joining owners, is copied unchanged, which is what keeps a
     * joining owner from changing the parameters. A campsite the editor does not own is
     * refused, and so is an edit that would leave the lottery with no campsite.
     *
     * @param editorId The joining owner whose selection is saved.
     * @param lottery The lottery as it is now.
     * @param selectedCampsiteIds The editor's campsites the edited lottery will cover.
     * @param onSuccess Called once the edited lottery is stored.
     * @param onFailure Receives the store's error or a refusal.
     */
    fun saveCampsitesEditedBy(
        editorId: String,
        lottery: Lottery,
        selectedCampsiteIds: List<String>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        repo.loadCampsitesByOwner(
            ownerId = editorId,
            onSuccess = { ownedCampsites ->
                val ownedIds = ownedCampsites.map { it.id }.toSet()
                if (!selectedCampsiteIds.all { it in ownedIds }) {
                    onFailure(
                        IllegalArgumentException("Only your own campsites can be added to a lottery")
                    )
                } else {
                    val keptByOthers = lottery.campsiteIds.filterNot { it in ownedIds }
                    val editedCampsiteIds = (keptByOthers + selectedCampsiteIds).distinct()
                    if (editedCampsiteIds.isEmpty()) {
                        onFailure(
                            IllegalArgumentException("A lottery must cover at least one campsite")
                        )
                    } else {
                        repo.save(
                            lottery.withCampsiteIds(editedCampsiteIds),
                            onSuccess = { onSuccess() },
                            onFailure = onFailure
                        )
                    }
                }
            },
            onFailure = onFailure
        )
    }

    /**
     * Force-closes and draws a lottery right now (feature 1: the admin of a lottery can
     * force it to close so the whole lifecycle can be tested): the draw selects up to
     * [numberOfWinners] of the current entrants in arbitrary order, and the booking
     * window is moved onto today, so [LotteryStatus.BOOKING] is what the SAME clock
     * reading yields at the boundaries of models.md. The entrants hear the result —
     * winners and losers both, as one [NotificationIntent.LotteryDrawn] — only once the
     * closed record is stored; [onDone] holds true only for a stored record, and a read
     * or save failure reports false and draws nothing.
     *
     * @param lottery The lottery to force-close, as it is now.
     * @param nameOfCampsite Resolves the campsite names the notices carry; the caller
     *     knows the lotteried campsites.
     * @param onDone Called with whether the closed lottery was stored.
     */
    fun forceCloseLottery(
        lottery: Lottery,
        nameOfCampsite: (String) -> String = { id -> id },
        onDone: (Boolean) -> Unit = {}
    ) {
        repo.getEntries(
            lotteryId = lottery.id,
            onSuccess = { entries ->
                val entrants = entries.map { entry -> entry.userId }.distinct()
                val winners = entrants.shuffled().take(lottery.numberOfWinners)
                val losers = entrants.filterNot { it in winners }
                val now = clock()
                val closed = lottery.copy(
                    drawn = true,
                    winnerIds = winners,
                    // a window that has not started yet is pulled to now too, so the
                    // force-closed lottery always reads BOOKING at its own clock
                    registrationStart = if (lottery.registrationStart > now) now
                        else lottery.registrationStart,
                    registrationEnd = now,
                    bookingStart = now,
                    bookingEnd = startOfTodayUtc() + DAY_MILLIS - 1
                )
                repo.save(
                    closed,
                    onSuccess = {
                        notifications.notify(
                            NotificationIntent.LotteryDrawn(
                                lotteryId = closed.id,
                                campsiteName =
                                    closed.campsiteIds.map(nameOfCampsite).joinToString(", "),
                                winnerIds = winners,
                                loserIds = losers
                            )
                        )
                        model.setStatusMessage("The lottery was closed and drawn")
                        onDone(true)
                    },
                    onFailure = { error ->
                        model.setStatusMessage("Could not force-close the lottery: " + error.message)
                        onDone(false)
                    }
                )
            },
            onFailure = { error ->
                model.setStatusMessage("Could not read the waiting list: " + error.message)
                onDone(false)
            }
        )
    }

    /**
     * The first millisecond of today on the UTC basis — the basis every lottery date the
     * picker writes and the lottery views display are kept on, so a force-closed
     * lottery's window names today in those views.
     */
    private fun startOfTodayUtc(): Long =
        try {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = formatter.parse(DateUtils.today())
            if (parsed != null) parsed.time else clock()
        } catch (e: Exception) {
            clock()
        }

    /**
     * Joins the open lottery's waiting list, or leaves it if the user is already on it
     * (US 01.01.06, 01.02.01, 01.02.02). Does nothing if the campsite has no open lottery.
     */
    fun toggleEntrantMembership(userId: String) {       // prompted Claude Sonnet 5.5 to move join/leave logic from screen to controller
        val lottery = model.entrantLottery
        if (lottery == null) {
            return
        }
        if (model.entrantIsOnWaitlist) {
            leave(lottery, userId)
        } else {
            join(lottery, userId)
        }
    }

    fun loadWaitlistEntries(lotteryId: String, onResult: (List<WaitingListEntry>) -> Unit) {
        repo.getEntries(
            lotteryId = lotteryId,
            onSuccess = { entries -> onResult(entries) },
            onFailure = { onResult(emptyList()) }
        )
    }
}

