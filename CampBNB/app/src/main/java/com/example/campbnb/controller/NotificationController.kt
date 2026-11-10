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

    /**
     * Reads every saved notification once, for the notification debug screen only.
     *
     * Read on demand rather than listened to: the log is a record of what has already been
     * sent, and a whole-collection listener would re-deliver every record on every write.
     *
     * @param onSuccess called once with every saved notification
     * @param onFailure called once when the read fails
     */
    fun readAll(
        onSuccess: (List<Notification>) -> Unit,
        onFailure: (Exception) -> Unit
    ) = repo.readAll(onSuccess, onFailure)

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


    /**
     * Sends a notification for reservation or messaging events.
     *
     * Notification failures do not affect the original operation.
     *
     * @param recipientId user receiving the notification
     * @param type notification category
     * @param details additional information about the event
     */
    fun send(
        recipientId: String,
        type: String,
        details: Map<String, String>
    ) {
        if (recipientId.isBlank()) return

        // Convert the string into the notification enum.
        val notificationType = try {
            NotificationType.valueOf(type.uppercase())
        } catch (e: IllegalArgumentException) {
            return
        }

        val campsiteName = details["campsiteName"]
            ?.ifBlank { "the campsite" }
            ?: "the campsite"

        val reservationId = details["reservationId"] ?: ""
        val senderId = details["senderId"] ?: ""

        // Build the notification content.
        val (title, message) = when (notificationType) {

            NotificationType.MESSAGE -> {
                val preview = details["preview"]
                    ?.takeIf { it.isNotBlank() }

                "New message about $campsiteName" to
                        if (preview != null) {
                            "You received a new message: $preview"
                        } else {
                            "You received a new message. Open CampBNB to read it."
                        }
            }

            NotificationType.CONFIRMATION ->
                "Reservation confirmed for $campsiteName" to
                        "Your reservation has been confirmed."

            NotificationType.CANCELLATION ->
                "Reservation cancelled for $campsiteName" to
                        "The reservation has been cancelled."

            NotificationType.REJECTION ->
                "Reservation rejected for $campsiteName" to
                        "The reservation request has been rejected."

            NotificationType.OWNER_NOTICE ->
                "Reservation update for $campsiteName" to
                        "There is an update regarding your reservation."

            else -> return
        }

        // Check whether the recipient allows notifications.
        optOutChecker.filterAllowed(listOf(recipientId)) { allowed ->

            val notification = Notification(
                recipientId = recipientId,
                senderId = senderId,
                type = notificationType,
                title = title,
                message = message,
                reservationId = reservationId,
                campsiteName = campsiteName,
                pushAllowed = recipientId in allowed
            )

            // Save the notification.
            repo.saveAll(listOf(notification)) { result ->
                result.onFailure { error ->
                    println(
                        "Failed to save notification: ${error.message}"
                    )
                }
            }
        }
    }
}
