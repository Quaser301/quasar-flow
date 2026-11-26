package com.example.campbnb.notificationTests

import com.example.campbnb.data.NotificationCommand
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

    /** Forgets what this fake holds, so a case can read only the records it saved. */
    fun forgetSaved() {
        saved.clear()
    }

    /** Simulates a Firestore snapshot update for the per-user listener. */
    fun emit(update: NotificationUpdate) {
        listener?.invoke(update)
    }

    /** Carries out whichever request the notification feature asked for. */
    override fun run(command: NotificationCommand) {
        when (command) {
            is NotificationCommand.Save -> {
                saveCalls++
                if (failNextSave) {
                    failNextSave = false
                    command.onDone.invoke(Result.failure(Exception("fake network failure")))
                    return
                }
                command.records.forEach { saved[it.id] = it }
                command.onDone.invoke(Result.success(Unit))
            }

            is NotificationCommand.Read -> {
                readAllCalls++
                if (failNextReadAll) {
                    failNextReadAll = false
                    command.onFailure.invoke(Exception("fake read failure"))
                    return
                }
                command.onSuccess.invoke(saved.values.toList())
            }

            is NotificationCommand.Watch -> {
                listeningUserId = command.userId
                listener = command.onUpdate
                command.onListening.invoke {
                    stopCalls++
                    listener = null
                    listeningUserId = null
                }
            }

            is NotificationCommand.MarkRead -> {
                val notification = saved[command.notificationId]
                if (notification == null) {
                    command.onDone.invoke(
                        Result.failure(NoSuchElementException(command.notificationId))
                    )
                    return
                }
                notification.status = NotificationStatus.READ
                command.onDone.invoke(Result.success(Unit))
            }

            is NotificationCommand.Delete -> {
                if (!saved.containsKey(command.notificationId)) {
                    command.onDone.invoke(
                        Result.failure(NoSuchElementException(command.notificationId))
                    )
                    return
                }
                saved.remove(command.notificationId)
                command.onDone.invoke(Result.success(Unit))
            }
        }
    }
}
