package com.example.campbnb

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.campbnb.ui.shared.AppViewModificationIsValidForCurrentWindowChecker
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSettingController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [AppViewModificationIsValidForCurrentWindowChecker] window component.
 *
 * The checker runs on the Compose render clock ([LaunchedEffect]), so it is tested with a
 * [createComposeRule] scene on the androidTest target rather than as a plain unit test.
 */
@OptIn(ExperimentalTestApi::class)
class AppViewComponentsTest {

    @Test
    fun test_InitialInvalidViewReturnsImmediately() {
        // the app always starts in EntrantView, so flagging EntrantView must fire on the
        // very first render.
        val controller = AppViewSettingController()
        var returnedFrom = false

        val rule = createComposeRule()
        rule.setContent {
            AppViewModificationIsValidForCurrentWindowChecker(
                returnTo = { returnedFrom = true },
                viewSetting = controller.toAppViewSetting(),
                returnIfAppViewIs = listOf(AppView.EntrantView)
            )
        }
        rule.waitForIdle()

        assertTrue(
            "EntrantView is the initial view, so the checker must have returned",
            returnedFrom
        )
    }

    @Test
    fun test_InitialValidViewDoesNotReturn() {
        // an initial view outside the flagged list must leave the window alone.
        val controller = AppViewSettingController()
        var returnedFrom = false

        val rule = createComposeRule()
        rule.setContent {
            AppViewModificationIsValidForCurrentWindowChecker(
                returnTo = { returnedFrom = true },
                viewSetting = controller.toAppViewSetting(),
                returnIfAppViewIs = listOf(AppView.OwnerView)
            )
        }
        rule.waitForIdle()

        assertFalse(
            "EntrantView is not flagged, so the checker must not have returned",
            returnedFrom
        )
    }

    @Test
    fun test_ChangingToFlaggedViewReturns() {
        // the point of the checker is that it re-runs when the live flow changes value,
        // so a view change made after the first render must still fire returnTo.
        val controller = AppViewSettingController()
        var returnedFrom = false

        val rule = createComposeRule()
        rule.setContent {
            AppViewModificationIsValidForCurrentWindowChecker(
                returnTo = { returnedFrom = true },
                viewSetting = controller.toAppViewSetting(),
                returnIfAppViewIs = listOf(AppView.AdminView)
            )
        }
        rule.waitForIdle()
        assertFalse(
            "The initial view is not flagged",
            returnedFrom
        )

        controller.changeViewTo(AppView.AdminView)
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()

        assertTrue(
            "AdminView is flagged, so the changed view must trigger a return",
            returnedFrom
        )
    }

    @Test
    fun test_ChangingToUnflaggedViewNeverReturns() {
        // changing the live view is only half the contract: views that are not in
        // returnIfAppViewIs must never fire returnTo, however many times the view changes.
        val controller = AppViewSettingController()
        var returnedFrom = false

        val rule = createComposeRule()
        rule.setContent {
            AppViewModificationIsValidForCurrentWindowChecker(
                returnTo = { returnedFrom = true },
                viewSetting = controller.toAppViewSetting(),
                returnIfAppViewIs = listOf(AppView.AdminView)
            )
        }
        rule.waitForIdle()

        controller.changeViewTo(AppView.OwnerView)
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()

        controller.changeViewTo(AppView.EntrantView)
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()

        assertFalse(
            "Neither changed view is flagged, so the checker must never have returned",
            returnedFrom
        )
    }
}
