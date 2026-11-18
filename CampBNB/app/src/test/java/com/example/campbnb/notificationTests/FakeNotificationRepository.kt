package com.example.campbnb.notificationTests

import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.NotificationUpdate
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationStatus

class FakeNotificationRepository : NotificationRepository {
    val saved = mutableMapOf<String, Notification>()
    var saveCalls = 0
    var readAllCalls = 0
    var stopCalls = 0
    var failNextSave = false
    var failNextReadAll = false

    private var listeningUserId: String? = null
    private var listener: ((NotificationUpdate) -> Unit)? = null

    override fun saveAll(notifications: List<Notification>, onDone: (Result<Unit>) -> Unit) {
        saveCalls++
        if (failNextSave) {
            failNextSave = false
            onDone(Result.failure(Exception("fake network failure")))
            return
        }
        notifications.forEach { saved[it.id] = it }
        onDone(Result.success(Unit))
    }

    /** Forgets what this fake holds, so a case can read only the records it saved. */
    fun forgetSaved() {
        saved.clear()
    }

    /** Serves whatever this fake holds, the way one read of the collection does. */
    override fun readAll(onSuccess: (List<Notification>) -> Unit, onFailure: (Exception) -> Unit) {
        readAllCalls++
        if (failNextReadAll) {
            failNextReadAll = false
            onFailure(Exception("fake read failure"))
            return
        }
        onSuccess(saved.values.toList())
    }

    /** Simulates a Firestore snapshot update for the per-user listener. */
    fun emit(update: NotificationUpdate) {
        listener?.invoke(update)
    }

    override fun listenForUser(
        userId: String,
        onUpdate: (NotificationUpdate) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit {

        listeningUserId = userId
        listener = onUpdate
        return {
            stopCalls++
            listener = null
            listeningUserId = null
        }
    }


    override fun markRead(
        notificationId: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        val notification = saved[notificationId]
        if (notification == null) {
            onDone(Result.failure(NoSuchElementException(notificationId)))
            return
        }
        notification.status = NotificationStatus.READ
        onDone(Result.success(Unit))
    }
}
