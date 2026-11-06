package com.example.campbnb.viewModels

import androidx.lifecycle.ViewModel
import com.example.campbnb.controller.Conversation
import com.example.campbnb.controller.Conversations
import com.example.campbnb.controller.MessageController
import com.example.campbnb.models.Message
import com.example.campbnb.models.MessageStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the Messages tab shows.
 *
 * Ready.openConversation is null while the chat list is showing, and holds the chat when one is open.
 */
sealed interface MessagesState {
    data object Loading : MessagesState
    data class Ready(
        val conversations: List<Conversation>,
        val openConversation: Conversation? = null,
        val thread: List<Message> = emptyList()
    ) : MessagesState
    data class Error(val message: String) : MessagesState
}

/**
 * Owns the live message state for a single user.
 */
class MessageViewModel(
    private val controller: MessageController,
    private val userId: String
) : ViewModel() {

    private val _state = MutableStateFlow<MessagesState>(MessagesState.Loading)
    val state: StateFlow<MessagesState> = _state.asStateFlow()

    private var allMessages: List<Message> = emptyList()
    private var loaded = false
    private var openId: String? = null
    private val markingRead = mutableSetOf<String>()
    private var stopListening: (() -> Unit)? = null

    init {
        start()
    }

    fun start() {
        stopListening?.invoke()
        stopListening = null
        loaded = false
        _state.value = MessagesState.Loading

        if (userId.isBlank()) {
            _state.value = MessagesState.Error("No signed-in user was found.")
            return
        }

        stopListening = controller.listenForUser(
            userId = userId,
            onUpdate = { messages ->
                allMessages = messages
                loaded = true
                publish()
            },
            onError = { error ->
                _state.value = MessagesState.Error(error.message ?: "Could not load messages.")
            }
        )
    }

    /**
     * Opens the chat for [reservationId]. Also used for the "chat/{reservationId}" notification link.
     * If the chat has no messages yet it opens empty, and the first send looks the reservation up.
     */
    fun open(reservationId: String) {
        openId = reservationId
        publish()
    }

    /** Goes back from a chat to the chat list. */
    fun close() {
        openId = null
        publish()
    }

    /**
     * Sends [text] in the open chat.
     * @param onResult called with the saved message, or with the reason it failed
     */
    fun send(text: String, onResult: (Result<Message>) -> Unit = {}) {
        val open = (_state.value as? MessagesState.Ready)?.openConversation
        if (open == null) {
            onResult(Result.failure(IllegalStateException("No chat is open")))
            return
        }
        if (open.hasBothParticipants) {
            controller.reply(open, userId, text, onResult)
        } else {
            controller.sendMessage(open.reservationId, userId, text, onResult)
        }
    }

    private fun publish() {
        if (!loaded) return

        val conversations = Conversations.group(userId, allMessages)
        val open = openId?.let { id ->
            conversations.firstOrNull { it.reservationId == id } ?: Conversation(reservationId = id)
        }
        val thread = if (open != null) {
            Conversations.thread(userId, open.reservationId, allMessages)
        } else {
            emptyList()
        }
        _state.value = MessagesState.Ready(conversations, open, thread)

        if (open != null) markIncomingRead(thread)
    }

    // Opening a chat counts as reading it. Each id is only sent once, so the snapshot that
    // follows the write cannot trigger the same write again.
    private fun markIncomingRead(thread: List<Message>) {
        val ids = thread
            .filter {
                it.recipientId == userId &&
                        it.status == MessageStatus.UNREAD &&
                        markingRead.add(it.id)
            }
            .map { it.id }
        if (ids.isNotEmpty()) {
            controller.markMessagesRead(ids) { result ->
                if (result.isFailure) markingRead.removeAll(ids.toSet())
            }
        }
    }

    override fun onCleared() {
        stopListening?.invoke()
        stopListening = null
        super.onCleared()
    }
}
