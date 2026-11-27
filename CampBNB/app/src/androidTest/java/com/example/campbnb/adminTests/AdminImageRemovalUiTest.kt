package com.example.campbnb.adminTests

// tests created with the help of Claude Sonnet 5.5

/**
 * Intent (UI) tests for US 03.03.01 (an admin removes an image that violates policy) and
 * US 03.06.01 (an admin browses uploaded images so they can remove them).
 *
 * Each test drives the real screens the way an admin would: click the image, see the image
 * settings with the red "Remove image" button, confirm in the pop-up with a reason, and check
 * what the admin sees and what the uploader was told. Repositories are in-memory fakes.
 */

import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campbnb.imageTests.storedPicture
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.ImageKind
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel
import com.example.campbnb.controller.UserController
import com.example.campbnb.ui.admin.AdminImagesScreen
import com.example.campbnb.ui.admin.AdminProfilesScreen
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.details.CampsiteDetails
import com.example.campbnb.ui.details.CampsiteDetailsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdminImageRemovalUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val lakePicture = storedPicture(Color.GREEN)
    private val boPicture = storedPicture(Color.BLUE)

    private val entrant = User(
        deviceId = "user-9", name = "Robin Target", email = "r@example.com",
        roles = listOf(Role.ENTRANT), profileImg = boPicture
    )

    private val userRepo = ImageFakeUserRepository(listOf(imageTestAdmin, entrant))
    private val campsiteRepo = ImageFakeCampsiteRepository(
        listOf(Campsite(ownerId = "owner-1", campName = "Lakeside", campImg = lakePicture).apply { id = "camp-1" })
    )
    private val notifications = ImageFakeNotificationRepository()
    private val controller = imageAdminController(userRepo, campsiteRepo, notifications, imageTestAdmin)

    private var lastMessage = ""
    private var details = CampsiteDetails(id = "camp-1", name = "Lakeside", imageUrl = lakePicture)

    private fun showDetails(role: UserRole, imageUrl: String = lakePicture) {
        composeRule.setContent {
            CampsiteDetailsScreen(
                role = role,
                campsite = details.copy(imageUrl = imageUrl),
                onBack = {},
                onTabSelected = {},
                onRemoveImage = { reason ->
                    controller.removeImage(ImageKind.CAMPSITE, "camp-1", reason) { _, message -> lastMessage = message }
                }
            )
        }
    }

    private fun confirmWithReason(reason: RemovalReason) {
        composeRule.onNodeWithTag("remove_image").performClick()
        composeRule.onNodeWithText("Remove this image?").assertIsDisplayed()
        composeRule.onNodeWithTag("reason_" + reason.name).performClick()
        composeRule.onNodeWithTag("confirm_removal").performClick()
    }

    // ---- US 03.03.01: campsite image from the details page ----

    @Test
    fun clickingTheCampsiteImage_opensImageSettingsWithARedRemoveButton() {
        showDetails(UserRole.ADMIN)

        composeRule.onNodeWithTag("details_header").performClick()
        composeRule.onNodeWithText("Image settings").assertIsDisplayed()
        composeRule.onNodeWithTag("image_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("remove_image").assertIsDisplayed()
    }

    @Test
    fun removingTheCampsiteImage_afterConfirming_emptiesItAndTellsTheOwnerWhy() {
        showDetails(UserRole.ADMIN)

        composeRule.onNodeWithTag("details_header").performClick()
        confirmWithReason(RemovalReason.INAPPROPRIATE_CONTENT)

        assertEquals("Image removed", lastMessage)
        assertEquals("", campsiteRepo.campsites[0].campImg)
        assertEquals("the campsite itself is kept", 1, campsiteRepo.campsites.size)
        assertEquals(1, notifications.saved.size)
        assertEquals("owner-1", notifications.saved[0].recipientId)
        assertTrue(notifications.saved[0].message.contains(RemovalReason.INAPPROPRIATE_CONTENT.label))
        composeRule.onNodeWithText("Image settings").assertDoesNotExist()
    }

    @Test
    fun theConfirmation_cannotBeConfirmedUntilAReasonIsPicked() {
        showDetails(UserRole.ADMIN)

        composeRule.onNodeWithTag("details_header").performClick()
        composeRule.onNodeWithTag("remove_image").performClick()

        composeRule.onNodeWithTag("confirm_removal").assertIsNotEnabled()
    }

    @Test
    fun cancellingTheConfirmation_keepsTheImage() {
        showDetails(UserRole.ADMIN)

        composeRule.onNodeWithTag("details_header").performClick()
        composeRule.onNodeWithTag("remove_image").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Remove this image?").assertDoesNotExist()
        assertEquals(lakePicture, campsiteRepo.campsites[0].campImg)
        assertTrue(notifications.saved.isEmpty())
    }

    @Test
    fun anOwnerOrEntrant_clickingTheImage_getsNoImageSettings() {
        showDetails(UserRole.OWNER)

        composeRule.onNodeWithTag("details_header").performClick()
        composeRule.onNodeWithText("Image settings").assertDoesNotExist()
    }

    @Test
    fun aCampsiteWithNoImage_offersTheAdminNothingToRemove() {
        showDetails(UserRole.ADMIN, imageUrl = "")

        composeRule.onNodeWithTag("details_header").performClick()
        composeRule.onNodeWithText("Image settings").assertDoesNotExist()
    }

    // ---- US 03.03.01: profile picture from the Profiles list ----

    @Test
    fun removingAProfilePicture_fromTheProfilesList_emptiesItAndTellsTheUser() {
        val userModel = UserModel()
        val userController = UserController(userModel, userRepo)
        userModel.setCurrentUser(imageTestAdmin)
        userController.loadAllUsers()
        composeRule.setContent { AdminProfilesScreen({}, userModel, controller) }

        composeRule.onNodeWithTag("profile_image_user-9").performClick()
        confirmWithReason(RemovalReason.HARASSMENT)

        assertEquals("", userRepo.users.first { it.id == "user-9" }.profileImg)
        assertEquals("user-9", notifications.saved[0].recipientId)
        composeRule.onNodeWithText("Image removed").assertIsDisplayed()
    }

    // ---- US 03.06.01: browsing every uploaded image ----

    @Test
    fun theImagesScreen_listsCampsiteAndProfileImages() {
        composeRule.setContent { AdminImagesScreen({}, controller) }

        composeRule.onNodeWithTag("image_tile_CAMPSITE_camp-1").assertIsDisplayed()
        composeRule.onNodeWithTag("image_tile_PROFILE_user-9").assertIsDisplayed()
        composeRule.onNodeWithText("Campsite - Lakeside").assertIsDisplayed()
        composeRule.onNodeWithText("Profile - Robin Target").assertIsDisplayed()
    }

    @Test
    fun removingFromTheImagesScreen_makesItsTileDisappear() {
        composeRule.setContent { AdminImagesScreen({}, controller) }

        composeRule.onNodeWithTag("image_tile_CAMPSITE_camp-1").performClick()
        confirmWithReason(RemovalReason.SPAM_OR_FAKE)

        composeRule.onNodeWithTag("image_tile_CAMPSITE_camp-1").assertDoesNotExist()
        composeRule.onNodeWithTag("image_tile_PROFILE_user-9").assertIsDisplayed()
        assertEquals("", campsiteRepo.campsites[0].campImg)
    }

    @Test
    fun withNoUploadedImages_theImagesScreenSaysSo() {
        campsiteRepo.campsites.clear()
        userRepo.saveProfileImage("user-9", "") {}
        composeRule.setContent { AdminImagesScreen({}, controller) }

        composeRule.onNodeWithTag("images_empty").assertIsDisplayed()
    }
}