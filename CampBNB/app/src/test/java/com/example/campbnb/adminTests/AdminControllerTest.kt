package com.example.campbnb.adminTests

import com.example.campbnb.controller.AdminController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Rating
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel
import com.example.campbnb.models.WaitingListEntry
import com.example.campbnb.notificationTests.FakeNotificationRepository
import org.junit.Assert
import org.junit.Test
import com.example.campbnb.data.CampsiteRemovalRepository
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.ReservationModel
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue

/**
 * AdminController's profile deletion (US 03.02.01), campsite deletion (US 03.01.01) and
 * campsite-owner removal (US 03.07.01) are tested with fake repositories, so nothing touches
 * Firebase. The cases are the ones the stories leave open or that could do damage: a
 * non-admin trying it, an admin deleting themselves, the data going with a profile or
 * campsite, an owner's campsites and lotteries going with the owner, every connected entrant
 * being told, the notice reaching the removed user (with and without a reason), and a failed
 * cleanup keeping what was to be deleted.
 */


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

    private class FakeCampsiteRepository(
        ratings: List<Rating>,
        campsites: List<Campsite> = emptyList()
    ) : CampsiteRepository() {
        val ratings = ratings.toMutableList()
        val campsites = campsites.toMutableList()
        var failRatingDelete = false
        var failCampsiteDelete = false

        override fun getById(
            campsiteId: String,
            onSuccess: (Campsite?) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(campsites.firstOrNull { it.id == campsiteId })
        }

        override fun getRatingsByCampsite(
            campsiteId: String,
            onSuccess: (List<Rating>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(ratings.filter { it.campsiteId == campsiteId })
        }

        override fun getRatingsByUser(
            userId: String,
            onSuccess: (List<Rating>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(ratings.filter { it.userId == userId })
        }

        override fun getByOwner(
            ownerId: String,
            onSuccess: (List<Campsite>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(campsites.filter { it.ownerId == ownerId })
        }

        override fun deleteRating(ratingId: String, onComplete: (Result<Exception?>) -> Unit) {
            if (failRatingDelete) {
                onComplete(Result.failure(Exception("fake failure")))
                return
            }
            ratings.removeAll { it.id == ratingId }
            onComplete(Result.success(null))
        }

        override fun deleteCampsite(
            campsiteId: String,
            onComplete: (Result<Exception?>) -> Unit
        ) {
            if (failCampsiteDelete) {
                onComplete(Result.failure(Exception("fake failure")))
                return
            }
            campsites.removeAll { it.id == campsiteId }
            onComplete(Result.success(null))
        }
    }

    private class FakeLotteryRepository(entries: List<WaitingListEntry>) : LotteryRepository() {
        val entries = entries.toMutableList()
        val lotteries = mutableListOf<Lottery>()

        override fun getAllLotteries(
            onSuccess: (List<Lottery>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(lotteries.toList())
        }

        override fun getEntries(
            lotteryId: String,
            onSuccess: (List<WaitingListEntry>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(entries.filter { it.lotteryId == lotteryId })
        }

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

    /** Holds reservations and records which were cancelled and which lotteries were deleted or saved. */
    private class FakeRemovalRepository : CampsiteRemovalRepository() {
        val reservations = mutableListOf<ReservationModel>()
        val cancelled = mutableListOf<String>()
        val deletedLotteryIds = mutableListOf<String>()
        val savedLotteries = mutableListOf<Lottery>()
        var failCancel = false

        override fun getActiveReservations(
            campsiteId: String,
            onSuccess: (List<ReservationModel>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(reservations.filter { it.campsiteId == campsiteId })
        }

        override fun cancelReservation(reservationId: String, onComplete: (Result<Unit>) -> Unit) {
            if (failCancel) {
                onComplete(Result.failure(Exception("fake failure")))
                return
            }
            cancelled.add(reservationId)
            onComplete(Result.success(Unit))
        }

        // Lets a test mirror what the real database does: a deleted lottery is gone, a saved one replaces the old.
        var onLotteryDeleted: (String) -> Unit = {}
        var onLotterySaved: (Lottery) -> Unit = {}

        override fun deleteLottery(lotteryId: String, onComplete: (Result<Exception?>) -> Unit) {
            deletedLotteryIds.add(lotteryId)
            onLotteryDeleted(lotteryId)
            onComplete(Result.success(null))
        }

        override fun saveLottery(lottery: Lottery, onComplete: (Result<Exception?>) -> Unit) {
            savedLotteries.add(lottery)
            onLotterySaved(lottery)
            onComplete(Result.success(null))
        }

        override fun getActiveReservationsOfEntrant(
            entrantId: String,
            onSuccess: (List<ReservationModel>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(reservations.filter { it.entrantId == entrantId })
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
            ),
            listOf(
                Campsite(ownerId = "owner-1", campName = "Lakeside").apply { id = "camp-1" },
                Campsite(ownerId = "owner-2", campName = "Ridge").apply { id = "camp-2" }
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

        val removalRepo = FakeRemovalRepository()
        val controller = AdminController(
            userController,
            userRepo,
            campsiteRepo,
            lotteryRepo,
            NotificationController(notificationRepo),
            removalRepo
        )

        var success: Boolean? = null
        var message: String = ""

        init {
            userModel.setCurrentUser(admin)
            removalRepo.onLotteryDeleted = { id -> lotteryRepo.lotteries.removeAll { it.id == id } }
            removalRepo.onLotterySaved = { saved ->
                lotteryRepo.lotteries.removeAll { it.id == saved.id }
                lotteryRepo.lotteries.add(saved)
            }
        }

        fun delete(userId: String, reason: RemovalReason?) {
            controller.deleteProfile(userId, reason) { ok, text ->
                success = ok
                message = text
            }
        }

        fun deleteCampsite(campsiteId: String, reason: RemovalReason?) {
            controller.deleteCampsite(campsiteId, reason) { ok, text ->
                success = ok
                message = text
            }
        }
    }

    @Test
    fun test_ANonAdminCannotDeleteAnything() {
        val s = Setup(listOf(Role.OWNER))

        s.delete("user-9", null)

        Assert.assertEquals("the request is refused", false, s.success)
        Assert.assertTrue("the profile is still there", s.userRepo.users.any { it.id == "user-9" })
        Assert.assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_AnAdminCannotDeleteTheirOwnProfile() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("admin-1", null)

        Assert.assertEquals("the request is refused", false, s.success)
        Assert.assertTrue("the admin is still there", s.userRepo.users.any { it.id == "admin-1" })
    }

    @Test
    fun test_AnAdminCannotDeleteAnotherAdmin() {
        val s = Setup(listOf(Role.ADMIN))
        val otherAdmin = User(deviceId = "admin-2", name = "Dee", roles = listOf(Role.ADMIN))
        s.userRepo.users.add(otherAdmin)
        s.campsiteRepo.ratings.add(Rating("camp-1", "admin-2", 5))

        s.delete("admin-2", RemovalReason.OTHER)

        Assert.assertEquals("the request is refused", false, s.success)
        Assert.assertTrue(
            "the other admin is still there",
            s.userRepo.users.any { it.id == "admin-2" })
        Assert.assertTrue(
            "their data is untouched",
            s.campsiteRepo.ratings.any { it.userId == "admin-2" }
        )
        Assert.assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_AProfileThatNoLongerExistsChangesNothing() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("user-404", null)

        Assert.assertEquals("the request is refused", false, s.success)
        Assert.assertEquals("all three profiles remain", 3, s.userRepo.users.size)
    }

    @Test
    fun test_ABlankIdChangesNothing() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("  ", null)

        Assert.assertEquals("the request is refused", false, s.success)
        Assert.assertEquals("all three profiles remain", 3, s.userRepo.users.size)
    }

    @Test
    fun test_DeletingAProfileRemovesItsRatingsAndWaitingListEntriesOnly() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("user-9", null)

        Assert.assertEquals("the deletion worked", true, s.success)
        Assert.assertFalse("the profile is gone", s.userRepo.users.any { it.id == "user-9" })
        Assert.assertEquals("the other two profiles remain", 2, s.userRepo.users.size)
        Assert.assertEquals(
            "only the other user's rating remains",
            listOf("user-2"),
            s.campsiteRepo.ratings.map { it.userId }
        )
        Assert.assertEquals(
            "only the other user's waiting-list entry remains",
            listOf("user-2"),
            s.lotteryRepo.entries.map { it.userId }
        )
    }

    @Test
    fun test_TheListOfUsersIsReloadedAfterADeletion() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("user-9", null)

        Assert.assertFalse(
            "the list the Profiles screen shows no longer holds the deleted user",
            s.userModel.allUsers.any { it.id == "user-9" }
        )
        Assert.assertEquals("it still holds everyone else", 2, s.userModel.allUsers.size)
    }

    @Test
    fun test_TheRemovedUserIsToldWhyWhenAReasonWasPicked() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("user-9", RemovalReason.HARASSMENT)

        val notices = s.notificationRepo.saved.values.toList()
        Assert.assertEquals("exactly one notice was saved", 1, notices.size)
        Assert.assertEquals("it is addressed to the removed user", "user-9", notices[0].recipientId)
        Assert.assertTrue(
            "it states the reason picked",
            notices[0].message.contains(RemovalReason.HARASSMENT.label)
        )
    }

    @Test
    fun test_TheNoticeStillGoesOutWithoutAReason() {
        val s = Setup(listOf(Role.ADMIN))

        s.delete("user-9", null)

        val notices = s.notificationRepo.saved.values.toList()
        Assert.assertEquals("exactly one notice was saved", 1, notices.size)
        Assert.assertFalse("no reason is mentioned", notices[0].message.contains("Reason"))
    }

    @Test
    fun test_AFailedCleanupKeepsTheProfileAndSendsNoNotice() {
        val s = Setup(listOf(Role.ADMIN))
        s.campsiteRepo.failRatingDelete = true

        s.delete("user-9", null)

        Assert.assertEquals("the request reports failure", false, s.success)
        Assert.assertTrue(
            "the profile is kept so the admin can try again",
            s.userRepo.users.any { it.id == "user-9" })
        Assert.assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_AFailedProfileDeleteReportsFailureAndSendsNoNotice() {
        val s = Setup(listOf(Role.ADMIN))
        s.userRepo.failDelete = true

        s.delete("user-9", RemovalReason.FRAUD)

        Assert.assertEquals("the request reports failure", false, s.success)
        Assert.assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    // ---- US 03.01.01: an admin deletes a campsite ----

    @Test
    fun test_ANonAdminCannotDeleteACampsite() {
        val s = Setup(listOf(Role.OWNER))

        s.deleteCampsite("camp-1", RemovalReason.SPAM_OR_FAKE)

        Assert.assertEquals("the request is refused", false, s.success)
        Assert.assertTrue(
            "the campsite is still there",
            s.campsiteRepo.campsites.any { it.id == "camp-1" })
        Assert.assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_ABlankCampsiteIdChangesNothing() {
        val s = Setup(listOf(Role.ADMIN))

        s.deleteCampsite("  ", RemovalReason.SPAM_OR_FAKE)

        Assert.assertEquals("the request is refused", false, s.success)
        Assert.assertEquals("both campsites remain", 2, s.campsiteRepo.campsites.size)
    }

    @Test
    fun test_ACampsiteThatNoLongerExistsChangesNothing() {
        val s = Setup(listOf(Role.ADMIN))

        s.deleteCampsite("camp-404", RemovalReason.SPAM_OR_FAKE)

        Assert.assertEquals("the request is refused", false, s.success)
        Assert.assertEquals("both campsites remain", 2, s.campsiteRepo.campsites.size)
        Assert.assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_DeletingACampsiteRemovesItAndItsRatingsOnly() {
        val s = Setup(listOf(Role.ADMIN))

        s.deleteCampsite("camp-1", RemovalReason.FRAUD)

        Assert.assertEquals("the deletion worked", true, s.success)
        Assert.assertEquals(
            "only the other campsite remains",
            listOf("camp-2"),
            s.campsiteRepo.campsites.map { it.id })
        Assert.assertEquals(
            "only the other campsite's rating remains",
            listOf("camp-2"),
            s.campsiteRepo.ratings.map { it.campsiteId }
        )
    }

    @Test
    fun test_TheOwnerIsToldWhichCampsiteWasRemovedAndWhy() {
        val s = Setup(listOf(Role.ADMIN))

        s.deleteCampsite("camp-1", RemovalReason.INAPPROPRIATE_CONTENT)

        val notices = s.notificationRepo.saved.values.toList()
        Assert.assertEquals("exactly one notice was saved", 1, notices.size)
        Assert.assertEquals(
            "it is addressed to the campsite's owner",
            "owner-1",
            notices[0].recipientId
        )
        Assert.assertTrue("it names the campsite", notices[0].message.contains("Lakeside"))
        Assert.assertTrue(
            "it states the reason picked",
            notices[0].message.contains(RemovalReason.INAPPROPRIATE_CONTENT.label)
        )
    }

    @Test
    fun test_TheOwnerNoticeStillGoesOutWithoutAReason() {
        val s = Setup(listOf(Role.ADMIN))

        s.deleteCampsite("camp-1", null)

        val notices = s.notificationRepo.saved.values.toList()
        Assert.assertEquals("exactly one notice was saved", 1, notices.size)
        Assert.assertFalse("no reason is mentioned", notices[0].message.contains("Reason"))
    }

    @Test
    fun test_AFailedRatingCleanupKeepsTheCampsiteAndSendsNoNotice() {
        val s = Setup(listOf(Role.ADMIN))
        s.campsiteRepo.failRatingDelete = true

        s.deleteCampsite("camp-1", RemovalReason.FRAUD)

        Assert.assertEquals("the request reports failure", false, s.success)
        Assert.assertTrue(
            "the campsite is kept so the admin can try again",
            s.campsiteRepo.campsites.any { it.id == "camp-1" })
        Assert.assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_AFailedCampsiteDeleteReportsFailureAndSendsNoNotice() {
        val s = Setup(listOf(Role.ADMIN))
        s.campsiteRepo.failCampsiteDelete = true

        s.deleteCampsite("camp-1", RemovalReason.FRAUD)

        Assert.assertEquals("the request reports failure", false, s.success)
        Assert.assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    // ---- US 03.01.01: what happens to reservations and lotteries ----

    private fun noticesTo(s: Setup, userId: String): List<com.example.campbnb.models.Notification> {
        return s.notificationRepo.saved.values.filter { it.recipientId == userId }
    }

    @Test
    fun test_DeletingACampsiteCancelsItsReservationsAndTellsTheEntrants() {
        val s = Setup(listOf(Role.ADMIN))
        s.removalRepo.reservations.add(
            ReservationModel(
                lotteryEntryId = "entry-1", campsiteId = "camp-1", entrantId = "user-5",
                startDate = "2026-12-01", endDate = "2026-12-03"
            )
        )
        s.removalRepo.reservations.add(
            ReservationModel(lotteryEntryId = "entry-2", campsiteId = "camp-2", entrantId = "user-6")
        )

        s.deleteCampsite("camp-1", RemovalReason.FRAUD)

        assertEquals("the deletion worked", true, s.success)
        assertEquals("only this campsite's reservation was cancelled", listOf("entry-1"), s.removalRepo.cancelled)
        val notices = noticesTo(s, "user-5")
        assertEquals("the entrant was told once", 1, notices.size)
        assertTrue("and it names the campsite", notices[0].message.contains("Lakeside"))
        assertEquals("the other campsite's entrant was not told", 0, noticesTo(s, "user-6").size)
    }

    @Test
    fun test_ALotteryForOnlyThisCampsiteIsDeletedWithItsWaitingList() {
        val s = Setup(listOf(Role.ADMIN))
        s.lotteryRepo.lotteries.add(
            Lottery(ownerId = "owner-1", campsiteIds = listOf("camp-1")).apply { id = "lot-1" }
        )

        s.deleteCampsite("camp-1", null)

        assertEquals("the deletion worked", true, s.success)
        assertEquals("the lottery was deleted", listOf("lot-1"), s.removalRepo.deletedLotteryIds)
        assertTrue("its waiting list is gone", s.lotteryRepo.entries.none { it.lotteryId == "lot-1" })
        assertEquals("the first entrant was told", 1, noticesTo(s, "user-9").size)
        assertEquals("the second entrant was told", 1, noticesTo(s, "user-2").size)
    }

    @Test
    fun test_ALotteryWithSeveralCampsitesLosesOnlyThisOne() {
        val s = Setup(listOf(Role.ADMIN))
        s.lotteryRepo.lotteries.add(
            Lottery(ownerId = "owner-1", campsiteIds = listOf("camp-1", "camp-2")).apply { id = "lot-multi" }
        )
        s.lotteryRepo.entries.add(WaitingListEntry("lot-multi", "user-5"))

        s.deleteCampsite("camp-1", null)

        assertEquals("the deletion worked", true, s.success)
        assertTrue("the lottery was not deleted", s.removalRepo.deletedLotteryIds.isEmpty())
        assertEquals("it was saved once", 1, s.removalRepo.savedLotteries.size)
        assertEquals("under the same id", "lot-multi", s.removalRepo.savedLotteries[0].id)
        assertEquals("with only the other campsite", listOf("camp-2"), s.removalRepo.savedLotteries[0].campsiteIds)
        assertTrue(
            "the entrant is still on the waiting list",
            s.lotteryRepo.entries.any { it.lotteryId == "lot-multi" && it.userId == "user-5" }
        )
        assertEquals("and was told a campsite was removed", 1, noticesTo(s, "user-5").size)
    }

    @Test
    fun test_ALotteryForAnotherCampsiteIsLeftAlone() {
        val s = Setup(listOf(Role.ADMIN))
        s.lotteryRepo.lotteries.add(
            Lottery(ownerId = "owner-2", campsiteIds = listOf("camp-2")).apply { id = "lot-other" }
        )

        s.deleteCampsite("camp-1", null)

        assertTrue("nothing was deleted", s.removalRepo.deletedLotteryIds.isEmpty())
        assertTrue("nothing was saved", s.removalRepo.savedLotteries.isEmpty())
    }

    @Test
    fun test_AFailedReservationCancelKeepsTheCampsiteAndSendsNoNotice() {
        val s = Setup(listOf(Role.ADMIN))
        s.removalRepo.reservations.add(
            ReservationModel(lotteryEntryId = "entry-1", campsiteId = "camp-1", entrantId = "user-5")
        )
        s.removalRepo.failCancel = true

        s.deleteCampsite("camp-1", RemovalReason.FRAUD)

        assertEquals("the request reports failure", false, s.success)
        assertTrue("the campsite is kept so the admin can try again", s.campsiteRepo.campsites.any { it.id == "camp-1" })
        assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_DeletingAProfileCancelsItsReservationsOnly() {
        val s = Setup(listOf(Role.ADMIN))
        s.removalRepo.reservations.add(
            ReservationModel(lotteryEntryId = "entry-1", campsiteId = "camp-1", entrantId = "user-9")
        )
        s.removalRepo.reservations.add(
            ReservationModel(lotteryEntryId = "entry-2", campsiteId = "camp-1", entrantId = "user-2")
        )

        s.delete("user-9", null)

        assertEquals("the deletion worked", true, s.success)
        assertEquals("only the deleted user's reservation was cancelled", listOf("entry-1"), s.removalRepo.cancelled)
    }

    @Test
    fun test_AFailedReservationCancelKeepsTheProfileAndSendsNoNotice() {
        val s = Setup(listOf(Role.ADMIN))
        s.removalRepo.reservations.add(
            ReservationModel(lotteryEntryId = "entry-1", campsiteId = "camp-1", entrantId = "user-9")
        )
        s.removalRepo.failCancel = true

        s.delete("user-9", null)

        assertEquals("the request reports failure", false, s.success)
        assertTrue("the profile is kept so the admin can try again", s.userRepo.users.any { it.id == "user-9" })
        assertEquals("nothing was announced", 0, s.notificationRepo.saved.size)
    }

    // ---- US 03.07.01: an admin removes a campsite owner ----

    private fun addOwner(s: Setup) {
        s.userRepo.users.add(User(deviceId = "owner-1", name = "Olive", roles = listOf(Role.OWNER)))
    }

    private fun removeOwner(s: Setup, userId: String, reason: RemovalReason?) {
        s.controller.removeOwner(userId, reason) { ok, text ->
            s.success = ok
            s.message = text
        }
    }

    @Test
    fun test_RemovingAnOwnerNeedsAReason() {
        val s = Setup(listOf(Role.ADMIN))
        addOwner(s)

        removeOwner(s, "owner-1", null)

        assertEquals("the request is refused", false, s.success)
        assertTrue("the owner is still there", s.userRepo.users.any { it.id == "owner-1" })
        assertTrue("their campsite is still there", s.campsiteRepo.campsites.any { it.id == "camp-1" })
    }

    @Test
    fun test_OnlyAnOwnerCanBeRemovedThisWay() {
        val s = Setup(listOf(Role.ADMIN))

        removeOwner(s, "user-9", RemovalReason.FRAUD)

        assertEquals("the request is refused", false, s.success)
        assertTrue("the entrant is still there", s.userRepo.users.any { it.id == "user-9" })
    }

    @Test
    fun test_ANonAdminCannotRemoveAnOwner() {
        val s = Setup(listOf(Role.OWNER))
        addOwner(s)

        removeOwner(s, "owner-1", RemovalReason.FRAUD)

        assertEquals("the request is refused", false, s.success)
        assertTrue("the owner is still there", s.userRepo.users.any { it.id == "owner-1" })
    }

    @Test
    fun test_RemovingAnOwnerDeletesTheirCampsitesOnly() {
        val s = Setup(listOf(Role.ADMIN))
        addOwner(s)

        removeOwner(s, "owner-1", RemovalReason.SPAM_OR_FAKE)

        assertEquals("the removal worked", true, s.success)
        Assert.assertFalse("the owner is gone", s.userRepo.users.any { it.id == "owner-1" })
        assertEquals(
            "only the other owner's campsite remains",
            listOf("camp-2"),
            s.campsiteRepo.campsites.map { it.id }
        )
        assertTrue(
            "the other campsite's ratings are untouched",
            s.campsiteRepo.ratings.any { it.campsiteId == "camp-2" }
        )
    }

    @Test
    fun test_RemovingAnOwnerDeletesTheirLotteriesAndTellsEveryEntrantOnce() {
        val s = Setup(listOf(Role.ADMIN))
        addOwner(s)
        s.lotteryRepo.lotteries.add(
            Lottery(ownerId = "owner-1", campsiteIds = listOf("camp-1")).apply { id = "lot-1" }
        )

        removeOwner(s, "owner-1", RemovalReason.FRAUD)

        assertEquals("the removal worked", true, s.success)
        assertEquals("the lottery was deleted once", listOf("lot-1"), s.removalRepo.deletedLotteryIds)
        assertTrue("its waiting list is gone", s.lotteryRepo.entries.none { it.lotteryId == "lot-1" })
        assertEquals("the first entrant was told once", 1, noticesTo(s, "user-9").size)
        assertEquals("the second entrant was told once", 1, noticesTo(s, "user-2").size)
        assertTrue(
            "and it says why the site is gone",
            noticesTo(s, "user-9")[0].message.contains("no longer has an owner")
        )
    }

    @Test
    fun test_ADrawnLotterysWinnersAreToldEvenWithoutAWaitingListEntry() {
        val s = Setup(listOf(Role.ADMIN))
        addOwner(s)
        s.lotteryRepo.lotteries.add(
            Lottery(
                ownerId = "owner-1",
                campsiteIds = listOf("camp-1"),
                winnerIds = listOf("user-7")
            ).apply { id = "lot-w" }
        )

        removeOwner(s, "owner-1", RemovalReason.FRAUD)

        assertEquals("the removal worked", true, s.success)
        assertEquals("the winner was told once", 1, noticesTo(s, "user-7").size)
    }

    @Test
    fun test_RemovingAnOwnerCancelsReservationsAtTheirCampsitesAndTellsTheEntrants() {
        val s = Setup(listOf(Role.ADMIN))
        addOwner(s)
        s.removalRepo.reservations.add(
            ReservationModel(
                lotteryEntryId = "entry-1", campsiteId = "camp-1", entrantId = "user-5",
                startDate = "2026-12-01", endDate = "2026-12-03"
            )
        )
        s.removalRepo.reservations.add(
            ReservationModel(lotteryEntryId = "entry-2", campsiteId = "camp-2", entrantId = "user-6")
        )

        removeOwner(s, "owner-1", RemovalReason.FRAUD)

        assertEquals(
            "only the owner's campsite reservation was cancelled",
            listOf("entry-1"),
            s.removalRepo.cancelled
        )
        val notices = noticesTo(s, "user-5")
        assertEquals("the entrant was told once", 1, notices.size)
        assertTrue("and it names the campsite", notices[0].message.contains("Lakeside"))
        assertEquals("the other campsite's entrant was not told", 0, noticesTo(s, "user-6").size)
    }

    @Test
    fun test_AnotherOwnersLotteryOnlyLosesTheRemovedOwnersCampsite() {
        val s = Setup(listOf(Role.ADMIN))
        addOwner(s)
        s.lotteryRepo.lotteries.add(
            Lottery(ownerId = "owner-2", campsiteIds = listOf("camp-1", "camp-2")).apply { id = "lot-multi" }
        )
        s.lotteryRepo.entries.add(WaitingListEntry("lot-multi", "user-5"))

        removeOwner(s, "owner-1", RemovalReason.FRAUD)

        assertTrue("the lottery was not deleted", s.removalRepo.deletedLotteryIds.isEmpty())
        assertEquals("it was saved once", 1, s.removalRepo.savedLotteries.size)
        assertEquals(
            "with only the other campsite",
            listOf("camp-2"),
            s.removalRepo.savedLotteries[0].campsiteIds
        )
        assertEquals("its entrant was told once", 1, noticesTo(s, "user-5").size)
    }

    @Test
    fun test_TheRemovedOwnerIsToldTheirCampsitesAndLotteriesWentToo() {
        val s = Setup(listOf(Role.ADMIN))
        addOwner(s)

        removeOwner(s, "owner-1", RemovalReason.HARASSMENT)

        val notices = noticesTo(s, "owner-1")
        assertEquals("the owner got one notice, not one per campsite", 1, notices.size)
        assertTrue("it says their campsites went too", notices[0].message.contains("campsites and lotteries"))
        assertTrue("and states the reason", notices[0].message.contains(RemovalReason.HARASSMENT.label))
    }

    @Test
    fun test_AFailedCancelKeepsTheOwnerAndTheirCampsite() {
        val s = Setup(listOf(Role.ADMIN))
        addOwner(s)
        s.removalRepo.reservations.add(
            ReservationModel(lotteryEntryId = "entry-1", campsiteId = "camp-1", entrantId = "user-5")
        )
        s.removalRepo.failCancel = true

        removeOwner(s, "owner-1", RemovalReason.FRAUD)

        assertEquals("the request reports failure", false, s.success)
        assertTrue("the owner is kept so the admin can try again", s.userRepo.users.any { it.id == "owner-1" })
        assertTrue("their campsite is kept", s.campsiteRepo.campsites.any { it.id == "camp-1" })
        assertEquals("nobody was told anything", 0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_TheGenericDeleteProfileButtonAlsoCleansUpAnOwner() {
        val s = Setup(listOf(Role.ADMIN))
        addOwner(s)

        s.delete("owner-1", null)

        assertEquals("the deletion worked", true, s.success)
        Assert.assertFalse("their campsite went with them", s.campsiteRepo.campsites.any { it.id == "camp-1" })
    }
}