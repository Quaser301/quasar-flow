package com.example.campbnb

import com.example.campbnb.models.AppSettingsModel
import com.example.campbnb.models.NotificationSettings
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSettingController
import com.example.campbnb.viewModels.EntrantSettingsController
import com.example.campbnb.viewModels.OwnerSettingsController
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOTICE: This file was AI-assisted. The verbatim prompts for this work are recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 */

class AppSettingsSeedingTest {

    @Test
    fun test_a_saved_view_setting_resumes_where_the_user_left_it() {
        val viewController = AppViewSettingController()

        viewController.applyLoadedSettings(AppSettingsModel(appViewChoice = "OwnerView"))

        assertTrue(
            "the recorded owner view becomes the current setting",
            viewController.viewSetting.value == AppView.OwnerView
        )
    }

    @Test
    fun test_an_unrecorded_or_unknown_view_keeps_the_default() {
        val viewController = AppViewSettingController()

        viewController.applyLoadedSettings(AppSettingsModel(appViewChoice = "Nowhereville"))

        assertTrue(
            "an unknown choice does not crash and does not move the setting",
            viewController.viewSetting.value == AppView.EntrantView
        )
    }

    @Test
    fun test_saved_entrant_choices_seed_the_entrant_settings() {
        val entrantSettings = EntrantSettingsController()

        entrantSettings.applyLoadedSettings(
            AppSettingsModel(entrantBookingsOrder = "Cheapest first")
        )

        assertTrue(
            "the recorded ordering replaces the placeholder default",
            entrantSettings.bookingSortSetting.value == "Cheapest first"
        )
        assertTrue(
            "an unrecorded alerting keeps its default",
            entrantSettings.inboxAlertsSetting.value == "All activity"
        )
    }

    @Test
    fun test_loaded_notification_settings_replace_the_default_outright() {
        val entrantSettings = EntrantSettingsController()

        entrantSettings.applyLoadedNotificationSettings(listOf(NotificationSettings.CSV))

        assertTrue(
            "the loaded choices become the current ones",
            entrantSettings.notificationSettings.value == listOf(NotificationSettings.CSV)
        )
        assertTrue(
            "the default is not kept alongside the loaded list",
            NotificationSettings.PUSH !in entrantSettings.notificationSettings.value
        )
    }

    @Test
    fun test_turning_all_notifications_off_is_a_real_choice() {
        val entrantSettings = EntrantSettingsController()

        entrantSettings.applyLoadedNotificationSettings(emptyList())

        assertTrue(
            "an empty loaded list means all notifications off, not no record",
            entrantSettings.notificationSettings.value.isEmpty()
        )
    }

    @Test
    fun test_saved_owner_choices_seed_the_owner_settings() {
        val ownerSettings = OwnerSettingsController()

        ownerSettings.applyLoadedSettings(
            AppSettingsModel(
                ownerListingVisibility = "Hidden from entrants",
                ownerLotteryAccepting = false
            )
        )

        assertTrue(
            "the recorded visibility replaces the placeholder default",
            ownerSettings.listingVisibilitySetting.value == "Hidden from entrants"
        )
        assertTrue(
            "the recorded lottery choice is applied",
            !ownerSettings.lotteryOpenSetting.value
        )
        assertTrue(
            "an unrecorded notice keeps its default",
            ownerSettings.availabilityNoticeSetting.value == "Open calendar"
        )
    }

    @Test
    fun test_an_default_record_changes_nothing() {
        val ownerSettings = OwnerSettingsController()

        ownerSettings.applyLoadedSettings(AppSettingsModel())

        assertTrue(
            "the default record's lottery value equals the controller default, so nothing moves",
            ownerSettings.lotteryOpenSetting.value
        )
    }
}
