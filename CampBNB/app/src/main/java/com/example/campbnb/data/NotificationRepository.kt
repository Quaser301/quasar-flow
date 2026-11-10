package com.example.campbnb.data

/**
 * Contract between notification logic and wherever notifications are stored
 *
 * Unit tests use a fake and real app uses FirestoreNotificationRepository
 */

import com.example.campbnb.models.Notification

/**
 * one live update form notification listener
 * @property all Every notification the user currently has
 * @property added Only the notifications that are new since the previous update
 */
data class NotificationUpdate(
    val all: List<Notification>,
    val added: List<Notification>
)
/** storage operations notification feature needs */
interface NotificationRepository {
    /**
     * saves all notifications at once
     * @param onDone called exactly once, with success or with the failure
     */
    fun saveAll( notifications: List<Notification>, onDone: (Result<Unit>) -> Unit)

    /**
     * reads every saved notification, in one read, whichever user receives them
     *
     * Only the notification debug screen uses this: the per-user listener below cannot
     * answer "what has the app sent overall". It is read once and re-read on an explicit
     * tap rather than listened to, because a listener over the whole collection re-delivers
     * every saved record on every write anywhere.
     *
     * @param onSuccess called once with every saved notification
     * @param onFailure called once when the read fails
     */
    fun readAll(
        onSuccess: (List<Notification>) -> Unit,
        onFailure: (Exception) -> Unit
    )

    /**
     * starts live listener for one user's notification
     *
     * @return a function that stops the listener. Called when screen goes away, otherwise listener keeps running
     */
    fun listenForUser(
        userId: String,
        onUpdate: (NotificationUpdate) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit

    /**
     * Marks one notification as READ and records when */
    fun markRead(notificationId: String, onDone: (Result<Unit>) -> Unit)
}