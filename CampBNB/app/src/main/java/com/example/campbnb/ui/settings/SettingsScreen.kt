package com.example.campbnb.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSettingController
import com.example.campbnb.viewModels.EntrantSettingsController
import com.example.campbnb.viewModels.OwnerSettingsController
import com.example.campbnb.viewModels.SettingsDisplayController


/** The dropdown groups the Settings screen can mount, by stable name. */
enum class SettingsGroupId {
    APP_VIEW_SWITCH, ADMIN_SETTINGS, ENTRANT_SETTINGS, OWNER_SETTINGS
}

/** Labels for the dropdown groups. */
fun groupLabel(groupId: SettingsGroupId): String = when (groupId) {
    SettingsGroupId.APP_VIEW_SWITCH -> "app view switch"
    SettingsGroupId.ADMIN_SETTINGS -> "admin settings"
    SettingsGroupId.ENTRANT_SETTINGS -> "entrant settings"
    SettingsGroupId.OWNER_SETTINGS -> "owner settings"
}

/**
 * Which dropdown groups the Settings screen shows, and in what order.
 *
 * An admin&apos;s own settings come first with the app view switch first there, and
 * while the admin is currently viewing as another role, that role&apos;s settings
 * exist below the admin settings. Any other user only sees their own role&apos;s
 * settings.
 *
 * @param role The role the user is.
 * @param currentView The current app view setting.
 * @return The ordered dropdown group names to mount.
 */
fun settingsGroupsFor(role: UserRole, currentView: AppView): List<SettingsGroupId> = when (role) {
    UserRole.ADMIN -> when (currentView) {
        AppView.AdminView -> listOf(SettingsGroupId.APP_VIEW_SWITCH, SettingsGroupId.ADMIN_SETTINGS)
        AppView.OwnerView -> listOf(
            SettingsGroupId.APP_VIEW_SWITCH,
            SettingsGroupId.ADMIN_SETTINGS,
            SettingsGroupId.OWNER_SETTINGS
        )
        AppView.EntrantView -> listOf(
            SettingsGroupId.APP_VIEW_SWITCH,
            SettingsGroupId.ADMIN_SETTINGS,
            SettingsGroupId.ENTRANT_SETTINGS
        )
    }
    UserRole.OWNER -> listOf(SettingsGroupId.OWNER_SETTINGS)
    UserRole.CAMPER -> listOf(SettingsGroupId.ENTRANT_SETTINGS)
}

/**
 * The Settings screen body: a back button in the top left, a Settings header in the
 * top center, and the dropdown menu of settings groups across the rest of the page.
 *
 * The screen takes the entire page as a navigation screen, but its registration is
 * done separately: this composable is only the body. Pressing back calls the passed-in
 * lambda, so the user goes back to the screen they were just on.
 *
 * The settings controllers should be created and kept by the caller, so their values
 * stay alive across re-renders; the defaults only serve standalone use.
 */
@Composable
fun SettingsScreen(
    back: () -> Unit,
    role: UserRole,
    viewSettings: AppViewSettingController,
    entrantSettings: EntrantSettingsController = EntrantSettingsController(),
    ownerSettings: OwnerSettingsController = OwnerSettingsController(),
    adminSettingsContent: @Composable () -> Unit = { AdminSettingsPlaceholder() },
) {
    val display = remember { SettingsDisplayController(back) }
    val currentView = viewSettings.viewSetting.collectAsStateWithLifecycle()

    Column {
        SettingsTopBar(display)
        settingsGroupsFor(role, currentView.value).forEach { groupId ->
            SettingsDropdown(display, groupId) {
                when (groupId) {
                    SettingsGroupId.APP_VIEW_SWITCH -> ViewSwitchItems(viewSettings)
                    SettingsGroupId.ADMIN_SETTINGS -> adminSettingsContent()
                    SettingsGroupId.ENTRANT_SETTINGS -> EntrantSettingsItems(entrantSettings)
                    SettingsGroupId.OWNER_SETTINGS -> OwnerSettingsItems(ownerSettings)
                }
            }
        }
    }
}

