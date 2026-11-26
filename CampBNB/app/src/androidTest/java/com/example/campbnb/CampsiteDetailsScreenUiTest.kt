package com.example.campbnb

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.details.CampsiteDetails
import com.example.campbnb.ui.details.CampsiteDetailsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CampsiteDetailsScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val campsite = CampsiteDetails(
        id = "c1",
        name = "Lakeside Camp",
        rating = 4.0,
        reviewCount = 2,
        tags = listOf("Fire pit", "Lakeside", "Parking", "Pet friendly"),
        capacityGuests = 6,
        location = "Banff",
        description = "A quiet spot by the water."
    )

    @Test
    fun theBackCircle_callsOnBack() {
        var backTapped = false
        composeRule.setContent {
            CampsiteDetailsScreen(
                role = UserRole.CAMPER,
                campsite = campsite,
                onBack = { backTapped = true },
                onTabSelected = {}
            )
        }
        composeRule.onNodeWithTag("details_back").performClick()
        assertTrue(backTapped)
    }

    @Test
    fun theLotteryBar_opensAMenu_thatEntersTheLottery() {
        var entered = false
        composeRule.setContent {
            CampsiteDetailsScreen(
                role = UserRole.CAMPER,
                campsite = campsite,
                onBack = {},
                onTabSelected = {},
                onEnterLottery = { entered = true }
            )
        }
        composeRule.onNodeWithTag("lottery_menu_bar").performClick()
        composeRule.onNodeWithTag("lottery_enter").performClick()
        assertTrue(entered)
    }

    @Test
    fun aCamper_seesNoLotteryBar_whenThereIsNoActiveLottery() {
        composeRule.setContent {
            CampsiteDetailsScreen(
                role = UserRole.CAMPER,
                campsite = campsite,
                onBack = {},
                onTabSelected = {},
                lotteryButtonEnabled = false
            )
        }
        composeRule.onNodeWithTag("lottery_menu_bar").assertDoesNotExist()
    }

    @Test
    fun thePageContent_neverSitsUnderTheLotteryBar() {
        // Regression: the Post review button used to be covered by the floating lottery button.
        composeRule.setContent {
            CampsiteDetailsScreen(
                role = UserRole.CAMPER,
                campsite = campsite,
                onBack = {},
                onTabSelected = {},
                entrantContent = {
                    Column {
                        Text("Probe", modifier = androidx.compose.ui.Modifier
                            .testTag("probe"))
                    }
                }
            )
        }
        composeRule.onNodeWithTag("probe").performScrollTo()
        val probeBottom = composeRule.onNodeWithTag("probe").getBoundsInRoot().bottom
        val barTop = composeRule.onNodeWithTag("lottery_menu_bar").getBoundsInRoot().top
        assertTrue("Content ($probeBottom) must end above the lottery bar ($barTop)", probeBottom <= barTop + 0.dp)
    }

    @Test
    fun viewAllTags_opensTheFullList_andCloseDismissesIt() {
        composeRule.setContent {
            CampsiteDetailsScreen(
                role = UserRole.OWNER,
                campsite = campsite,
                onBack = {},
                onTabSelected = {}
            )
        }
        composeRule.onNodeWithTag("tags_close").assertDoesNotExist()
        composeRule.onNodeWithTag("see_tags").performScrollTo().performClick()
        composeRule.onNodeWithTag("tags_close").assertIsDisplayed()
        composeRule.onNodeWithTag("tags_close").performClick()
        composeRule.onNodeWithTag("tags_close").assertDoesNotExist()
    }

    @Test
    fun seeAllReviews_showsTheReviewsContent_andItsCloseReturnsToTheDetails() {
        composeRule.setContent {
            CampsiteDetailsScreen(
                role = UserRole.CAMPER,
                campsite = campsite,
                onBack = {},
                onTabSelected = {},
                reviewsContent = { close ->
                    Text("Reviews here", modifier = androidx.compose.ui.Modifier
                        .testTag("reviews_probe"))
                    androidx.compose.material3.TextButton(
                        onClick = close,
                        modifier = androidx.compose.ui.Modifier.testTag("reviews_close")
                    ) { Text("Close") }
                }
            )
        }
        composeRule.onNodeWithTag("see_reviews").performScrollTo().performClick()
        composeRule.onNodeWithTag("reviews_probe").assertIsDisplayed()
        composeRule.onNodeWithTag("details_back").assertDoesNotExist()
        composeRule.onNodeWithTag("reviews_close").performClick()
        composeRule.onNodeWithTag("details_back").assertIsDisplayed()
    }

    @Test
    fun anOwner_hasNoActionStrip() {
        composeRule.setContent {
            CampsiteDetailsScreen(
                role = UserRole.OWNER,
                campsite = campsite,
                onBack = {},
                onTabSelected = {}
            )
        }
        composeRule.onNodeWithTag("lottery_menu_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("delete_site").assertDoesNotExist()
    }

    @Test
    fun anAdmin_cancellingTheDeletePopUp_deletesNothing() {
        var reasonGiven: RemovalReason? = null
        var deleted = false
        composeRule.setContent {
            CampsiteDetailsScreen(
                role = UserRole.ADMIN,
                campsite = campsite,
                onBack = {},
                onTabSelected = {},
                onDeleteSite = { reason -> deleted = true; reasonGiven = reason }
            )
        }
        composeRule.onNodeWithTag("delete_site").performClick()
        composeRule.onNodeWithTag("admin_delete_cancel").performClick()
        composeRule.onNodeWithTag("confirm_delete_site").assertDoesNotExist()
        assertEquals(false, deleted)
        assertNull(reasonGiven)
    }

    @Test
    fun anAdmin_deletingASite_mustPickAReason_thenOnDeleteSiteGetsIt() {
        var reasonGiven: RemovalReason? = null
        composeRule.setContent {
            CampsiteDetailsScreen(
                role = UserRole.ADMIN,
                campsite = campsite,
                onBack = {},
                onTabSelected = {},
                onDeleteSite = { reason -> reasonGiven = reason }
            )
        }
        composeRule.onNodeWithTag("delete_site").performClick()
        composeRule.onNodeWithTag("confirm_delete_site").performClick()
        composeRule.onNodeWithTag("reason_" + RemovalReason.SPAM_OR_FAKE.name).performClick()
        composeRule.onNodeWithTag("confirm_removal").performClick()
        assertEquals(RemovalReason.SPAM_OR_FAKE, reasonGiven)
    }
}