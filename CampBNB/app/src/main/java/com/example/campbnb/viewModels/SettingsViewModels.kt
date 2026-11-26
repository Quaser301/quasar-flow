package com.example.campbnb.viewModels

import androidx.lifecycle.ViewModel
import com.example.campbnb.models.NotificationSettings
import com.example.campbnb.models.AppSettingsModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


// Same shape as AppViewSettingController: a ViewModel keeps these values alive across
// re-renders and window resizing, and every value is exposed as a readonly StateFlow so
// the matching settings panel re-renders when a value changes.

// PLACEHOLDER NOTE: the entry names below are placeholders chosen from what each role's
// own views can do (the HomeScreen tab sets: entrants get Messages, Bookings and Inbox;
// owners get Messages, My Campsites and Inbox; US 03.01.01/03.03.01 show entrants and
// owners losing visibility of admin-deleted content). The real settings entries are still
// undecided; the structure is meant to hold replacements without redesign.

/**
 * Provides a concrete [ViewModel] holding the Entrant-role settings edited from the
 * Settings modal while [AppView.EntrantView] is current.
 *
 * Each setting is kept as an immutable `StateFlow` so the entrant settings panel can
 * observe changes and re-render.
 *
 * @property bookingSortSetting The current bookings ordering placeholder.
 * @property inboxAlertsSetting The current inbox alerting placeholder.
 * @property messageFilterSetting The current message-list filtering placeholder.
 * @property notificationSettings The current notification settings for this user. Defaults to
 * PUSH notifications. Multiple notification types are allowed (IE: PUSH and Download)
 */
class EntrantSettingsController: ViewModel() {
    private val _bookingSortSetting = MutableStateFlow("Newest first")
    val bookingSortSetting = _bookingSortSetting.asStateFlow()

    private val _inboxAlertsSetting = MutableStateFlow("All activity")
    val inboxAlertsSetting = _inboxAlertsSetting.asStateFlow()

    private val _messageFilterSetting = MutableStateFlow("Unread first")
    val messageFilterSetting = _messageFilterSetting.asStateFlow()

    private val _notificationSettings = MutableStateFlow(listOf(NotificationSettings.PUSH))
    val notificationSettings = _notificationSettings.asStateFlow()

    /**
     * Switches the bookings ordering placeholder.
     *
     * @param newSort The ordering to switch to.
     */
    fun changeBookingSortTo(newSort: String) {
        _bookingSortSetting.value = newSort
    }

    /**
     * Switches the inbox alerting placeholder.
     *
     * @param newAlerts The alerting behavior to switch to.
     */
    fun changeInboxAlertsTo(newAlerts: String) {
        _inboxAlertsSetting.value = newAlerts
    }

    /**
     * Switches the message-list filtering placeholder.
     *
     * @param newFilter The filtering to switch to.
     */
    fun changeMessageFilterTo(newFilter: String) {
        _messageFilterSetting.value = newFilter
    }

    fun toggleNotificationSetting(setting: NotificationSettings, enabled: Boolean) {
        _notificationSettings.update { currentList ->
            if (enabled) {
                if (setting !in currentList) currentList + setting else currentList
            } else {
                currentList - setting
            }
        }
    }

    /**
     * Adopts the notification settings loaded for this user, so the Settings screen
     * shows the saved toggles instead of the default when it first reads them. The
     * list is applied as it was loaded: an empty list is a real choice (all
     * notifications off), not an absent record.
     *
     * @param enabled The settings loaded for this user, from [NotificationPreferencesService.loadNotificationSettings].
     */
    fun applyLoadedNotificationSettings(enabled: List<NotificationSettings>) {
        _notificationSettings.value = enabled
    }

    /**
     * Applies a loaded [AppSettingsModel]: every recorded entrant choice that is not
     * empty replaces that placeholder's default, so a user's saved settings are alive
     * before the first screen renders. Empty choices leave the defaults untouched.
     * The notification settings themselves live in [NotificationPreferencesService]
     * and are not part of this record's entrant group.
     *
     * @param record The loaded settings record, from [AppSettingsService.loadAppSettings].
     */
    fun applyLoadedSettings(record: AppSettingsModel) {
        if (record.entrantBookingsOrder.isNotEmpty()) {
            changeBookingSortTo(record.entrantBookingsOrder)
        }
        if (record.entrantInboxAlerts.isNotEmpty()) {
            changeInboxAlertsTo(record.entrantInboxAlerts)
        }
        if (record.entrantMessagesFilter.isNotEmpty()) {
            changeMessageFilterTo(record.entrantMessagesFilter)
        }
    }
}