/** The back button in the top left and the Settings header in the top center. */
@Composable
private fun SettingsTopBar(display: SettingsDisplayController) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .clickable(onClick = { display.pressBack() })
                .padding(6.dp)
        ) {
            Text("< back", fontSize = 16.sp, color = FieldGrey)
        }
        Box(
            Modifier.weight(1.0f),
            contentAlignment = Alignment.Center
        ) {
            Text("Settings", fontSize = 26.sp)
        }
    }
}

/**
 * One dropdown of the menu: a labelled bar that opens or closes its group of
 * settings on a tap, showing that group&apos;s content while it is open.
 *
 * @param display The holder of the dropdown expansion state.
 * @param groupId The stable name of this dropdown.
 * @param content The group&apos;s settings, rendered only while the dropdown is open.
 */
@Composable
private fun SettingsDropdown(
    display: SettingsDisplayController,
    groupId: SettingsGroupId,
    content: @Composable () -> Unit,
) {
    val expanded = display.expandedStateOf(groupId.name).collectAsStateWithLifecycle()

    Column {
        Box(
            Modifier
                .clickable(onClick = { display.toggleGroup(groupId.name) })
                .padding(6.dp)
        ) {
            Text(
                groupLabel(groupId) + if (expanded.value) " - open" else " - tap to open",
                fontSize = 18.sp
            )
        }
        if (expanded.value) content()
    }
}

/**
 * The app view switch&apos;s group of settings: the three views, each one moving
 * the current setting when chosen.
 *
 * @param viewSettings The app view setting controller to move.
 */
@Composable
private fun ViewSwitchItems(viewSettings: AppViewSettingController) {
    Column {
        listOf(AppView.AdminView, AppView.OwnerView, AppView.EntrantView).forEach { view ->
            Box(
                Modifier
                    .clickable(onClick = { viewSettings.changeViewTo(view) })
                    .padding(4.dp)
            ) {
                Text(
                    view.name + (
                        if (view == viewSettings.viewSetting.value) " <- current"
                        else ""
                        ),
                    fontSize = 16.sp
                )
            }
        }
    }
}

/** The entrant settings group: the entrant&apos;s placeholder settings entries. */
@Composable
private fun EntrantSettingsItems(settings: EntrantSettingsController) {
    val bookingsOrder = settings.bookingSortSetting.collectAsStateWithLifecycle()
    val inboxAlerts = settings.inboxAlertsSetting.collectAsStateWithLifecycle()
    val messagesFilter = settings.messageFilterSetting.collectAsStateWithLifecycle()

    Column {
        SettingsEntry("bookings order", bookingsOrder.value)
        SettingsEntry("inbox alerts", inboxAlerts.value)
        SettingsEntry("messages filter", messagesFilter.value)
    }
}

/** The owner settings group: the owner&apos;s placeholder settings entries. */
@Composable
private fun OwnerSettingsItems(settings: OwnerSettingsController) {
    val listingVisibility = settings.listingVisibilitySetting.collectAsStateWithLifecycle()
    val lotteryAccepting = settings.lotteryOpenSetting.collectAsStateWithLifecycle()
    val availabilityNotice = settings.availabilityNoticeSetting.collectAsStateWithLifecycle()

    Column {
        SettingsEntry("listing visibility", listingVisibility.value)
        SettingsEntry("lottery accepting entrants", if (lotteryAccepting.value) "yes" else "no")
        SettingsEntry("availability notice", availabilityNotice.value)
    }
}

/** Placeholder admin settings groups: the admin&apos;s section is worked separately. */
@Composable
private fun AdminSettingsPlaceholder() {
    Column {
        SettingsEntry("policy review", "placeholder")
        SettingsEntry("flagged content", "placeholder")
    }
}

/** One label-and-current-value row of an opened group. */
@Composable
private fun SettingsEntry(label: String, value: String) {
    Text("$label: $value", fontSize = 16.sp)
}
