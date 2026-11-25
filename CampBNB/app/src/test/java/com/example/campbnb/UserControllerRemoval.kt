package com.example.campbnb

// Implemented with the help of Claude

/**
 * UserController's removal watcher: a user deleting their own profile must not look like an
 * admin removing it, because only the admin case shows the "your profile was deleted" pop-up.
 */

import com.example.campbnb.controller.UserController
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel
import org.junit.Assert.assertEquals
import org.junit.Test

class UserControllerRemovalTest {

    /** Remembers the watcher; delete() fires it first, the way Firestore's local snapshot does. */
    private class FakeUserRepository : UserRepository() {
        var removalCallback: (() -> Unit)? = null
        var failDelete = false

        override fun listenForRemoval(userId: String, onRemoved: () -> Unit): () -> Unit {
            removalCallback = onRemoved
            return {}
        }

        override fun delete(userId: String, onComplete: (Result<Exception?>) -> Unit) {
            if (failDelete) {
                onComplete(Result.failure(Exception("fake failure")))
                return
            }
            removalCallback?.invoke()
            onComplete(Result.success(null))
        }
    }

    private val repo = FakeUserRepository()
    private val model = UserModel()
    private val controller = UserController(model, repo)
    private var selfCalls = 0
    private var adminCalls = 0

    private fun startWatching() {
        model.setCurrentUser(User(deviceId = "user-1", name = "Bo", roles = listOf(Role.ENTRANT)))
        controller.watchForRemoval(
            "user-1",
            onDeletedBySelf = { selfCalls = selfCalls + 1 },
            onRemoved = { adminCalls = adminCalls + 1 }
        )
    }

    @Test
    fun test_AnAdminRemovalCallsOnRemoved() {
        startWatching()

        repo.removalCallback?.invoke()

        assertEquals("the pop-up path ran", 1, adminCalls)
        assertEquals("the self-delete path did not", 0, selfCalls)
    }

    @Test
    fun test_DeletingYourOwnProfileDoesNotLookLikeAnAdminRemoval() {
        startWatching()

        controller.deleteProfile { }

        assertEquals("the self-delete path ran", 1, selfCalls)
        assertEquals("the pop-up path did not", 0, adminCalls)
    }

    @Test
    fun test_AFailedSelfDeleteDoesNotHideALaterAdminRemoval() {
        startWatching()
        repo.failDelete = true
        controller.deleteProfile { }

        repo.removalCallback?.invoke()

        assertEquals("the pop-up path ran", 1, adminCalls)
        assertEquals("the self-delete path did not", 0, selfCalls)
    }
}