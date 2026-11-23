package com.example.campbnb


// Implemented with the help of Claude Sonnet 5.5

/**
 * AdminController's profile deletion (US 03.02.01) is tested with fake repositories, so
 * nothing touches Firebase. The cases are the ones the story leaves open or that could do
 * damage: a non-admin trying it, an admin deleting themselves, the profile's ratings and
 * waiting-list entries going with it, the notice reaching the removed user (with and
 * without a reason), and a failed cleanup keeping the profile.
 */

import com.example.campbnb.controller.AdminController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.Rating
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel
import com.example.campbnb.models.WaitingListEntry
import com.example.campbnb.notificationTests.FakeNotificationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminControllerTest {

    private class FakeUserRepository(users: List<User>) : UserRepository() {
        val users = users.toMutableList()
        var failDelete = false

        override fun findByDeviceId(
            deviceId: String,
            onSuccess: (User?) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(users.firstOrNull { it.id == deviceId })
        }

        override fun delete(userId: String, onComplete: (Result<Exception?>) -> Unit) {
            if (failDelete) {
                onComplete(Result.failure(Exception("fake failure")))
                return
            }
            users.removeAll { it.id == userId }
            onComplete(Result.success(null))
        }

        override fun getAll(onSuccess: (List<User>) -> Unit, onFailure: (Exception) -> Unit) {
            onSuccess(users.toList())
        }
    }

    private class FakeCampsiteRepository(ratings: List<Rating>) : CampsiteRepository() {
        val ratings = ratings.toMutableList()
        var failRatingDelete = false

        override fun getRatingsByUser(
            userId: String,
            onSuccess: (List<Rating>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(ratings.filter { it.userId == userId })
        }

        override fun deleteRating(ratingId: String, onComplete: (Result<Exception?>) -> Unit) {
            if (failRatingDelete) {
                onComplete(Result.failure(Exception("fake failure")))
                return
            }
            ratings.removeAll { it.id == ratingId }
            onComplete(Result.success(null))
        }
    }

    private class FakeLotteryRepository(entries: List<WaitingListEntry>) : LotteryRepository() {
        val entries = entries.toMutableList()

        override fun getEntriesByUser(
            userId: String,
            onSuccess: (List<WaitingListEntry>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(entries.filter { it.userId == userId })
        }

        override fun removeEntry(
            lotteryId: String,
            userId: String,
            onComplete: (Result<Exception?>) -> Unit
        ) {
            entries.removeAll { it.id == WaitingListEntry.makeId(lotteryId, userId) }
            onComplete(Result.success(null))
        }
    }

    /** Everything a case needs, built the way the app builds it. */
    private class Setup(signedInRoles: List<Role>) {
        val admin = User(deviceId = "admin-1", name = "Ada", roles = signedInRoles)
        val target = User(deviceId = "user-9", name = "Bo", roles = listOf(Role.ENTRANT))
        val other = User(deviceId = "user-2", name = "Cy", roles = listOf(Role.ENTRANT))

        val userRepo = FakeUserRepository(listOf(admin, target, other))
        val campsiteRepo = FakeCampsiteRepository(
            listOf(
                Rating("camp-1", "user-9", 4),
                Rating("camp-2", "user-9", 5),
                Rating("camp-1", "user-2", 3)
            )
        )
        val lotteryRepo = FakeLotteryRepository(
            listOf(
                WaitingListEntry("lot-1", "user-9"),
                WaitingListEntry("lot-1", "user-2")
            )
        )
        val notificationRepo = FakeNotificationRepository()
        val userModel = UserModel()
        val userController = UserController(userModel, userRepo)
        val controller = AdminController(
            userController,
            userRepo,
            campsiteRepo,
            lotteryRepo,
            NotificationController(notificationRepo)
        )

        var success: Boolean? = null
        var message: String = ""

        init {
            userModel.setCurrentUser(admin)
        }

        fun delete(userId: String, reason: RemovalReason?) {
            controller.deleteProfile(userId, reason) { ok, text ->
                success = ok
                message = text
            }
        }
    }

    @Test
    fun test_ANonAdminCannotDeleteAnything() {
        val s = Setup(listOf(Role.OWNER))

        s.delete("user-9", null)

        assertEquals("the request is refused", false, s.success)
        assertTrue("the profile is still there", s.userRepo.users.any { it.id == "user-9" })
        assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_AnAdminCannotDeleteTheirOwnProfile() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("admin-1", null)

        assertEquals("the request is refused", false, s.success)
        assertTrue("the admin is still there", s.userRepo.users.any { it.id == "admin-1" })
    }

    @Test
    fun test_AnAdminCannotDeleteAnotherAdmin() {
        val s = Setup(listOf(Role.ADMIN))
        val otherAdmin = User(deviceId = "admin-2", name = "Dee", roles = listOf(Role.ADMIN))
        s.userRepo.users.add(otherAdmin)
        s.campsiteRepo.ratings.add(Rating("camp-1", "admin-2", 5))

        s.delete("admin-2", RemovalReason.OTHER)

        assertEquals("the request is refused", false, s.success)
        assertTrue("the other admin is still there", s.userRepo.users.any { it.id == "admin-2" })
        assertTrue(
            "their data is untouched",
            s.campsiteRepo.ratings.any { it.userId == "admin-2" }
        )
        assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_AProfileThatNoLongerExistsChangesNothing() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("user-404", null)

        assertEquals("the request is refused", false, s.success)
        assertEquals("all three profiles remain", 3, s.userRepo.users.size)
    }

    @Test
    fun test_ABlankIdChangesNothing() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("  ", null)

        assertEquals("the request is refused", false, s.success)
        assertEquals("all three profiles remain", 3, s.userRepo.users.size)
    }

    @Test
    fun test_DeletingAProfileRemovesItsRatingsAndWaitingListEntriesOnly() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("user-9", null)

        assertEquals("the deletion worked", true, s.success)
        assertFalse("the profile is gone", s.userRepo.users.any { it.id == "user-9" })
        assertEquals("the other two profiles remain", 2, s.userRepo.users.size)
        assertEquals(
            "only the other user's rating remains",
            listOf("user-2"),
            s.campsiteRepo.ratings.map { it.userId }
        )
        assertEquals(
            "only the other user's waiting-list entry remains",
            listOf("user-2"),
            s.lotteryRepo.entries.map { it.userId }
        )
    }

    @Test
    fun test_TheListOfUsersIsReloadedAfterADeletion() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("user-9", null)

        assertFalse(
            "the list the Profiles screen shows no longer holds the deleted user",
            s.userModel.allUsers.any { it.id == "user-9" }
        )
        assertEquals("it still holds everyone else", 2, s.userModel.allUsers.size)
    }

    @Test
    fun test_TheRemovedUserIsToldWhyWhenAReasonWasPicked() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("user-9", RemovalReason.HARASSMENT)

        val notices = s.notificationRepo.saved.values.toList()
        assertEquals("exactly one notice was saved", 1, notices.size)
        assertEquals("it is addressed to the removed user", "user-9", notices[0].recipientId)
        assertTrue(
            "it states the reason picked",
            notices[0].message.contains(RemovalReason.HARASSMENT.label)
        )
    }

    @Test
    fun test_TheNoticeStillGoesOutWithoutAReason() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("user-9", null)

        val notices = s.notificationRepo.saved.values.toList()
        assertEquals("exactly one notice was saved", 1, notices.size)
        assertFalse("no reason is mentioned", notices[0].message.contains("Reason"))
    }

    @Test
    fun test_AFailedCleanupKeepsTheProfileAndSendsNoNotice() {
        val s = Setup(listOf(Role.ADMIN))
        s.campsiteRepo.failRatingDelete = true

        s.delete("user-9", null)

        assertEquals("the request reports failure", false, s.success)
        assertTrue("the profile is kept so the admin can try again", s.userRepo.users.any { it.id == "user-9" })
        assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_AFailedProfileDeleteReportsFailureAndSendsNoNotice() {
        val s = Setup(listOf(Role.ADMIN))
        s.userRepo.failDelete = true

        s.delete("user-9", RemovalReason.FRAUD)

        assertEquals("the request reports failure", false, s.success)
        assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }
}