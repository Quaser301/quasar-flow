package com.example.campbnb.data

import com.example.campbnb.models.Notification

/**
 * Contract between notification logic and wherever notifications are stored
 *
 * Unit tests use a fake and the real app uses FirestoreNotificationRepository
 */

/**
 * one live update form notification listener
 * @property all Every notification the user currently has
 * @property added Only the notifications that are new since the previous update
 */
data class NotificationUpdate(
    val all: List<Notification>,
    val added: List<Notification>
)

/**
 * one request against a notification store, as data rather than as a method call
 *
 * A request names what to ask of the store and what to do with the answer, which is what
 * lets a store be asked through one entry point: the store decides how to carry out the
 * request, and a view over another store decides per request what to change about it. A
 * store answers asynchronously, so every answer a request can give is part of the request
 * itself and nothing comes back up the call stack.
 *
 * @memberproperty Save Store these records.
 * @memberproperty Read Ask for every saved notification, whichever user receives them.
 * @memberproperty Watch Follow one user's notifications live.
 * @memberproperty MarkRead Record that one notification has been opened.
 * @memberproperty Delete Remove one saved notification.
 */
sealed interface NotificationCommand {
    /**
     * store these records, all of them in one request
     * @param onDone called exactly once, with success or with the failure
     */
    data class Save(
        val records: List<Notification>,
        val onDone: (Result<Unit>) -> Unit
    ) : NotificationCommand

    /**
     * ask for every saved notification, in one read, whichever user receives them
     *
     * Only the notification debug screen asks this: the per user listener below cannot
     * answer "what has the app sent overall". It is asked once and re-asked on an explicit
     * tap rather than followed, because a listener over the whole collection re-delivers
     * every saved record on every write anywhere.
     *
     * @param onSuccess called once with every saved notification
     * @param onFailure called once when the read fails
     */
    data class Read(
        val onSuccess: (List<Notification>) -> Unit,
        val onFailure: (Exception) -> Unit
    ) : NotificationCommand

    /**
     * follow one user's notifications live
     *
     * @param onUpdate called on every update of that user's notifications
     * @param onError called when following fails
     * @param onListening called once with a function that stops following. The caller keeps
     * it and calls it when its screen goes away, otherwise the listener keeps running
     */
    data class Watch(
        val userId: String,
        val onUpdate: (NotificationUpdate) -> Unit,
        val onError: (Exception) -> Unit,
        val onListening: (stop: () -> Unit) -> Unit
    ) : NotificationCommand

    /** marks one notification as READ and records when */
    data class MarkRead(
        val notificationId: String,
        val onDone: (Result<Unit>) -> Unit
    ) : NotificationCommand

    /**
     * removes one saved notification, so it is no longer listed anywhere
     *
     * A viewer deleting one of its own notices asks for this: the record is gone rather
     * than marked, so neither the inbox nor the debug log will show it again.
     *
     * @param notificationId the database key of the record to remove
     * @param onDone called exactly once, with success or with the failure
     */
    data class Delete(
        val notificationId: String,
        val onDone: (Result<Unit>) -> Unit
    ) : NotificationCommand
}

/** what a notification store is asked to do */
interface NotificationRepository {
    /**
     * runs one request against this store
     *
     * The request names its own continuations, so nothing is returned: results reach the
     * caller through the request's continuations, as everywhere else in an asynchronous
     * store.
     *
     * @param command the request to run
     */
    fun run(command: NotificationCommand)
}
