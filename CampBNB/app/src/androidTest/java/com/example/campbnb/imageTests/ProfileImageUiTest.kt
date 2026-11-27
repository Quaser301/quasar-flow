package com.example.campbnb.imageTests

// tests created with the help of Claude Sonnet 5.5

/**
 * Intent (UI) tests for US 01.08.02: an entrant adds, updates, or removes their profile image.
 *
 * Each test drives the real Profile screen the way an entrant would (tap "Add photo" or
 * "Change photo" and choose a picture, or tap "Remove photo") and checks what the entrant
 * sees and what the screen reports to be saved. The phone's picker is a stand-in that answers
 * at once, and nothing touches Firebase.
 */

import android.content.Context
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.campbnb.data.ImageRepository
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.screens.ProfileScreen
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileImageUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Every value the screen reported through onImageChanged, in order. */
    private val reported = CopyOnWriteArrayList<String>()

    /**
     * Shows the real Profile screen holding [startImage]; the way the app does, a reported
     * image is kept and passed back in. Every tap on "Add photo" picks [picture].
     */
    private fun showProfile(startImage: String, picture: Uri) {
        val picker = pickerReturning(picture)
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                var image by remember { mutableStateOf(startImage) }
                ProfileScreen(
                    role = UserRole.CAMPER,
                    initialName = "Sam Camper",
                    initialEmail = "sam@example.com",
                    initialPhone = "780-555-0100",
                    onSave = { _, _, _ -> },
                    onOpenSettings = {},
                    onManageCampsites = {},
                    onDeleteProfile = {},
                    profileImage = image,
                    onImageChanged = { newImage ->
                        reported.add(newImage)
                        image = newImage
                    }
                )
            }
        }
    }

    private fun waitForProfileImage() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("profile_image").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun test_AnEntrantWithoutAnImageAddsOne() {
        showProfile("", writeTestPicture(context, "profile_green.png", Color.GREEN))
        composeRule.onAllNodesWithTag("profile_image").assertCountEquals(0)
        composeRule.onNodeWithText("Add photo").assertIsDisplayed()
        composeRule.onAllNodesWithTag("profile_image_remove").assertCountEquals(0)

        composeRule.onNodeWithTag("profile_image_change").performClick()
        waitForProfileImage()

        composeRule.onNodeWithTag("profile_image").assertIsDisplayed()
        composeRule.onNodeWithText("Change photo").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_image_remove").assertIsDisplayed()

        assertEquals("one image was reported", 1, reported.size)
        val picture = decodeStored(reported[0])
        assertNotNull("the reported text is a real image", picture)
        assertTrue(
            "the 1600 pixel picture was shrunk to the profile limit",
            maxOf(picture!!.width, picture.height) <= ImageRepository.PROFILE_EDGE
        )
    }

    @Test
    fun test_AnEntrantWithAnImageUpdatesIt() {
        val before = storedPicture(Color.BLUE)
        showProfile(before, writeTestPicture(context, "profile_red.png", Color.RED))
        waitForProfileImage()
        composeRule.onNodeWithText("Change photo").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_image_change").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { reported.isNotEmpty() }

        assertEquals("one image was reported", 1, reported.size)
        assertTrue("it is a new image, not an empty one", reported[0].isNotEmpty())
        assertNotEquals("the new image replaced the old one", before, reported[0])
    }

    @Test
    fun test_AnEntrantWithAnImageRemovesIt() {
        showProfile(
            storedPicture(Color.BLUE),
            writeTestPicture(context, "profile_unused.png", Color.RED)
        )
        waitForProfileImage()

        composeRule.onNodeWithTag("profile_image_remove").performClick()

        assertEquals("an empty image was reported", listOf(""), reported.toList())
        composeRule.onAllNodesWithTag("profile_image").assertCountEquals(0)
        composeRule.onNodeWithText("Add photo").assertIsDisplayed()
        composeRule.onAllNodesWithTag("profile_image_remove").assertCountEquals(0)
    }

    @Test
    fun test_AFileThatIsNotAPictureIsRefusedAndNothingIsSaved() {
        showProfile("", writeNotAPicture(context, "not_a_picture.txt"))

        composeRule.onNodeWithTag("profile_image_change").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("That image could not be used. Please pick a different one.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("nothing was reported", reported.isEmpty())
        composeRule.onAllNodesWithTag("profile_image").assertCountEquals(0)
    }
}