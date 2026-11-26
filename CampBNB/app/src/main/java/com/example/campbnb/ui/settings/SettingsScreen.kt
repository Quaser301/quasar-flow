package com.example.campbnb.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campbnb.R
import com.example.campbnb.data.AppSettingsService
import com.example.campbnb.data.NoAppSettingsService
import com.example.campbnb.data.NoNotificationPreferencesService
import com.example.campbnb.data.NotificationPreferencesService
import com.example.campbnb.models.ConfirmationDelivery
import com.example.campbnb.models.confirmationDeliveryOf
import com.example.campbnb.ui.components.GradientBarButton
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.shared.AppDropDownMenu
import com.example.campbnb.ui.shared.BarWithBackChevronAndHeaderCentered
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSettingController
import com.example.campbnb.viewModels.EntrantSettingsController
import com.example.campbnb.viewModels.OwnerSettingsController
import com.example.campbnb.viewModels.SettingsDisplayController

/**
 * 25-11-26: Prompted Gemini 3.6 to add a scrollable popup to the app policy button
 * to display the policy text that will be stored in the string xml.
 * 25-11-26: Prompted Claude Sonnet 5.5 to write a standard app policy and format it for the
 * strings.xml file
 */
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
 * An admin's own settings come first with the app view switch first there, and
 * while the admin is currently viewing as another role, that role's settings
 * exist below the admin settings. Any other user only sees their own role's
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
    notificationPreferences: NotificationPreferencesService = NoNotificationPreferencesService(),
    appSettings: AppSettingsService = NoAppSettingsService(),
    userIdForSettings: String = "",
) {
    val display = remember { SettingsDisplayController() }
    val currentView = viewSettings.viewSetting.collectAsStateWithLifecycle()
    var showPolicyDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    // A pending dropdown choice means there are unsaved changes.
    val pendingDelivery by entrantSettings.pendingDelivery.collectAsStateWithLifecycle()
    val hasUnsavedChanges = pendingDelivery != null
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Backing out with unsaved changes asks first; otherwise it just goes back.
    val guardedBack: () -> Unit = {
        if (hasUnsavedChanges) showDiscardDialog = true else back()
    }
    // The system back gesture gets the same guard as the on-screen chevron.
    BackHandler(enabled = hasUnsavedChanges) { showDiscardDialog = true }

    Column(
        modifier = Modifier.padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BarWithBackChevronAndHeaderCentered(guardedBack, "Settings")
        settingsGroupsFor(role, currentView.value).forEach { groupId ->
            SettingsDropdown(display, groupId) {
                when (groupId) {
                    SettingsGroupId.APP_VIEW_SWITCH -> ViewSwitchMenu(
                        display,
                        viewSettings,
                        appSettings,
                        userIdForSettings
                    )
                    SettingsGroupId.ADMIN_SETTINGS -> adminSettingsContent()
                    SettingsGroupId.ENTRANT_SETTINGS -> EntrantSettingsItems(
                        settings = entrantSettings
                    )
                    SettingsGroupId.OWNER_SETTINGS -> OwnerSettingsItems(ownerSettings)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Update: always shown, disabled until a setting changes. Pressing it applies
        // the change instantly and saves it in the background.
        GradientBarButton(
            text = "Update",
            enabled = hasUnsavedChanges,
            onClick = {
                val toSave = entrantSettings.commitPending()
                notificationPreferences.setNotificationSettings(userIdForSettings, toSave) { result ->
                    if (result.isFailure) {
                        Toast.makeText(context, "Could not save settings", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier
                .testTag("settings_update")
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // App Policy displays for all roles as I feel it's relevant for more than just entrant
        GradientBarButton(
            text = "App Policy",
            onClick = { showPolicyDialog = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
        )

        if (showPolicyDialog) {
            AlertDialog(
                onDismissRequest = { showPolicyDialog = false },
                title = {
                    Text(
                        text = "App Policy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Box(
                        modifier = Modifier
                            .heightIn(max = 400.dp) // Constrains popup height
                            .verticalScroll(rememberScrollState()) // Makes text scrollable
                    ) {
                        Text(
                            text = stringResource(id = R.string.app_policy),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPolicyDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Leaving with unsaved changes: Cancel stays here with the changes held,
        // Discard drops them and goes back.
        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("Discard changes?") },
                text = { Text("You have unsaved settings. Continuing to navigate back will discard your changes.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscardDialog = false
                        entrantSettings.discardPending()
                        back()
                    }) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

/**
 * One dropdown of the menu: a labeled bar that opens or closes its group of
 * settings on a tap, showing that group's content while it is open.
 *
 * @param display The holder of the dropdown expansion state.
 * @param groupId The stable name of this dropdown.
 * @param content The group's settings, rendered only while the dropdown is open.
 */
@Composable
private fun SettingsDropdown(
    display: SettingsDisplayController,
    groupId: SettingsGroupId,
    content: @Composable () -> Unit,
) {
    val expanded = display.expandedStateOf(groupId.name).collectAsStateWithLifecycle()

    Box(
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            GradientBarButton(
                groupLabel(groupId) + if (expanded.value) "" else " - tap to open",
                onClick = { display.toggleGroup(groupId.name) },
                modifier = Modifier
                    .testTag("settings_group_bar")
                    .fillMaxWidth()
                    .padding(6.dp)
            )
            if (expanded.value) content()
        }
    }
}

/**
 * The app view switch's group of settings: choosing one of the three views is
 * exactly what a dropdown menu is for, so this group uses the material3 menu. Its
 * expansion is still the display controller's state, and a dismissal through the
 * menu's own closing runs the same controller toggle so the two never disagree.
 *
 * @param display The holder of the dropdown expansion state.
 * @param viewSettings The app view setting controller to move.
 * @param appSettings The storage that keeps the chosen view between app lifetimes.
 * @param userId The user whose record the chosen view is saved under.
 */
@Composable
private fun ViewSwitchMenu(
    display: SettingsDisplayController,
    viewSettings: AppViewSettingController,
    appSettings: AppSettingsService,
    userId: String,
) {
    val expanded = display.expandedStateOf(
        SettingsGroupId.APP_VIEW_SWITCH.name
    ).collectAsStateWithLifecycle()
    val currentView = viewSettings.viewSetting.collectAsStateWithLifecycle()
    val entries = listOf(AppView.AdminView, AppView.OwnerView, AppView.EntrantView)
    val textRenderer: @Composable (AppView) -> Unit = {
        Text(it.name + if (it == currentView.value) " ←" else "")
    }

    AppDropDownMenu(
        entries,
        {
            viewSettings.changeViewTo(it)
            // the chosen view is saved so the user is in it again on the next
            // app lifetime; the record keeps the other groups untouched
            appSettings.setAppViewChoice(userId, it) { _ -> }
        },
        {display.toggleGroup(SettingsGroupId.APP_VIEW_SWITCH.name )},
        textRenderer,
        expanded.value,
        // the view-settings switch's dropdown reads centered (feature 3)
        centered = true
    )
}

/** The entrant settings group: the entrant's settings entries. */
@Composable
private fun EntrantSettingsItems(settings: EntrantSettingsController) {
    val bookingsOrder = settings.bookingSortSetting.collectAsStateWithLifecycle()
    val inboxAlerts = settings.inboxAlertsSetting.collectAsStateWithLifecycle()
    val messagesFilter = settings.messageFilterSetting.collectAsStateWithLifecycle()

    Column {
        SettingsEntry("bookings order", bookingsOrder.value)
        SettingsEntry("inbox alerts", inboxAlerts.value)
        SettingsEntry("messages filter", messagesFilter.value)
        ConfirmationSettingsTab(settings)
    }
}

/**
 * The "Confirmation Settings" sub-tab: one dropdown choosing whether a reservation
 * confirmation arrives as a notification (default) or an auto-downloaded CSV.
 * Choosing only marks the change as pending; the Update button saves it.
 */
@Composable
private fun ConfirmationSettingsTab(settings: EntrantSettingsController) {
    var open by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val saved by settings.notificationSettings.collectAsStateWithLifecycle()
    val pending by settings.pendingDelivery.collectAsStateWithLifecycle()
    // What the dropdown shows: the unsaved choice if there is one, else the saved one.
    val shown = pending ?: confirmationDeliveryOf(saved)

    Column {
        GradientBarButton(
            "Confirmation Settings" + if (open) "" else " - tap to open",
            onClick = { open = !open },
            modifier = Modifier
                .testTag("confirmation_settings_bar")
                .fillMaxWidth()
                .padding(6.dp)
        )
        if (open) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Receive reservation confirmations as", fontSize = 18.sp)
                Box {
                    Text(
                        text = deliveryLabel(shown) + "  ▾",
                        fontSize = 18.sp,
                        modifier = Modifier
                            .testTag("confirmation_delivery_dropdown")
                            .clip(RoundedCornerShape(8.dp))
                            .background(FieldGrey)
                            .clickable { menuOpen = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    AppDropDownMenu(
                        entries = listOf(ConfirmationDelivery.NOTIFICATION, ConfirmationDelivery.CSV),
                        onSelect = { settings.chooseDelivery(it) },
                        onDismissed = { menuOpen = false },
                        textRenderer = { Text(deliveryLabel(it) + if (it == shown) " ←" else "") },
                        expanded = menuOpen
                    )
                }
            }
        }
    }
}

/** The dropdown wording for each delivery choice. */
private fun deliveryLabel(delivery: ConfirmationDelivery): String = when (delivery) {
    ConfirmationDelivery.NOTIFICATION -> "Notification"
    ConfirmationDelivery.CSV -> "CSV download"
}

/** The owner settings group: the owner's placeholder settings entries. */
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

/** Placeholder admin settings groups: the admin's section is worked separately. */
@Composable
private fun AdminSettingsPlaceholder() {
    Column (
        verticalArrangement = Arrangement.spacedBy(16.dp)
    )
    {
        SettingsEntry("Policy review", "placeholder")
        SettingsEntry("Flagged content", "placeholder")
    }
}

/** One label-and-current-value row of an opened group. */
@Composable
private fun SettingsEntry(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2))
            .background(FieldGrey)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .padding(vertical = 12.dp)
        ) {
            Text(label, fontSize = 24.sp)
            Text(value, fontSize = 12.sp)
        }
    }
}


