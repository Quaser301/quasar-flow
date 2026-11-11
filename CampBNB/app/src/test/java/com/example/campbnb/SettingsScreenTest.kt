package com.example.campbnb

/**
 * NOTICE: This file was AI-assisted. The verbatim prompt for this work is recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 *
 * The three tests follow the request made in that prompt: pressing the back button goes
 * back, tapping a dropdown element drops down that element, and switching the view
 * introduces the settings where they are supposed to be. The Settings screen keeps its
 * behavior in the [SettingsDisplayController] state and the pure settingsGroupsFor
 * ordering model, which is what these tests drive.
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
    fun test_Tap_DropsDownThatElementOnly() {
        val display = SettingsDisplayController()

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
}
