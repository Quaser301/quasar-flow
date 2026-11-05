package com.example.campbnb

import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.NotificationUpdate
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationStatus

class FakeNotificationRepository : NotificationRepository {
    val saved = mutableMapOf<String, Notification>()
    var saveCalls = 0
    var failNextSave = false

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

    override fun listenForUser(
        userId: String,
        onUpdate: (NotificationUpdate) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit = { }

    override fun markRead(notificationId: String, onDone: (Result<Unit>) -> Unit) {
        saved[notificationId]?.status = NotificationStatus.READ
        onDone(Result.success(Unit))
    }
}