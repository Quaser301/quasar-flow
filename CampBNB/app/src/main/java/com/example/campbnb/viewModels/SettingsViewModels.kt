package com.example.campbnb.viewModels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow



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
 */
class EntrantSettingsController: ViewModel() {
    private val _bookingSortSetting = MutableStateFlow("Newest first")
    val bookingSortSetting = _bookingSortSetting.asStateFlow()

    private val _inboxAlertsSetting = MutableStateFlow("All activity")
    val inboxAlertsSetting = _inboxAlertsSetting.asStateFlow()

    private val _messageFilterSetting = MutableStateFlow("Unread first")
    val messageFilterSetting = _messageFilterSetting.asStateFlow()

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
     * @param newAlerts The alerting behaviour to switch to.
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
}

/**
 * Provides a concrete [ViewModel] holding the Campsite-Owner-role settings edited from the
 * Settings modal while [AppView.OwnerView] is current.
 *
 * Each setting is kept as an immutable `StateFlow` so the owner settings panel can
 * observe changes and re-render.
 *
 * @property listingVisibilitySetting The current listing visibility placeholder.
 * @property lotteryOpenSetting Whether the owner&apos;s lotteries are open to new entrants.
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
     * Switches whether the owner&apos;s lotteries accept new entrants.
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
}
