package com.example.campbnb.adminTests

// tests created with the help of Claude Sonnet 5.5

/**
 * Intent (UI) tests for US 03.02.01: an admin removes a profile that violates app policy.
 *
 * Each test drives the real Profiles screen the way an admin would (tap a row's settings
 * icon, tap the red "Delete Profile" button, answer the pop-up) and checks what the admin
 * sees afterwards. Repositories are in-memory fakes, so nothing touches Firebase.
 */

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campbnb.EmptyRemovalRepository
import com.example.campbnb.controller.AdminController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.NotificationCommand
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.Notification
import com.example.campbnb.models.Rating
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel
import com.example.campbnb.models.WaitingListEntry
import com.example.campbnb.ui.admin.AdminProfilesScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory users; never touches Firebase. */
private class UiFakeUserRepository(users: List<User>) : UserRepository() {
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

/** A campsite repository holding no ratings. */
private class UiFakeCampsiteRepository : CampsiteRepository() {
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
}

/** A lottery repository holding no waiting-list entries. */
private class UiFakeLotteryRepository : LotteryRepository() {
    override fun getEntriesByUser(
        userId: String,
        onSuccess: (List<WaitingListEntry>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(emptyList())
    }

    override fun removeEntry(
        lotteryId: String,
        userId: String,
        onComplete: (Result<Exception?>) -> Unit
    ) {
        onComplete(Result.success(null))
    }
}

/** Remembers the notifications saved, so a test can read what the removed user was told. */
private class UiFakeNotificationRepository : NotificationRepository {
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
class AdminDeleteProfileUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val signedInAdmin = User(
        deviceId = "admin-1", name = "Ada Admin", email = "ada@example.com",
        roles = listOf(Role.ADMIN)
    )
    private val otherAdmin = User(
        deviceId = "admin-2", name = "Dee Admin", email = "dee@example.com",
        roles = listOf(Role.ADMIN)
    )
    private val entrant = User(
        deviceId = "user-9", name = "Robin Target", email = "robin@example.com",
        roles = listOf(Role.ENTRANT)
    )

    private val notifications = UiFakeNotificationRepository()

    /** Shows the Profiles screen the way the app does, with the admin signed in. */
    private fun showProfilesScreen() {
        val userRepo = UiFakeUserRepository(listOf(signedInAdmin, otherAdmin, entrant))
        val userModel = UserModel()
        val userController = UserController(userModel, userRepo)
        val adminController = AdminController(
            userController,
            userRepo,
            UiFakeCampsiteRepository(),
            UiFakeLotteryRepository(),
            NotificationController(notifications),
            EmptyRemovalRepository()
        )
        userModel.setCurrentUser(signedInAdmin)
        userController.loadAllUsers()

        composeRule.setContent {
            AdminProfilesScreen({}, userModel, adminController)
        }
    }

    /** Taps the row's settings icon, then its red Delete Profile button. */
    private fun openDeletePopUpFor(userId: String) {
        composeRule.onNodeWithTag("settings_" + userId).performClick()
        composeRule.onNodeWithTag("delete_profile_" + userId).performClick()
    }

    @Test
    fun deletingAProfile_afterConfirming_removesItFromTheList() {
        showProfilesScreen()
        composeRule.onNodeWithText("robin@example.com", substring = true).assertIsDisplayed()

        openDeletePopUpFor("user-9")
        composeRule.onNodeWithText("Delete this profile?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_removal").performClick()

        composeRule.onNodeWithText("robin@example.com", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("profiles_message").assertIsDisplayed()
        composeRule.onNodeWithText("Profile deleted").assertIsDisplayed()
    }

    @Test
    fun cancellingThePopUp_keepsTheProfile() {
        showProfilesScreen()

        openDeletePopUpFor("user-9")
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Delete this profile?").assertDoesNotExist()
        composeRule.onNodeWithText("robin@example.com", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("profiles_message").assertDoesNotExist()
    }

    @Test
    fun theSettingsPanel_staysClosedUntilTheIconIsTapped() {
        showProfilesScreen()

        composeRule.onNodeWithTag("delete_profile_user-9").assertDoesNotExist()

        composeRule.onNodeWithTag("settings_user-9").performClick()

        composeRule.onNodeWithTag("delete_profile_user-9").assertIsDisplayed()
        composeRule.onNodeWithText("Delete Profile").assertIsDisplayed()
    }

    @Test
    fun anAdminProfile_offersNoDeleteButton() {
        showProfilesScreen()

        composeRule.onNodeWithTag("settings_admin-2").performClick()

        composeRule.onNodeWithText("Admin profiles cannot be deleted").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_profile_admin-2").assertDoesNotExist()
    }

    @Test
    fun aReasonPickedInThePopUp_isPassedOnToTheRemovedUser() {
        showProfilesScreen()

        openDeletePopUpFor("user-9")
        composeRule.onNodeWithTag("reason_" + RemovalReason.FRAUD.name).performClick()
        composeRule.onNodeWithTag("confirm_removal").performClick()

        composeRule.onNodeWithText("Profile deleted").assertIsDisplayed()
        assertEquals("the removed user was sent one notice", 1, notifications.saved.size)
        assertEquals("addressed to them", "user-9", notifications.saved[0].recipientId)
        assertTrue(
            "and it says why",
            notifications.saved[0].message.contains(RemovalReason.FRAUD.label)
        )
    }
}