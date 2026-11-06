package com.example.campbnb.ui.inbox

import androidx.lifecycle.ViewModel
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface InboxState {
    data object Loading : InboxState
    /** Inbox loaded; items are newest first. */
    data class Ready(val items: List<Notification>) : InboxState
    /** The inbox failed to load; message is shown instead. */
    data class Error(val message: String) : InboxState
}

/**
 * Owns the live notification inbox state for a single user.
 */
class NotificationViewModel(
    private val controller: NotificationController,
    private val userId: String
) : ViewModel() {
    private val _state = MutableStateFlow<InboxState>(InboxState.Loading)
    val state: StateFlow<InboxState> = _state.asStateFlow()

    private var stopListening: (() -> Unit)? = null

    init {
        start()
    }

    /** Restarts live following of the user's notifications; a blank userId is
     *     a hard error state. */
    fun start() {
        stopListening?.invoke()
        _state.value = InboxState.Loading

        if (userId.isBlank()) {
            _state.value = InboxState.Error("No signed-in user was found.")
            return
        }

        stopListening = controller.listen(
            userId = userId,
            onUpdate = { update ->
                _state.value = InboxState.Ready(
                    NotificationController.sortNewestFirst(update.all)
                )
            },
            onError = { error ->
                _state.value = InboxState.Error(
                    error.message ?: "Could not load notifications."
                )
            }
        )
    }

    /** Opening an unread notification marks it read. */
    fun open(notification: Notification) {
        if (notification.status == NotificationStatus.UNREAD) {
            controller.markRead(notification.id)
        }
    }

    /** Marks every unread item of the loaded inbox as read. */
    fun markAllRead() {
        val items = (_state.value as? InboxState.Ready)?.items ?: return
        items.filter { it.status == NotificationStatus.UNREAD }
            .forEach { controller.markRead(it.id) }
    }

    /** Teardown: stops the live listener. */
    override fun onCleared() {
        stopListening?.invoke()
        stopListening = null
        super.onCleared()
    }
}
