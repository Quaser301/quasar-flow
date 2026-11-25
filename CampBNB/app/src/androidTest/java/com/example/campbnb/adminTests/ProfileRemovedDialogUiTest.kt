package com.example.campbnb

// tests created with the help of Claude

/**
 * Intent (UI) tests for the pop-up an entrant sees when an admin deletes their profile
 * (US 03.02.01): it explains what happened, and the app only moves on once OK is tapped.
 */

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campbnb.ui.admin.ProfileRemovedDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileRemovedDialogUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun thePopUp_explainsWhatHappened_andWaitsForOk() {
        var okTaps = 0
        composeRule.setContent { ProfileRemovedDialog(onOk = { okTaps = okTaps + 1 }) }

        composeRule.onNodeWithText("Your profile was deleted").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_removed_ok").assertIsDisplayed()
        assertEquals("the app has not moved on yet", 0, okTaps)
    }

    @Test
    fun tappingOk_movesTheAppOnOnce() {
        var okTaps = 0
        composeRule.setContent { ProfileRemovedDialog(onOk = { okTaps = okTaps + 1 }) }

        composeRule.onNodeWithTag("profile_removed_ok").performClick()

        assertEquals("OK was reported once", 1, okTaps)
    }
}