package com.example.campbnb.adminTests

// tests created with the help of Claude Sonnet 5.5

/**
 * Intent (UI) tests for US 03.01.01: an admin deletes a campsite that violates app policy.
 *
 * Each test drives the real campsite details screen the way an admin would (tap the red
 * "Delete Site" button, answer the confirmation pop-up, then pick a policy reason in the
 * second pop-up) and checks what happened to the campsite and what its owner was told.
 * Repositories are in-memory fakes, so nothing touches Firebase.
 */

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.example.campbnb.data.LotteryRepository
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
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.details.CampsiteDetails
import com.example.campbnb.ui.details.CampsiteDetailsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory campsites and ratings; never touches Firebase. */
private class CampsiteUiFakeCampsiteRepository : CampsiteRepository() {
    val campsites = mutableListOf(
        Campsite(ownerId = "owner-1", campName = "Lakeside").apply { id = "camp-1" }
    )
    val ratings = mutableListOf(Rating("camp-1", "user-9", 4))

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

    override fun deleteRating(ratingId: String, onComplete: (Result<Exception?>) -> Unit) {
        ratings.removeAll { it.id == ratingId }
        onComplete(Result.success(null))
    }

    override fun deleteCampsite(campsiteId: String, onComplete: (Result<Exception?>) -> Unit) {
        campsites.removeAll { it.id == campsiteId }
        onComplete(Result.success(null))
    }
}

/** Remembers the notifications saved, so a test can read what the owner was told. */
private class CampsiteUiFakeNotificationRepository : NotificationRepository {
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
class AdminDeleteCampsiteUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val signedInAdmin = User(
        deviceId = "admin-1", name = "Ada Admin", email = "ada@example.com",
        roles = listOf(Role.ADMIN)
    )

    private val campsiteRepo = CampsiteUiFakeCampsiteRepository()
    private val notifications = CampsiteUiFakeNotificationRepository()
    private var lastMessage = ""

    /** Shows the details screen of "Lakeside" the way the app does, for a viewer with [role]. */
    private fun showDetailsScreen(role: UserRole) {
        val userModel = UserModel()
        val userController = UserController(userModel, UserRepository())
        val adminController = AdminController(
            userController,
            UserRepository(),
            campsiteRepo,
            EmptyLotteryRepository(),
            NotificationController(notifications),
            EmptyRemovalRepository()
        )
        userModel.setCurrentUser(signedInAdmin)

        composeRule.setContent {
            CampsiteDetailsScreen(
                role = role,
                campsite = CampsiteDetails(id = "camp-1", name = "Lakeside"),
                onBack = {},
                onTabSelected = {},
                onDeleteSite = { reason ->
                    adminController.deleteCampsite("camp-1", reason) { _, message ->
                        lastMessage = message
                    }
                }
            )
        }
    }

    /** Taps Delete Site, then Delete in the first pop-up, which opens the policy pop-up. */
    private fun openPolicyPopUp() {
        composeRule.onNodeWithTag("delete_site").performClick()
        composeRule.onNodeWithText("Delete this site?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_site").performClick()
    }

    @Test
    fun deletingASite_afterBothPopUps_removesItAndTellsTheOwnerWhy() {
        showDetailsScreen(UserRole.ADMIN)

        openPolicyPopUp()
        composeRule.onNodeWithText("Which policy was broken?").assertIsDisplayed()
        composeRule.onNodeWithTag("reason_" + RemovalReason.SPAM_OR_FAKE.name).performClick()
        composeRule.onNodeWithTag("confirm_removal").performClick()

        assertEquals("the admin is told it worked", "Campsite deleted", lastMessage)
        assertTrue("the campsite is gone", campsiteRepo.campsites.isEmpty())
        assertTrue("its ratings went with it", campsiteRepo.ratings.isEmpty())
        assertEquals("the owner was sent one notice", 1, notifications.saved.size)
        assertEquals("addressed to them", "owner-1", notifications.saved[0].recipientId)
        assertTrue("it names the campsite", notifications.saved[0].message.contains("Lakeside"))
        assertTrue(
            "and it says why",
            notifications.saved[0].message.contains(RemovalReason.SPAM_OR_FAKE.label)
        )
    }

    @Test
    fun cancellingTheFirstPopUp_keepsTheSite() {
        showDetailsScreen(UserRole.ADMIN)

        composeRule.onNodeWithTag("delete_site").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Delete this site?").assertDoesNotExist()
        composeRule.onNodeWithText("Which policy was broken?").assertDoesNotExist()
        assertEquals("the campsite is still there", 1, campsiteRepo.campsites.size)
        assertTrue("nobody was told anything", notifications.saved.isEmpty())
    }

    @Test
    fun thePolicyPopUp_cannotBeConfirmedUntilAReasonIsPicked() {
        showDetailsScreen(UserRole.ADMIN)

        openPolicyPopUp()
        composeRule.onNodeWithTag("confirm_removal").assertIsNotEnabled()

        composeRule.onNodeWithTag("reason_" + RemovalReason.FRAUD.name).performClick()
        composeRule.onNodeWithTag("confirm_removal").assertIsEnabled()
    }

    @Test
    fun cancellingThePolicyPopUp_keepsTheSite() {
        showDetailsScreen(UserRole.ADMIN)

        openPolicyPopUp()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Which policy was broken?").assertDoesNotExist()
        assertEquals("the campsite is still there", 1, campsiteRepo.campsites.size)
        assertTrue("nobody was told anything", notifications.saved.isEmpty())
    }

    @Test
    fun anOwner_isNotOfferedDeleteSite() {
        showDetailsScreen(UserRole.OWNER)

        composeRule.onNodeWithTag("delete_site").assertDoesNotExist()
    }
}