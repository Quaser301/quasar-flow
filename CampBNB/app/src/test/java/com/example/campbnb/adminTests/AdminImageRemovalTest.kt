package com.example.campbnb.adminTests

// tests created with the help of Claude Sonnet 5.5

import com.example.campbnb.controller.AdminController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.CampsiteRemovalRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.ImageKind
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel
import com.example.campbnb.models.uploadedImagesFor
import com.example.campbnb.notificationTests.FakeNotificationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AdminController's image removal (US 03.03.01) and image browsing (US 03.06.01), tested with
 * fake repositories so nothing touches Firebase: only an admin can do either, the picture is
 * emptied and nothing else on the campsite or profile changes, the uploader is told (with the
 * reason), a failed write tells nobody, and the browser lists exactly the pictures that exist.
 */
class AdminImageRemovalTest {

    private class FakeUserRepository(users: List<User>) : UserRepository() {
        val users = users.toMutableList()
        var failWrite = false

        override fun findByDeviceId(deviceId: String, onSuccess: (User?) -> Unit, onFailure: (Exception) -> Unit) {
            onSuccess(users.firstOrNull { it.id == deviceId })
        }

        override fun saveProfileImage(userId: String, image: String, onComplete: (Result<Exception?>) -> Unit) {
            if (failWrite) {
                onComplete(Result.failure(Exception("fake failure")))
                return
            }
            val index = users.indexOfFirst { it.id == userId }
            users[index] = users[index].copy(profileImg = image)
            onComplete(Result.success(null))
        }

        override fun getAll(onSuccess: (List<User>) -> Unit, onFailure: (Exception) -> Unit) {
            onSuccess(users.toList())
        }
    }

    private class FakeCampsiteRepository(campsites: List<Campsite>) : CampsiteRepository() {
        val campsites = campsites.toMutableList()
        var failWrite = false

        override fun getAll(onSuccess: (List<Campsite>) -> Unit, onFailure: (Exception) -> Unit) {
            onSuccess(campsites.toList())
        }

        override fun getById(campsiteId: String, onSuccess: (Campsite?) -> Unit, onFailure: (Exception) -> Unit) {
            onSuccess(campsites.firstOrNull { it.id == campsiteId })
        }

        override fun saveCampsiteImage(campsiteId: String, image: String, onComplete: (Result<Exception?>) -> Unit) {
            if (failWrite) {
                onComplete(Result.failure(Exception("fake failure")))
                return
            }
            val index = campsites.indexOfFirst { it.id == campsiteId }
            val old = campsites[index]
            val updated = Campsite(ownerId = old.ownerId, campName = old.campName, campImg = image)
            updated.id = old.id
            campsites[index] = updated
            onComplete(Result.success(null))
        }
    }

    private class Setup(signedInRoles: List<Role>) {
        val admin = User(deviceId = "admin-1", name = "Ada", roles = signedInRoles)
        val entrant = User(deviceId = "user-9", name = "Bo", roles = listOf(Role.ENTRANT), profileImg = "pic-bo")
        val plain = User(deviceId = "user-2", name = "Cy", roles = listOf(Role.ENTRANT))

        val userRepo = FakeUserRepository(listOf(admin, entrant, plain))
        val campsiteRepo = FakeCampsiteRepository(
            listOf(
                Campsite(ownerId = "owner-1", campName = "Lakeside", campImg = "pic-lake").apply { id = "camp-1" },
                Campsite(ownerId = "owner-2", campName = "Ridge").apply { id = "camp-2" }
            )
        )
        val notificationRepo = FakeNotificationRepository()
        val userModel = UserModel()
        val controller = AdminController(
            UserController(userModel, userRepo),
            userRepo,
            campsiteRepo,
            LotteryRepository(),
            NotificationController(notificationRepo),
            CampsiteRemovalRepository()
        )

        var success: Boolean? = null
        var message = ""

        init {
            userModel.setCurrentUser(admin)
        }

        fun remove(kind: ImageKind, id: String, reason: RemovalReason?) {
            controller.removeImage(kind, id, reason) { ok, text ->
                success = ok
                message = text
            }
        }
    }

    @Test
    fun test_ANonAdminCannotRemoveOrBrowseImages() {
        val s = Setup(listOf(Role.OWNER))

        s.remove(ImageKind.CAMPSITE, "camp-1", RemovalReason.SPAM_OR_FAKE)
        assertEquals(false, s.success)
        assertEquals("pic-lake", s.campsiteRepo.campsites[0].campImg)
        assertEquals(0, s.notificationRepo.saved.size)

        var browsed: Int? = null
        s.controller.loadUploadedImages { images, _ -> browsed = images.size }
        assertEquals("nothing is listed to a non-admin", 0, browsed)
    }

