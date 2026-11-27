package com.example.campbnb.imageTests

// tests created with the help of Claude Sonnet 5.5

/**
 * Intent (UI) tests for US 02.01.02: a campsite owner gives their campsite an optional image.
 *
 * Each test drives the real registration form the way an owner would (tap "Upload Image",
 * choose a picture, tap save) and checks what the owner sees and what the saved listing holds.
 * The phone's picker is a stand-in that answers at once, and nothing touches Firebase.
 */

import android.content.Context
import android.graphics.Color
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.campbnb.data.ImageRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.ui.campsite.CampsiteRegistrationForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampsiteImageUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var saved: Campsite? = null

    /** Shows the form; every tap on "Upload Image" picks [picture]. */
    private fun showForm(existing: Campsite?, picture: android.net.Uri) {
        val picker = pickerReturning(picture)
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                CampsiteRegistrationForm(
                    existingCampsite = existing,
                    onBack = {},
                    onUpload = { campsite -> saved = campsite }
                )
            }
        }
    }

    private fun typeInto(tag: String, text: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTextInput(text)
    }

    private fun fillRequiredFields() {
        typeInto("name_field", "Pine Hollow")
        typeInto("location_field", "Hinton, AB")
        typeInto("description_field", "A quiet site by the river")
        typeInto("amenities_field", "fire pit, cookhouse")
        typeInto("occupancy_field", "8")
        typeInto("price_field", "65")
        typeInto("cancel_policy_field", "Free up to 48 hours before")
        typeInto("geo_field", "53.41, -117.58")
    }

    private fun waitForPreview() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("image_preview").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun anExistingCampsite(image: String): Campsite {
        return Campsite(
            ownerId = "owner-1",
            campName = "Pine Hollow",
            campLoc = "Hinton, AB",
            campDesc = "A quiet site by the river",
            campAmenities = listOf("fire pit"),
            geoLocation = "53.41, -117.58",
            campImg = image,
            maxOccupancy = 8,
            cancelPolicy = "Free up to 48 hours before",
            campPrice = 65.0
        )
    }

    @Test
    fun test_AnOwnerPicksAnImageAndTheNewListingCarriesIt() {
        showForm(null, writeTestPicture(context, "camp_green.png", Color.GREEN))
        fillRequiredFields()

        composeRule.onNodeWithTag("image_upload").performScrollTo().performClick()
        waitForPreview()

        composeRule.onNodeWithTag("image_preview").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Image Selected").assertIsDisplayed()
        composeRule.onNodeWithText("Change Image").assertIsDisplayed()

        composeRule.onNodeWithTag("save_changes").performScrollTo().performClick()

        val stored = saved!!.campImg
        assertTrue("the listing holds the image text", stored.isNotEmpty())
        val picture = decodeStored(stored)
        assertNotNull("the text is a real image", picture)
        assertTrue(
            "the 1600 pixel picture was shrunk to the campsite limit",
            maxOf(picture!!.width, picture.height) <= ImageRepository.CAMPSITE_EDGE
        )
    }

    @Test
    fun test_AnOwnerWhoPicksNothingGetsAListingWithoutAnImage() {
        showForm(null, writeTestPicture(context, "camp_unused.png", Color.GREEN))
        fillRequiredFields()

        composeRule.onNodeWithTag("save_changes").performScrollTo().performClick()

        assertEquals("the image is optional", "", saved!!.campImg)
    }

    @Test
    fun test_AnOwnerRemovesTheImageAndTheListingHasNone() {
        showForm(
            anExistingCampsite(storedPicture(Color.BLUE)),
            writeTestPicture(context, "camp_unused2.png", Color.GREEN)
        )
        waitForPreview()

        composeRule.onNodeWithTag("image_remove").performScrollTo().performClick()

        composeRule.onAllNodesWithTag("image_preview").assertCountEquals(0)
        composeRule.onNodeWithText("Upload Image").assertIsDisplayed()

        composeRule.onNodeWithTag("save_changes").performScrollTo().performClick()

        assertEquals("the removed image is gone from the saved listing", "", saved!!.campImg)
    }
}