package com.example.campbnb.notificationTests

import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.NotificationUpdate
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationStatus
import com.example.campbnb.models.NotificationType
import com.example.campbnb.notifications.NotificationWatcher
import com.example.campbnb.notifications.SystemNotifier
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationWatcherTest {
    private val repository = FakeNotificationRepository()
    private val notifier = mockk<SystemNotifier>(relaxed = true)
    private val watcher = NotificationWatcher(
        NotificationController(repository),
        notifier
    )

    private fun notification(
        id: String,
        pushAllowed: Boolean = true,
        status: NotificationStatus = NotificationStatus.UNREAD
    ) = Notification(
        recipientId = "user-1",
        type = NotificationType.WIN,
        title = "You won",
        message = "You won the lottery.",
        pushAllowed = pushAllowed,
        status = status
    ).also { it.id = id }

    @Test
    fun firstSnapshotDoesNotShowOldNotifications() {
        watcher.start("user-1")
        val old = notification("old")

        repository.emit(
            NotificationUpdate(
                all = listOf(old),
                added = listOf(old)
            )
        )

        verify(exactly = 0) { notifier.show(any()) }
    }

    @Test
    fun newUnreadAllowedNotificationShowsBanner() {
        watcher.start("user-1")
        repository.emit(NotificationUpdate(emptyList(), emptyList()))

        val fresh = notification("fresh")
        repository.emit(
            NotificationUpdate(
                all = listOf(fresh),
                added = listOf(fresh)
            )
        )

        verify(exactly = 1) { notifier.show(fresh) }
    }

    @Test
    fun readAndOptedOutNotificationsDoNotShowBanners() {
        watcher.start("user-1")
        repository.emit(NotificationUpdate(emptyList(), emptyList()))

        val optedOut = notification("opted-out", pushAllowed = false)
        val alreadyRead = notification("read", status = NotificationStatus.READ)

        repository.emit(
            NotificationUpdate(
                all = listOf(optedOut, alreadyRead),
                added = listOf(optedOut, alreadyRead)
            )
        )

        verify(exactly = 0) { notifier.show(any()) }
    }

    @Test
    fun stopRemovesListenerAndBlankUserDoesNotStartOne() {
        watcher.start("user-1")
        watcher.stop()
        assertEquals(1, repository.stopCalls)

        watcher.start(" ")
        watcher.stop()
        assertEquals(1, repository.stopCalls)
    }
}
