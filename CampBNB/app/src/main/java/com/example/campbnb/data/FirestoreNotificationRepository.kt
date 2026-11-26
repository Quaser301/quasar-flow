package com.example.campbnb.data

import com.example.campbnb.database.Database
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationStatus
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
/** stores notifications in Firestore */
class FirestoreNotificationRepository (
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
): NotificationRepository {
    private val collection = db.collection(Database.Notifications.COLLECTION_NAME)

    /**
     * Carries out whichever request the notification feature asked for.
     *
     * The request names its own continuations, so nothing is returned here: each branch
     * hands its answer back through the request.
     */
    override fun run(command: NotificationCommand) {
        when (command) {
            is NotificationCommand.Save -> storeAll(command.records, command.onDone)

            is NotificationCommand.Read -> readLog(command.onSuccess, command.onFailure)

            is NotificationCommand.Watch -> followUser(
                command.userId,
                command.onUpdate,
                command.onError,
                command.onListening
            )

            is NotificationCommand.MarkRead -> markOpened(command.notificationId, command.onDone)
        }
    }

    private fun storeAll(
        notifications: List<Notification>,
        onDone: (Result<Unit>) -> Unit
    ) {
        if (notifications.isEmpty()) {
            onDone(Result.success(Unit))
            return
        }

        val chunks = notifications.chunked(MAX_BATCH)
        var remaining = chunks.size
        var failed = false
        chunks.forEach { chunks ->
            val batch = db.batch()
            chunks.forEach { n -> batch.set(collection.document(n.id), n) } // document(n.id) means "write to exactly this ID"
            batch.commit()
                .addOnSuccessListener {
                    remaining--
                    if (remaining == 0 && !failed) onDone(Result.success(Unit))
                }
                .addOnFailureListener { error ->
                    if (!failed) {
                        failed = true
                        onDone(Result.failure(error))
                    }
                }
        }
    }

    /**
     * Reads every saved notification once, whichever user receives it. The debug log is a
     * record of broadcasts that have already been sent, so it is read through the read engine
     * the other repositories use rather than subscribed to: nothing is left open here that a
     * later write could re-deliver.
     */
    private fun readLog(
        onSuccess: (List<Notification>) -> Unit,
        onFailure: (Exception) -> Unit
    ) = Database.Notifications.readEngine().query().execute(onSuccess, onFailure)

    /**
     * Follows one user's notifications live, and hands the caller the way to stop.
     *
     * @param onListening given the function that drops the snapshot listener
     */
    private fun followUser(
        userId: String,
        onUpdate: (NotificationUpdate) -> Unit,
        onError: (Exception) -> Unit,
        onListening: (stop: () -> Unit) -> Unit
    ) {
        val registration = collection
            .whereEqualTo("recipientId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val all = snapshot.toObjects(Notification::class.java)
                val added = snapshot.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .map { it.document.toObject(Notification::class.java) }
                onUpdate(NotificationUpdate(all, added))
            }
        onListening.invoke { registration.remove() }
    }

    private fun markOpened(notificationId: String, onDone: (Result<Unit>) -> Unit) {
        collection.document(notificationId)
            .update(
                mapOf(
                    "status" to NotificationStatus.READ.name,
                    "readAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { onDone(Result.success(Unit)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    private companion object {
        const val MAX_BATCH = 500
    }
}
