

package com.example.campbnb.data

import com.example.campbnb.database.Database
import com.example.campbnb.models.Message
import com.example.campbnb.models.MessageStatus
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Stores and retrieves messages using Firebase Firestore.
 *
 * Supports:
 * - Sending messages
 * - Listening for a user's messages
 * - Listening to one reservation conversation
 * - Marking messages as read
 */
class FirestoreMessageRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : MessageRepository {

    private val collection =
        db.collection(Database.Messages.COLLECTION_NAME)

    /**
     * Saves a new message to Firestore.
     * A unique document ID is assigned before saving.
     */
    override fun send(
        message: Message,
        onDone: (Result<Unit>) -> Unit
    ) {
        val ref = collection.document()

        // Assign the Firestore document ID to the message.
        message.id = ref.id

        ref.set(message)
            .addOnSuccessListener {
                onDone(Result.success(Unit))
            }
            .addOnFailureListener { error ->
                onDone(Result.failure(error))
            }
    }

    /**
     * Listens for every message involving a particular user.
     */
    override fun listenForUser(
        userId: String,
        onUpdate: (List<Message>) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit {

        val registration = collection
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot
                        .toObjects(Message::class.java)
                        .sortedByDescending {
                            it.sentAt?.seconds ?: 0L
                        }

                    onUpdate(messages)
                }
            }

        // Return a function that stops listening.
        return {
            registration.remove()
        }
    }

    /**
     * Listens for messages belonging to one reservation.
     */
    override fun listenForReservation(
        reservationId: String,
        onUpdate: (List<Message>) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit {

        val registration = collection
            .whereEqualTo("reservationId", reservationId)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot
                        .toObjects(Message::class.java)
                        .sortedBy {
                            it.sentAt?.seconds ?: 0L
                        }

                    onUpdate(messages)
                }
            }

        return {
            registration.remove()
        }
    }

    /**
     * Marks the specified messages as READ.
     *
     * Uses batches so multiple messages can be updated together.
     */
    override fun markRead(
        messageIds: List<String>,
        onDone: (Result<Unit>) -> Unit
    ) {

        val validIds = messageIds
            .filter { it.isNotBlank() }
            .distinct()

        if (validIds.isEmpty()) {
            onDone(Result.success(Unit))
            return
        }

        // Firestore allows a maximum of 500 writes per batch.
        val chunks = validIds.chunked(MAX_BATCH)

        var remaining = chunks.size
        var failed = false

        chunks.forEach { chunk ->

            val batch = db.batch()

            chunk.forEach { id ->
                batch.update(
                    collection.document(id),
                    "status",
                    MessageStatus.READ.name
                )
            }

            batch.commit()
                .addOnSuccessListener {

                    remaining--

                    if (remaining == 0 && !failed) {
                        onDone(Result.success(Unit))
                    }
                }
                .addOnFailureListener { error ->

                    if (!failed) {
                        failed = true
                        onDone(Result.failure(error))
                    }
                }
        }
    }

    private companion object {
        const val MAX_BATCH = 500
    }
}
