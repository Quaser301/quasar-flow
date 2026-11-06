package com.example.campbnb.data

import com.example.campbnb.models.Message

interface MessageRepository {

    /**
     * Saves one new message. The repository gives it a fresh unique ID (written into
     * [Message.id]) before saving, so after this call the caller can read the ID.
     */
    fun send(message: Message, onDone: (Result<Unit>) -> Unit)

    /**
     * LIVE listener for every message the user can see (owner or entrant of the reservation).
     * [onUpdate] is called now and after every change.
     *
     * @return a function that stops the listener
     */
    fun listenForUser(
        userId: String,
        onUpdate: (List<Message>) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit

    /** LIVE listener for the messages of ONE conversation (one reservation). */
    fun listenForReservation(
        reservationId: String,
        onUpdate: (List<Message>) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit

    /** Marks the given messages READ (one batched write). An empty list is a success. */
    fun markRead(messageIds: List<String>, onDone: (Result<Unit>) -> Unit)
}