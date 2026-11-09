package com.example.campbnb.notificationTests

import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.NotificationUpdate
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationStatus
import com.example.campbnb.models.NotificationType
import com.example.campbnb.ui.inbox.InboxState
import com.example.campbnb.ui.inbox.NotificationViewModel
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationViewModelTest {
    private val repository = FakeNotificationRepository()

    private fun notification(id: String, seconds: Long) =
        Notification(
            recipientId = "user-1",
            type = NotificationType.LOSE,
            title = "Lottery update",
            message = "Your lottery result is available.",
            sentAt = Timestamp(seconds, 0)
        ).also { it.id = id }

    @Test
    fun startsInLoadingState() {
        val viewModel = NotificationViewModel(
            NotificationController(repository),
            "user-1"
        )
        assertTrue(viewModel.state.value is InboxState.Loading)
    }

    @Test
    fun displaysNotificationsNewestFirst() {
        val viewModel = NotificationViewModel(
            NotificationController(repository),
            "user-1"
        )
        val older = notification("older", 100)
        val newer = notification("newer", 200)

        repository.emit(
            NotificationUpdate(
                all = listOf(older, newer),
                added = emptyList()
            )
        )

        val ready = viewModel.state.value as InboxState.Ready
        assertEquals(
            listOf("newer", "older"),
            ready.items.map { it.id }
        )
    }

    @Test
    fun openingUnreadNotificationMarksItRead() {
        val viewModel = NotificationViewModel(
            NotificationController(repository),
            "user-1"
        )
        val unread = notification("n1", 100)
        repository.saved[unread.id] = unread

        viewModel.open(unread)

        assertEquals(
            NotificationStatus.READ,
            repository.saved.getValue("n1").status
        )
    }

    @Test
    fun openingTheSameNotificationTwice_stillLeavesItRead() {
        val viewModel = NotificationViewModel(
            NotificationController(repository),
            "user-1"
        )
        val unread = notification("n2", 100)
        repository.saved[unread.id] = unread

        viewModel.open(unread)
        viewModel.open(unread)

        assertEquals(
            NotificationStatus.READ,
            repository.saved.getValue("n2").status
        )
    }

    @Test
    fun blankUserShowsErrorInsteadOfListening() {
        val viewModel = NotificationViewModel(
            NotificationController(repository),
            ""
        )
        assertTrue(viewModel.state.value is InboxState.Error)
    }
}
