package com.example.campbnb.notificationTests

import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.NotificationUpdate
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationStatus

class FakeNotificationRepository : NotificationRepository {
    val saved = mutableMapOf<String, Notification>()
    var saveCalls = 0
    var failNextSave = false
    var stopCalls = 0
    var failNextListenAll = false

    private var listeningUserId: String? = null
    private var listener: ((NotificationUpdate) -> Unit)? = null
    private var allListener: ((NotificationUpdate) -> Unit)? = null

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

    /** Simulates a Firestore snapshot update in tests. */
    fun emit(update: NotificationUpdate) {
        listener?.invoke(update)
    }

    /** Simulates a snapshot update for the all-notifications listener. */
    fun emitAll(update: NotificationUpdate) {
        allListener?.invoke(update)
    }

    override fun listenForAll(
        onUpdate: (NotificationUpdate) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit {
        if (failNextListenAll) {
            failNextListenAll = false
            onError(Exception("fake read failure"))
            return {}
        }
        allListener = onUpdate
        return {
            stopCalls++
            allListener = null
        }
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