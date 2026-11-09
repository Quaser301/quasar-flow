package com.example.campbnb

/**
 * NOTICE: This file was AI-assisted. The verbatim prompt for this work is recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 *
 * The three tests follow the request made in that prompt: pressing the back button goes
 * back, tapping a dropdown element drops down that element, and switching the view
 * introduces the settings where they are supposed to be. The Settings screen keeps its
 * behaviour in the [SettingsDisplayController] state and the pure settingsGroupsFor
 * ordering model, which is what these tests drive. Added cases cover an unknown
 * dropdown key keeping its own state, a second tap closing the group it opened, and
 * repeated back presses each running the handed-in lambda once.
 */

import com.example.campbnb.ui.settings.SettingsGroupId
import com.example.campbnb.ui.settings.settingsGroupsFor
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSettingController
import com.example.campbnb.viewModels.SettingsDisplayController
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenTest {

    @Test
    fun test_BackPress_GoesBackByCallingThePassedInLambda() {
        var wentBack = false
        val display = SettingsDisplayController({ wentBack = true })

        display.pressBack()

        assertTrue("the back press must run the back lambda it was handed", wentBack)
    }

    @Test
    fun test_Tap_DropsDownThatElementOnly() {
        val display = SettingsDisplayController({})

        display.toggleGroup(SettingsGroupId.APP_VIEW_SWITCH.name)

        assertTrue(
            "the tapped dropdown opens",
            display.expandedStateOf(SettingsGroupId.APP_VIEW_SWITCH.name).value
        )
        assertTrue(
            "the other dropdowns stay closed",
            !display.expandedStateOf(SettingsGroupId.ENTRANT_SETTINGS.name).value &&
                    !display.expandedStateOf(SettingsGroupId.ADMIN_SETTINGS.name).value
        )
    }

    @Test
    fun test_ViewSwitch_IntroducesSettingsWhereTheyAreSupposedTo() {
        // the admin viewing as admin: own settings first with the switch first there.
        val asAdmin = settingsGroupsFor(UserRole.ADMIN, AppView.AdminView)
        assertTrue(
            "the app view switch is first",
            asAdmin[0] == SettingsGroupId.APP_VIEW_SWITCH
        )
        assertTrue(
            "admin settings are the only admin section",
            asAdmin == listOf(SettingsGroupId.APP_VIEW_SWITCH, SettingsGroupId.ADMIN_SETTINGS)
        )

        // an actual view change through the setting controller changes what is below it.
        val viewSettings = AppViewSettingController()
        viewSettings.changeViewTo(AppView.EntrantView)
        val asEntrant = settingsGroupsFor(UserRole.ADMIN, viewSettings.viewSetting.value)

        assertTrue(
            "the entrant settings appear only below the admin settings",
            asEntrant == listOf(
                SettingsGroupId.APP_VIEW_SWITCH,
                SettingsGroupId.ADMIN_SETTINGS,
                SettingsGroupId.ENTRANT_SETTINGS
            )
        )
    }

    @Test
    fun test_AnUnknownGroupTapTracksItsOwnStateAndNothingElse() {
        val display = SettingsDisplayController({})

        display.toggleGroup("SOME_WHERE_ELSE")

        assertTrue(
            "the unknown key keeps its own dropdown state apart from the named groups",
            display.expandedStateOf("SOME_WHERE_ELSE").value
        )
        assertTrue(
            "the named groups stay closed",
            !display.expandedStateOf(SettingsGroupId.APP_VIEW_SWITCH.name).value &&
                    !display.expandedStateOf(SettingsGroupId.ADMIN_SETTINGS.name).value &&
                    !display.expandedStateOf(SettingsGroupId.ENTRANT_SETTINGS.name).value &&
                    !display.expandedStateOf(SettingsGroupId.OWNER_SETTINGS.name).value
        )
    }

    @Test
    fun test_ASecondTapOnTheSameGroupClosesItAgain() {
        val display = SettingsDisplayController({})

        display.toggleGroup(SettingsGroupId.ADMIN_SETTINGS.name)

        assertTrue(
            "the first tap opens",
            display.expandedStateOf(SettingsGroupId.ADMIN_SETTINGS.name).value
        )

        display.toggleGroup(SettingsGroupId.ADMIN_SETTINGS.name)

        assertTrue(
            "the tap toggles, so two taps land back closed",
            !display.expandedStateOf(SettingsGroupId.ADMIN_SETTINGS.name).value
        )
    }

    @Test
    fun test_BackPressedThreeTimesRunsTheLambdaThreeTimes() {
        var wentBack = 0
        val display = SettingsDisplayController({ wentBack++ })

        display.pressBack()
        display.pressBack()
        display.pressBack()

        assertTrue("every back press runs the lambda exactly once", wentBack == 3)
    }
}
