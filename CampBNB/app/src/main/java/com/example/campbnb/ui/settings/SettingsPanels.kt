package com.example.campbnb.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting
import com.example.campbnb.viewModels.EntrantSettingsController
import com.example.campbnb.viewModels.OwnerSettingsController



/**
 * The Entrant-role settings section of the Settings modal.
 *
 * Shows the entrant&apos;s placeholder settings entries while
 * [AppView.EntrantView] is the current view setting, and one muted
 * inactive line otherwise, so the section never collapses and never
 * blocks anything stacked below or above it.
 *
 * Designed to be stacked under an admin settings section: this section
 * occupies only its own content height and never fills its container,
 * so the admin section keeps being editable no matter what is mounted
 * after it.
 *
 * @param entrantSettings The controller holding the entrant&apos;s settings StateFlows.
 * @param appViewSettings The live view setting, used only to decide whether this
 * section is the active one.
 */
@Composable
fun EntrantSettingsPanel(
    entrantSettings: EntrantSettingsController,
    appViewSettings: AppViewSetting,
) {
    val viewState = appViewSettings.setting.collectAsStateWithLifecycle()
    val bookingSort = entrantSettings.bookingSortSetting.collectAsStateWithLifecycle()
    val inboxAlerts = entrantSettings.inboxAlertsSetting.collectAsStateWithLifecycle()
    val messageFilter = entrantSettings.messageFilterSetting.collectAsStateWithLifecycle()

    Column {
        Text("Entrant settings", fontSize = 20.sp)
        if (viewState.value == AppView.EntrantView) {
            SettingsEntry("Bookings order", bookingSort.value)
            SettingsEntry("Inbox alerts", inboxAlerts.value)
            SettingsEntry("Messages filter", messageFilter.value)
        } else {
            Text(
                "not this view - a smaller window will show when the setting switches",
                fontSize = 14.sp,
                color = FieldGrey,
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
            )
        }
    }
}

/**
 * The Campsite-Owner-role settings section of the Settings modal.
 *
 * Behaves exactly like [EntrantSettingsPanel] for [AppView.OwnerView]: the
 * section shows its owner&apos;s placeholder settings entries while the owner
 * view is current and one muted inactive line otherwise, so it stays
 * stackable under an admin settings section.
 *
 * @param ownerSettings The controller holding the owner&apos;s settings StateFlows.
 * @param appViewSettings The live view setting, used only to decide whether this
 * section is the active one.
 */
@Composable
fun OwnerSettingsPanel(
    ownerSettings: OwnerSettingsController,
    appViewSettings: AppViewSetting,
) {
    val viewState = appViewSettings.setting.collectAsStateWithLifecycle()
    val listingVisibility = ownerSettings.listingVisibilitySetting.collectAsStateWithLifecycle()
    val lotteryOpen = ownerSettings.lotteryOpenSetting.collectAsStateWithLifecycle()
    val availabilityNotice = ownerSettings.availabilityNoticeSetting.collectAsStateWithLifecycle()

    Column {
        Text("Owner settings", fontSize = 20.sp)
        if (viewState.value == AppView.OwnerView) {
            SettingsEntry("Listing visibility", listingVisibility.value)
            SettingsEntry("Lottery accepting entrants", if (lotteryOpen.value) "Yes" else "No")
            SettingsEntry("Availability notice", availabilityNotice.value)
        } else {
            Text(
                "not this view - a smaller window will show when the setting switches",
                fontSize = 14.sp,
                color = FieldGrey,
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
            )
        }
    }
}

/** One label-and-current-value row of a settings section. */
@Composable
private fun SettingsEntry(label: String, value: String) {
    Row(
        Modifier.padding(top = 4.dp, bottom = 4.dp),
    ) {
        Text("$label: $value", fontSize = 16.sp)
    }
}
