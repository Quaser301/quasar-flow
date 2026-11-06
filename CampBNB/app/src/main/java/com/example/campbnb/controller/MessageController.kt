
package com.example.campbnb.controller

import com.example.campbnb.data.MessageRepository
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.models.Message
import com.example.campbnb.models.MessageStatus

/**
 * Handles messaging between a campsite owner and an entrant.
 */
class MessageController(
    private val repo: MessageRepository,
    private val reservationRepository: ReservationRepository
) {

    /**
     * Sends a message to the other participant in a reservation.
     */
    fun sendMessage(
        reservationId: String,
        senderId: String,
        content: String,
        onDone: (Result<Message>) -> Unit
    ) {
        val text = content.trim()

        if (reservationId.isBlank()) {
            onDone(Result.failure(IllegalArgumentException("Reservation ID is required")))
            return
        }

        if (senderId.isBlank()) {
            onDone(Result.failure(IllegalArgumentException("Sender ID is required")))
            return
        }

        if (text.isBlank()) {
            onDone(Result.failure(IllegalArgumentException("Message cannot be empty")))
            return
        }

        if (text.length > MAX_LENGTH) {
            onDone(Result.failure(IllegalArgumentException("Message is too long")))
            return
        }

        // Look up the reservation using its lottery entry ID.
        reservationRepository.getByLotteryEntry(
            lotteryEntryId = reservationId,
            onSuccess = { reservation ->

                if (reservation == null) {
                    onDone(
                        Result.failure(
                            NoSuchElementException("Reservation not found")
                        )
                    )
                    return@getByLotteryEntry
                }

                // Determine the recipient.
                val recipientId = when (senderId) {
                    reservation.ownerId -> reservation.entrantId
                    reservation.entrantId -> reservation.ownerId
                    else -> null
                }

                if (recipientId == null || recipientId.isBlank()) {
                    onDone(
                        Result.failure(
                            IllegalArgumentException(
                                "Sender is not part of this reservation"
                            )
                        )
                    )
                    return@getByLotteryEntry
                }

                // Create the message.
                val message = Message(
                    reservationId = reservation.lotteryEntryId,
                    campsiteId = reservation.campsiteId,
                    ownerId = reservation.ownerId,
                    entrantId = reservation.entrantId,
                    participantIds = listOf(
                        reservation.ownerId,
                        reservation.entrantId
                    ),
                    senderId = senderId,
                    recipientId = recipientId,
                    content = text,
                    status = MessageStatus.UNREAD
                )

                // Save the message.
                repo.send(message) { result ->
                    result.fold(
                        onSuccess = {
                            onDone(Result.success(message))
                        },
                        onFailure = { error ->
                            onDone(Result.failure(error))
                        }
                    )
                }
            },
            onFailure = { error ->
                onDone(Result.failure(error))
            }
        )
    }

    /**
     * Listens for all messages involving a user.
     */
    fun listenForUser(
        userId: String,
        onUpdate: (List<Message>) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit {
        return repo.listenForUser(userId, onUpdate, onError)
    }

    /**
     * Listens for messages belonging to one reservation.
     */
    fun listenForConversation(
        reservationId: String,
        onUpdate: (List<Message>) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit {
        return repo.listenForReservation(
            reservationId,
            onUpdate,
            onError
        )
    }

    /**
     * Marks messages as read.
     */
    fun markMessagesRead(
        messageIds: List<String>,
        onDone: (Result<Unit>) -> Unit
    ) {
        repo.markRead(messageIds, onDone)
    }

    /**
     * Sorts messages from oldest to newest.
     */
    fun sortOldestFirst(messages: List<Message>): List<Message> {
        return messages.sortedBy { it.sentAt?.seconds ?: 0L }
    }

    /**
     * Sorts messages from newest to oldest.
     */
    fun sortNewestFirst(messages: List<Message>): List<Message> {
        return messages.sortedByDescending { it.sentAt?.seconds ?: 0L }
    }

    companion object {
        const val MAX_LENGTH = 1000
    }
}

