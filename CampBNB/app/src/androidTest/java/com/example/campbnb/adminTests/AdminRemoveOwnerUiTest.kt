package com.example.campbnb.adminTests

// tests created with the help of Claude Sonnet 5.5

/**
 * Intent (UI) tests for US 03.07.01: an admin removes a campsite owner who violates app policy.
 *
 * Each test drives the real Profiles screen the way an admin would (tap a row's settings icon,
 * tap the red "Remove Profile for App Policy Violation" button, pick a policy, confirm) and
 * checks what the admin sees afterwards. Repositories are in-memory fakes, so nothing touches
 * Firebase.
 */

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campbnb.EmptyLotteryRepository
import com.example.campbnb.EmptyRemovalRepository
import com.example.campbnb.controller.AdminController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.NotificationCommand
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Notification
import com.example.campbnb.models.Rating
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.admin.AdminProfilesScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory users; never touches Firebase. */
private class OwnerUiFakeUserRepository(users: List<User>) : UserRepository() {
    val users = users.toMutableList()

    override fun findByDeviceId(
        deviceId: String,
        onSuccess: (User?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(users.firstOrNull { it.id == deviceId })
    }

    override fun delete(userId: String, onComplete: (Result<Exception?>) -> Unit) {
        users.removeAll { it.id == userId }
        onComplete(Result.success(null))
    }

    override fun getAll(onSuccess: (List<User>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(users.toList())
    }
}

/** In-memory campsites with no ratings; never touches Firebase. */
private class OwnerUiFakeCampsiteRepository(campsites: List<Campsite>) : CampsiteRepository() {
    val campsites = campsites.toMutableList()

    override fun getByOwner(
        ownerId: String,
        onSuccess: (List<Campsite>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(campsites.filter { it.ownerId == ownerId })
    }

    override fun getRatingsByCampsite(
        campsiteId: String,
        onSuccess: (List<Rating>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(emptyList())
    }

    override fun getRatingsByUser(
        userId: String,
        onSuccess: (List<Rating>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(emptyList())
    }

    override fun deleteRating(ratingId: String, onComplete: (Result<Exception?>) -> Unit) {
        onComplete(Result.success(null))
    }

    override fun deleteCampsite(campsiteId: String, onComplete: (Result<Exception?>) -> Unit) {
        campsites.removeAll { it.id == campsiteId }
        onComplete(Result.success(null))
    }
}

/** Remembers the notifications saved, so a test can read what the removed owner was told. */
private class OwnerUiFakeNotificationRepository : NotificationRepository {
    val saved = mutableListOf<Notification>()

    override fun run(command: NotificationCommand) {
        when (command) {
            is NotificationCommand.Save -> {
                saved.addAll(command.records)
                command.onDone.invoke(Result.success(Unit))
            }
            is NotificationCommand.Read -> {
                command.onSuccess.invoke(saved.toList())
            }
            is NotificationCommand.Watch -> {
                command.onListening.invoke {}
            }
            is NotificationCommand.MarkRead -> {
                command.onDone.invoke(Result.success(Unit))
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class AdminRemoveOwnerUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val signedInAdmin = User(
        deviceId = "admin-1", name = "Ada Admin", email = "ada@example.com",
        roles = listOf(Role.ADMIN)
    )
    private val owner = User(
        deviceId = "owner-1", name = "Olive Owner", email = "olive@example.com",
        roles = listOf(Role.OWNER)
    )
    private val entrant = User(
        deviceId = "user-9", name = "Robin Entrant", email = "robin@example.com",
        roles = listOf(Role.ENTRANT)
    )

    private val notifications = OwnerUiFakeNotificationRepository()
    private val campsiteRepo = OwnerUiFakeCampsiteRepository(
        listOf(Campsite(ownerId = "owner-1", campName = "Lakeside").apply { id = "camp-1" })
    )

    /** Shows the Profiles screen the way the app does, with the admin signed in. */
    private fun showProfilesScreen() {
        val userRepo = OwnerUiFakeUserRepository(listOf(signedInAdmin, owner, entrant))
        val userModel = UserModel()
        val userController = UserController(userModel, userRepo)
        val adminController = AdminController(
            userController,
            userRepo,
            campsiteRepo,
            EmptyLotteryRepository(),
            NotificationController(notifications),
            EmptyRemovalRepository()
        )
        userModel.setCurrentUser(signedInAdmin)
        userController.loadAllUsers()

        composeRule.setContent {
            AdminProfilesScreen({}, userModel, adminController)
        }
    }

    /** Taps the row's settings icon, then its red remove button. */
    private fun openRemoveOwnerPopUpFor(userId: String) {
        composeRule.onNodeWithTag("settings_" + userId).performClick()
        composeRule.onNodeWithTag("remove_owner_" + userId).performClick()
    }

    @Test
    fun anOwnersSettingsPanel_offersTheRemoveButton() {
        showProfilesScreen()

        composeRule.onNodeWithTag("settings_owner-1").performClick()

        composeRule.onNodeWithTag("remove_owner_owner-1").assertIsDisplayed()
    }

    @Test
    fun anEntrantsSettingsPanel_offersNoRemoveOwnerButton() {
        showProfilesScreen()

        composeRule.onNodeWithTag("settings_user-9").performClick()

        composeRule.onNodeWithTag("remove_owner_user-9").assertDoesNotExist()
        composeRule.onNodeWithTag("delete_profile_user-9").assertIsDisplayed()
    }

    @Test
    fun removingAnOwner_needsAReason_thenRemovesTheProfileAndCampsites() {
        showProfilesScreen()

        openRemoveOwnerPopUpFor("owner-1")
        composeRule.onNodeWithText("Remove this campsite owner?").assertIsDisplayed()

        // Without a policy picked, nothing is removed.
        composeRule.onNodeWithTag("confirm_removal").performClick()
        composeRule.onNodeWithText("olive@example.com", substring = true).assertIsDisplayed()
        assertEquals("the campsite is still there", 1, campsiteRepo.campsites.size)

        composeRule.onNodeWithTag("reason_" + RemovalReason.FRAUD.name).performClick()
        composeRule.onNodeWithTag("confirm_removal").performClick()

        composeRule.onNodeWithText("olive@example.com", substring = true).assertDoesNotExist()
        assertTrue("their campsites went too", campsiteRepo.campsites.isEmpty())
        assertEquals("the owner was sent one notice", 1, notifications.saved.size)
        assertEquals("addressed to them", "owner-1", notifications.saved[0].recipientId)
        assertTrue(
            "and it says why",
            notifications.saved[0].message.contains(RemovalReason.FRAUD.label)
        )
    }

    @Test
    fun cancellingThePopUp_keepsTheOwnerAndTheirCampsites() {
        showProfilesScreen()

        openRemoveOwnerPopUpFor("owner-1")
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Remove this campsite owner?").assertDoesNotExist()
        composeRule.onNodeWithText("olive@example.com", substring = true).assertIsDisplayed()
        assertEquals("the campsite is still there", 1, campsiteRepo.campsites.size)
        assertEquals("nobody was told anything", 0, notifications.saved.size)
    }
}