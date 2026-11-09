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

    override fun saveAll(notifications: List<Notification>, onDone: (Result<Unit>) -> Unit) {
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
     * Listens for every saved notification, whichever user receives it. The same snapshot
     * handling as the per-user listener below, without the recipientId filter.
     */
    override fun listenForAll(
        onUpdate: (NotificationUpdate) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit {
        val registration = collection
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
        return { registration.remove() }
    }

    override fun listenForUser(
        userId: String,
        onUpdate: (NotificationUpdate) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit {
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
                    .map{ it.document.toObject(Notification::class.java)}
                onUpdate(NotificationUpdate(all, added))
            }
        return { registration.remove() }
    }

    override fun markRead(notificationId: String, onDone: (Result<Unit>) -> Unit) {
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