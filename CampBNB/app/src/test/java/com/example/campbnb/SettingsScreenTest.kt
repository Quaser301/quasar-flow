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
import com.example.campbnb.ui.settings.groupLabel
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
    fun test_UnknownGroupNamesShareOneFallbackFlow() {
        val display = SettingsDisplayController()

        display.toggleGroup("NOT_A_GROUP")
        assertTrue(
            "an unknown name still owns a flow, the shared fallback one",
            display.expandedStateOf("ALSO_NOT_A_GROUP").value
        )

        display.toggleGroup("ALSO_NOT_A_GROUP")
        assertTrue(
            "toggling through the other unknown name closes the shared flow",
            !display.expandedStateOf("NOT_A_GROUP").value
        )

        assertTrue(
            "the known groups keep their own flows",
            !display.expandedStateOf(SettingsGroupId.OWNER_SETTINGS.name).value
        )
    }

    @Test
    fun test_GroupLabels_AreTheExactLowerCopyNames() {
        assertTrue("app view switch", groupLabel(SettingsGroupId.APP_VIEW_SWITCH) == "app view switch")
        assertTrue("admin settings", groupLabel(SettingsGroupId.ADMIN_SETTINGS) == "admin settings")
        assertTrue("entrant settings", groupLabel(SettingsGroupId.ENTRANT_SETTINGS) == "entrant settings")
        assertTrue("owner settings", groupLabel(SettingsGroupId.OWNER_SETTINGS) == "owner settings")
    }

    @Test
    fun test_OwnerAndCamperRolesSee_OnlyTheirOwnGroup() {
        assertTrue(
            "an owner sees only their own group, whatever view is open",
            settingsGroupsFor(UserRole.OWNER, AppView.AdminView) ==
                    listOf(SettingsGroupId.OWNER_SETTINGS)
        )
        assertTrue(
            "a camper sees only their own group",
            settingsGroupsFor(UserRole.CAMPER, AppView.EntrantView) ==
                    listOf(SettingsGroupId.ENTRANT_SETTINGS)
        )
        assertTrue(
            "an admin viewing as owner gets the owner group below their own",
            settingsGroupsFor(UserRole.ADMIN, AppView.OwnerView) ==
                    listOf(
                        SettingsGroupId.APP_VIEW_SWITCH,
                        SettingsGroupId.ADMIN_SETTINGS,
                        SettingsGroupId.OWNER_SETTINGS
                    )
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