/**
 * Provides a concrete [ViewModel] holding the Campsite-Owner-role settings edited from the
 * Settings modal while [AppView.OwnerView] is current.
 *
 * Each setting is kept as an immutable `StateFlow` so the owner settings panel can
 * observe changes and re-render.
 *
 * @property listingVisibilitySetting The current listing visibility placeholder.
 * @property lotteryOpenSetting Whether the owner's lotteries are open to new entrants.
 * @property availabilityNoticeSetting The current availability notice placeholder.
 */
class OwnerSettingsController: ViewModel() {
    private val _listingVisibilitySetting = MutableStateFlow("Visible to entrants")
    val listingVisibilitySetting = _listingVisibilitySetting.asStateFlow()

    private val _lotteryOpenSetting = MutableStateFlow(true)
    val lotteryOpenSetting = _lotteryOpenSetting.asStateFlow()

    private val _availabilityNoticeSetting = MutableStateFlow("Open calendar")
    val availabilityNoticeSetting = _availabilityNoticeSetting.asStateFlow()

    /**
     * Switches the listing visibility placeholder.
     *
     * @param newVisibility The visibility to switch to.
     */
    fun changeListingVisibilityTo(newVisibility: String) {
        _listingVisibilitySetting.value = newVisibility
    }

    /**
     * Switches whether the owner's lotteries accept new entrants.
     *
     * @param open Whether the lotteries should accept new entrants.
     */
    fun changeLotteryOpenTo(open: Boolean) {
        _lotteryOpenSetting.value = open
    }

    /**
     * Switches the availability notice placeholder.
     *
     * @param newNotice The notice to switch to.
     */
    fun changeAvailabilityNoticeTo(newNotice: String) {
        _availabilityNoticeSetting.value = newNotice
    }

    /**
     * Applies a loaded [AppSettingsModel]: every recorded owner choice that is not
     * empty replaces that placeholder's default, so a user's saved settings are alive
     * before the first screen renders. Empty choices leave the defaults untouched;
     * the record's lottery boolean defaults to the same value as the controller's,
     * so an absent choice changes nothing.
     *
     * @param record The loaded settings record, from [AppSettingsService.loadAppSettings].
     */
    fun applyLoadedSettings(record: AppSettingsModel) {
        if (record.ownerListingVisibility.isNotEmpty()) {
            changeListingVisibilityTo(record.ownerListingVisibility)
        }
        _lotteryOpenSetting.value = record.ownerLotteryAccepting
        if (record.ownerAvailabilityNotice.isNotEmpty()) {
            changeAvailabilityNoticeTo(record.ownerAvailabilityNotice)
        }
    }
}

/**
 * Provides a concrete [ViewModel] holding the dropdown expansion state of the Settings
 * screen and the wiring of its back navigation.
 *
 * Each dropdown group has its expansion kept as an immutable `StateFlow`, so the
 * screen re-renders just that group when a tap opens or closes it.
 *
 * @see expandedStateOf The readonly `StateFlow` for a named dropdown group.
 */
class SettingsDisplayController: ViewModel() {
    // one state per known dropdown group from SettingsGroupId; groups start closed.
    private val appViewSwitchState = MutableStateFlow(false)
    private val adminSettingsState = MutableStateFlow(false)
    private val entrantSettingsState = MutableStateFlow(false)
    private val ownerSettingsState = MutableStateFlow(false)
    private val unknownGroupState = MutableStateFlow(false)

    /**
     * Provides the current expansion of the named dropdown group.
     *
     * @param groupId The stable name of the dropdown group.
     * @return A readonly [StateFlow] of whether that group is opened.
     */
    fun expandedStateOf(groupId: String): StateFlow<Boolean> =
        when (groupId) {
            "APP_VIEW_SWITCH" -> appViewSwitchState.asStateFlow()
            "ADMIN_SETTINGS" -> adminSettingsState.asStateFlow()
            "ENTRANT_SETTINGS" -> entrantSettingsState.asStateFlow()
            "OWNER_SETTINGS" -> ownerSettingsState.asStateFlow()
            else -> unknownGroupState.asStateFlow()
        }

    /**
     * Opens the named dropdown group if it was closed, or closes it if it was open.
     *
     * @param groupId The stable name of the dropdown group.
     */
    fun toggleGroup(groupId: String) {
        when (groupId) {
            "APP_VIEW_SWITCH" -> appViewSwitchState.value = !appViewSwitchState.value
            "ADMIN_SETTINGS" -> adminSettingsState.value = !adminSettingsState.value
            "ENTRANT_SETTINGS" -> entrantSettingsState.value = !entrantSettingsState.value
            "OWNER_SETTINGS" -> ownerSettingsState.value = !ownerSettingsState.value
            else -> unknownGroupState.value = !unknownGroupState.value
        }
    }
}