    @Test
    fun test_RemovingACampsiteImage_emptiesItAndTellsTheOwnerWhy() {
        val s = Setup(listOf(Role.ADMIN))

        s.remove(ImageKind.CAMPSITE, "camp-1", RemovalReason.INAPPROPRIATE_CONTENT)

        assertEquals(true, s.success)
        assertEquals("", s.campsiteRepo.campsites[0].campImg)
        assertEquals("the campsite itself is kept", "Lakeside", s.campsiteRepo.campsites[0].campName)
        val notices = s.notificationRepo.saved.values.toList()
        assertEquals(1, notices.size)
        assertEquals("owner-1", notices[0].recipientId)
        assertTrue(notices[0].message.contains("Lakeside"))
        assertTrue(notices[0].message.contains(RemovalReason.INAPPROPRIATE_CONTENT.label))
    }

    @Test
    fun test_RemovingAProfileImage_emptiesItAndTellsTheUser() {
        val s = Setup(listOf(Role.ADMIN))

        s.remove(ImageKind.PROFILE, "user-9", RemovalReason.HARASSMENT)

        assertEquals(true, s.success)
        assertEquals("", s.userRepo.users.first { it.id == "user-9" }.profileImg)
        assertEquals("the profile itself is kept", 3, s.userRepo.users.size)
        val notices = s.notificationRepo.saved.values.toList()
        assertEquals(1, notices.size)
        assertEquals("user-9", notices[0].recipientId)
        assertTrue(notices[0].message.contains(RemovalReason.HARASSMENT.label))
    }

    @Test
    fun test_ARemovalWithoutAReason_stillWorksAndSaysNoReason() {
        val s = Setup(listOf(Role.ADMIN))

        s.remove(ImageKind.CAMPSITE, "camp-1", null)

        assertEquals(true, s.success)
        assertFalse(s.notificationRepo.saved.values.first().message.contains("Reason"))
    }

    @Test
    fun test_AnImageThatIsGoneOrNeverExisted_isRefusedAndNobodyIsTold() {
        val s = Setup(listOf(Role.ADMIN))

        s.remove(ImageKind.CAMPSITE, "camp-2", null)
        assertEquals("a campsite with no picture", false, s.success)

        s.remove(ImageKind.CAMPSITE, "nope", null)
        assertEquals("a campsite that is gone", false, s.success)

        s.remove(ImageKind.PROFILE, "user-2", null)
        assertEquals("a profile with no picture", false, s.success)

        s.remove(ImageKind.PROFILE, " ", null)
        assertEquals("a blank id", false, s.success)

        assertEquals(0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_AFailedWrite_keepsTheImageAndTellsNobody() {
        val s = Setup(listOf(Role.ADMIN))
        s.campsiteRepo.failWrite = true
        s.userRepo.failWrite = true

        s.remove(ImageKind.CAMPSITE, "camp-1", null)
        assertEquals(false, s.success)
        s.remove(ImageKind.PROFILE, "user-9", null)
        assertEquals(false, s.success)

        assertEquals("pic-lake", s.campsiteRepo.campsites[0].campImg)
        assertEquals("pic-bo", s.userRepo.users.first { it.id == "user-9" }.profileImg)
        assertEquals(0, s.notificationRepo.saved.size)
    }

    @Test
    fun test_BrowsingListsEveryExistingPicture_campsitesThenProfiles() {
        val s = Setup(listOf(Role.ADMIN))

        var listed: List<String> = emptyList()
        var problem: String? = "unset"
        s.controller.loadUploadedImages { images, error ->
            listed = images.map { it.key }
            problem = error
        }

        assertNull(problem)
        assertEquals(listOf("CAMPSITE_camp-1", "PROFILE_user-9"), listed)
    }

    @Test
    fun test_ARemovedPicture_nolongerAppearsWhenBrowsing() {
        val s = Setup(listOf(Role.ADMIN))

        s.remove(ImageKind.CAMPSITE, "camp-1", null)
        var listed: List<String> = emptyList()
        s.controller.loadUploadedImages { images, _ -> listed = images.map { it.key } }

        assertEquals(listOf("PROFILE_user-9"), listed)
    }

    @Test
    fun test_uploadedImagesFor_skipsBlankAndOrdersByName() {
        val camps = listOf(
            Campsite(ownerId = "o", campName = "b", campImg = "x").apply { id = "2" },
            Campsite(ownerId = "o", campName = "A", campImg = "y").apply { id = "1" },
            Campsite(ownerId = "o", campName = "c").apply { id = "3" }
        )

        val keys = uploadedImagesFor(camps, emptyList()).map { it.key }

        assertEquals(listOf("CAMPSITE_1", "CAMPSITE_2"), keys)
    }
}