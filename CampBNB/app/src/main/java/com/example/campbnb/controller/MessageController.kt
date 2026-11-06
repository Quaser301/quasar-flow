package com.example.campbnb.controller

import com.example.campbnb.data.MessageRepository
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.models.Message
import com.example.campbnb.models.MessageStatus

/**
 * Handles messaging between a campsite owner and an entrant (US 02.12.01).
 *
 * After a message is saved, [notifier] tells the recipient (a MESSAGE notification and banner).
 * A failed notification never fails the message itself.
 *
 * @param notifier tells the recipient about a new message. Null turns notifications off (tests).
 * @param campsiteNameLookup finds a campsite's name from its ID, for the notification text.
 */
class MessageController(
    private val repo: MessageRepository,
    private val reservationRepository: ReservationRepository,
    private val notifier: NewMessageNotifier? = null,
    private val campsiteNameLookup: (campsiteId: String, onName: (String) -> Unit) -> Unit =
        { _, onName -> onName("") }
) {

    /**
     * Sends a message in the chat for [reservationId]. Looks the reservation up to find the other
     * person, so it works for the first message of a chat.
     */
    fun sendMessage(
        reservationId: String,
        senderId: String,
        content: String,
        onDone: (Result<Message>) -> Unit
    ) {
        val text = content.trim()
        validate(reservationId, senderId, text)?.let {
            onDone(Result.failure(IllegalArgumentException(it)))
            return
        }

        // The reservation's document ID is its lottery entry ID.
        reservationRepository.getByLotteryEntry(
            lotteryEntryId = reservationId,
            onSuccess = { reservation ->
                if (reservation == null) {
                    onDone(Result.failure(NoSuchElementException("Reservation not found")))
                    return@getByLotteryEntry
                }

                val recipientId = when (senderId) {
                    reservation.ownerId -> reservation.entrantId
                    reservation.entrantId -> reservation.ownerId
                    else -> ""
                }
                if (recipientId.isBlank()) {
                    onDone(
                        Result.failure(
                            IllegalArgumentException("Sender is not part of this reservation")
                        )
                    )
                    return@getByLotteryEntry
                }

                saveAndNotify(
                    Message(
                        reservationId = reservation.lotteryEntryId,
                        campsiteId = reservation.campsiteId,
                        ownerId = reservation.ownerId,
                        entrantId = reservation.entrantId,
                        participantIds = listOf(reservation.ownerId, reservation.entrantId),
                        senderId = senderId,
                        recipientId = recipientId,
                        content = text,
                        status = MessageStatus.UNREAD
                    ),
                    onDone
                )
            },
            onFailure = { error -> onDone(Result.failure(error)) }
        )
    }

    /**
     * Replies in a chat whose two people are already known (it has at least one message), so no
     * reservation lookup is needed.
     */
    fun reply(
        conversation: Conversation,
        senderId: String,
        content: String,
        onDone: (Result<Message>) -> Unit
    ) {
        val text = content.trim()
        validate(conversation.reservationId, senderId, text)?.let {
            onDone(Result.failure(IllegalArgumentException(it)))
            return
        }

        val recipientId = when (senderId) {
            conversation.ownerId -> conversation.entrantId
            conversation.entrantId -> conversation.ownerId
            else -> ""
        }
        if (recipientId.isBlank()) {
            onDone(
                Result.failure(IllegalArgumentException("Sender is not part of this reservation"))
            )
            return
        }

        saveAndNotify(
            Message(
                reservationId = conversation.reservationId,
                campsiteId = conversation.campsiteId,
                ownerId = conversation.ownerId,
                entrantId = conversation.entrantId,
                participantIds = listOf(conversation.ownerId, conversation.entrantId),
                senderId = senderId,
                recipientId = recipientId,
                content = text,
                status = MessageStatus.UNREAD
            ),
            onDone
        )
    }

    /** Listens for all messages involving a user. Returns the function that stops the listener. */
    fun listenForUser(
        userId: String,
        onUpdate: (List<Message>) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit = repo.listenForUser(userId, onUpdate, onError)

    /** Listens for messages belonging to one reservation. */
    fun listenForConversation(
        reservationId: String,
        onUpdate: (List<Message>) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit = repo.listenForReservation(reservationId, onUpdate, onError)

    /** Marks messages as read. */
    fun markMessagesRead(
        messageIds: List<String>,
        onDone: (Result<Unit>) -> Unit = {}
    ) {
        repo.markRead(messageIds, onDone)
    }

    /** Sorts messages from oldest to newest. */
    fun sortOldestFirst(messages: List<Message>): List<Message> =
        messages.sortedBy { it.sentAt?.seconds ?: 0L }

    /** Sorts messages from newest to oldest. */
    fun sortNewestFirst(messages: List<Message>): List<Message> =
        messages.sortedByDescending { it.sentAt?.seconds ?: 0L }

    /** The reason a message cannot be sent, or null if it can: blanks, empty text,
     *     and length over MAX_LENGTH are all refused. */
    private fun validate(reservationId: String, senderId: String, text: String): String? = when {
        reservationId.isBlank() -> "Reservation ID is required"
        senderId.isBlank() -> "Sender ID is required"
        text.isEmpty() -> "Message cannot be empty"
        text.length > MAX_LENGTH -> "Message is too long (max $MAX_LENGTH characters)"
        else -> null
    }

    // Saves the message, then (only if the save worked) tells the recipient.
    private fun saveAndNotify(message: Message, onDone: (Result<Message>) -> Unit) {
        repo.send(message) { result ->
            result.onSuccess {
                notifier?.let { n ->
                    campsiteNameLookup(message.campsiteId) { name -> n.messageSent(message, name) }
                }
            }
            onDone(result.map { message })
        }
    }

    companion object {
        const val MAX_LENGTH = 1000
    }
}


