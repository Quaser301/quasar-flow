package com.example.campbnb.controller

import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.NotificationUpdate
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
    fun notifyWinners (
        lotteryId: String,
        campsiteName: String,
        winnerIds: List<String>,
        onDone: (Result<Int>) -> Unit
    ) = sendLotteryResult(NotificationType.WIN, lotteryId, campsiteName, winnerIds, onDone)
    fun listen (
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
}
