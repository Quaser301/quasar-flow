package com.example.campbnb.data

import com.example.campbnb.database.Database
import com.example.campbnb.models.User

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

    /** Reads the users who turned notifications off (US 01.10.01). */
    open fun getOptedOut(onSuccess: (List<User>) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Users.readEngine().query()
            .whereEquals(User::notificationsEnabled, false)
            .execute(onSuccess, onFailure)
    }
}