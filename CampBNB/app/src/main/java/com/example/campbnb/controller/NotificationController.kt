package com.example.campbnb.controller

import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.NotificationUpdate
import com.example.campbnb.database.Database
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationType

fun interface OptOutChecker {
    fun filterAllowed(userIds: List<String>, onResult: (List<String>) -> Unit)
    companion object {
        val AllowAll = OptOutChecker { ids, done -> done(ids) }
    }
}

class NotificationController (
    private val repo: NotificationRepository,
    private val optOutChecker: OptOutChecker = OptOutChecker.AllowAll
) {
    fun notifyWinners(
        lotteryId: String,
        campsiteName: String,
        winnerIds: List<String>,
        onDone: (Result<Int>) -> Unit
    ) = sendLotteryResult(NotificationType.WIN, lotteryId, campsiteName, winnerIds, onDone)

    /**
     *
     * US 01.03.02: tells every entrant who was not selected that they lost.
     * @param lotteryId the lottery that was drawn
     * @param campsiteName campsite shown in the message
     * @param loserIds user IDs of the entrants who were not selected
     * @param onDone on success holds how many notifications were saved
     */
    fun notifyLosers(
        lotteryId: String,
        campsiteName: String,
        loserIds: List<String>,
        onDone: (Result<Int>) -> Unit
    ) = sendLotteryResult(NotificationType.LOSE, lotteryId, campsiteName,
        loserIds, onDone)
    /**
    losers.
    guard), so nobody
    chain.
     *
     * One call for LotteryController after a draw: winners first, then
     * A user who appears in both lists is treated as a winner only (overlap
     * is ever told they both won and lost. The first failure stops the
     * @param onDone on success holds the total number of notifications saved
     */
    fun notifyLotteryResults(
        lotteryId: String,
        campsiteName: String,
        winnerIds: List<String>,
        loserIds: List<String>,
        onDone: (Result<Int>) -> Unit
    ) {
        notifyWinners(lotteryId, campsiteName, winnerIds) { winners ->

            if (winners.isFailure) {
                onDone(winners)
            } else {
                val losersOnly = loserIds.filterNot { it in winnerIds }
                notifyLosers(lotteryId, campsiteName, losersOnly) { losers ->
                    onDone(losers.map { it + (winners.getOrNull() ?: 0) })
                }
            }
        }
    }

    fun listen(
        userId: String,
        onUpdate: (NotificationUpdate) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit = repo.listenForUser(userId, onUpdate, onError)

    fun markRead(notificationId: String, onDone: (Result<Unit>) -> Unit = {}) =
        repo.markRead(notificationId, onDone)

    private fun sendLotteryResult(
        type: NotificationType,
        lotteryId: String,
        campsiteName: String,
        userIds: List<String>,
        onDone: (Result<Int>) -> Unit
    ) {
        if (lotteryId.isBlank()) {
            onDone(Result.failure(IllegalArgumentException("lotteryId is required")))
            return
        }
        val recipients = userIds.filter { it.isNotBlank() }.distinct()
        if (recipients.isEmpty()) {
            onDone(Result.success(0))
            return
        }
        optOutChecker.filterAllowed(recipients) { allowed ->
            val allowedSet = allowed.toSet()
            val notifications = recipients.map { userId ->
                buildLotteryNotification(
                    type,
                    lotteryId,
                    campsiteName,
                    userId,
                    userId in allowedSet
                )
            }
            repo.saveAll(notifications) { result ->
                onDone(result.map {
                    notifications.size
                })
            }
        }
    }

    private fun buildLotteryNotification(
        type: NotificationType,
        lotteryId: String,
        campsiteName: String,
        userId: String,
        pushAllowed: Boolean
    ): Notification {
        val place = campsiteName.ifBlank { "the campsite" }
        val (title, message) = when (type) {
            NotificationType.WIN ->
                "You won the lottery for $place!" to
                        "Congratulations! You were selected in the draw for $place. " +
                        "Action needed: open the app and choose your dates before the booking window closes."

            NotificationType.LOSE ->
                "Lottery result for $place" to
                        "Unfortunately you were not selected in the draw this time." +
                        "Thank you for entering"
            else -> throw IllegalArgumentException("$type is not a lottery result")
        }
        return Notification(
            recipientId = userId,
            type = type,
            title = title,
            message = message,
            lotteryId = lotteryId,
            campsiteName = campsiteName,
            pushAllowed = pushAllowed
        ).also { it.id = "${lotteryId}_${userId}_${type.name}" }
    }

    companion object {
        fun sortNewestFirst(list: List<Notification>): List<Notification> =
            list.sortedByDescending { it.sentAt?.seconds ?: Long.MAX_VALUE }
    }

    /**
     * Sends notifications to users about reservation events.
     *
     * [ReservationController] calls this class when a reservation is confirmed, cancelled or
     * rejected, so the other party finds out without the controller knowing how the message is
     * delivered. Callers don't wait for a result: a notification that fails to send never
     * fails the reservation itself.
     *
     * Commands:
     *  - [send]: Delivers one notification to one user.
     *
     * @author Zino
     * @see ReservationController
     */

    fun send(recipientId: String, type: String, details: kotlin.collections.Map<String, String>) {

    }
}

