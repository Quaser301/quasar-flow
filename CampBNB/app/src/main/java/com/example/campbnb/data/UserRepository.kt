package com.example.campbnb.data

import com.example.campbnb.database.Database
import com.example.campbnb.models.User
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Boundary to Firestore for users. Methods are 'open' so tests can override them with a
 * fake that never touches Firebase.
 */
open class UserRepository {

    /** Reads the user whose document id is [deviceId]; the result is null if there is none. */
    open fun findByDeviceId(
        deviceId: String,
        onSuccess: (User?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        Database.Users.readEngine().getById(deviceId, onSuccess, onFailure)
    }

    /** Creates or replaces a user. */
    open fun save(user: User, onComplete: (Result<Exception?>) -> Unit) {
        Database.Users.writeEngine().save(user, onComplete)
    }

    /** Deletes a user. */
    open fun delete(userId: String, onComplete: (Result<Exception?>) -> Unit) {
        Database.Users.writeEngine().delete(userId, onComplete)
    }

    /** Reads every user (admin browsing, US 03.05.01). */
    open fun getAll(onSuccess: (List<User>) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Users.readEngine().query().execute(onSuccess, onFailure)
    }

    /**
     * Watches one user's document and calls [onRemoved] if it existed and is then deleted
     * (for example by an admin, US 03.02.01). A document that was never there, such as
     * before sign-up finishes, does not count as removed.
     *
     * @return A function that stops watching; call it when the screen or app goes away.
     */
    open fun listenForRemoval(userId: String, onRemoved: () -> Unit): () -> Unit {
        if (userId.isBlank()) {
            return {}
        }
        var existed = false
        val registration = FirebaseFirestore.getInstance()
            .collection(Database.Users.COLLECTION_NAME)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    if (snapshot.exists()) {
                        existed = true
                    } else if (existed) {
                        existed = false
                        onRemoved()
                    }
                }
            }
        return { registration.remove() }
    }

    /** Reads the users who turned notifications off (US 01.10.01). */
    open fun getOptedOut(onSuccess: (List<User>) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Users.readEngine().query()
            .whereEquals(User::notificationsEnabled, false)
            .execute(onSuccess, onFailure)
    }
}