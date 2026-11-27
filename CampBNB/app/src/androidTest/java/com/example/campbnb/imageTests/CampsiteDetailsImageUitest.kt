package com.example.campbnb.imageTests

// tests created with the help of Claude Sonnet 5.5

/**
 * Intent (UI) test for the campsite image on the details screen (US 02.01.02, shown through
 * US 01.01.05): the picture an owner uploaded is drawn behind the header, and a campsite
 * without one keeps the plain green header.
 *
 * Each test shows the real details screen with a campsite value; nothing touches Firebase.
 * Needs the helpers in ImageTestSupport.kt.
 */

import android.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.details.CampsiteDetails
import com.example.campbnb.ui.details.CampsiteDetailsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampsiteDetailsImageUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun showDetails(image: String) {
        composeRule.setContent {
            CampsiteDetailsScreen(
                role = UserRole.OWNER,
                campsite = CampsiteDetails(
                    id = "c1",
                    name = "Pine Hollow",
                    capacityGuests = 8,
                    location = "Hinton, AB",
                    description = "A quiet site by the river",
                    imageUrl = image
                ),
                onBack = {},
                onTabSelected = {}
            )
        }
    }

    @Test
    fun test_ACampsiteWithAnImageShowsItBehindTheHeader() {
        showDetails(storedPicture(Color.BLUE))

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("details_image").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("details_image").assertIsDisplayed()
        composeRule.onNodeWithText("Pine Hollow").assertIsDisplayed()
        composeRule.onNodeWithTag("details_back").assertIsDisplayed()
    }

    @Test
    fun test_ACampsiteWithoutAnImageKeepsTheGreenHeader() {
        showDetails("")

        composeRule.onAllNodesWithTag("details_image").assertCountEquals(0)
        composeRule.onNodeWithText("Pine Hollow").assertIsDisplayed()
        composeRule.onNodeWithTag("details_back").assertIsDisplayed()
    }
}