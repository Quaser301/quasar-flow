package com.example.campbnb

import com.example.campbnb.data.InMemoryAppSettingsService
import com.example.campbnb.models.AppSettingsModel
import com.example.campbnb.viewModels.AppView
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOTICE: This file was AI-assisted. The verbatim prompts for this work are recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 */

class AppSettingsServiceTest {

    @Test
    fun test_default_record_holds_no_choices_and_accepts_lotteries() {
        val record = InMemoryAppSettingsService().appSettingsFor("demo-entrant")

        assertTrue("no app view is chosen by default", record.appViewChoice == "")
        assertTrue("no entrant choice is recorded by default", record.entrantBookingsOrder == "")
        assertTrue("no owner choice is recorded by default", record.ownerListingVisibility == "")
        assertTrue("lotteries accept entrants by default", record.ownerLotteryAccepting)
    }

    @Test
    fun test_view_choice_is_saved_and_read_back() {
        val appSettings = InMemoryAppSettingsService()
        var saved = false

        appSettings.setAppViewChoice("admin-one", AppView.OwnerView) { result ->
            saved = result.isSuccess
        }
        assertTrue("the service reports a successful save", saved)

        assertTrue(
            "the user's record now names the owner view",
            appSettings.appSettingsFor("admin-one").appViewChoice == "OwnerView"
        )
    }

    @Test
    fun test_an_owner_group_save_keeps_the_view_choice() {
        val appSettings = InMemoryAppSettingsService()

        appSettings.setAppViewChoice("admin-one", AppView.EntrantView) {}
        appSettings.setOwnerSettings(
            "admin-one",
            listingVisibility = "Hidden from entrants",
            lotteryAccepting = false,
            availabilityNotice = "Closed in winter"
        ) {}

        val record = appSettings.appSettingsFor("admin-one")
        assertTrue("the saved owner visibility is in the record", record.ownerListingVisibility == "Hidden from entrants")
        assertTrue("the saved lottery choice is in the record", !record.ownerLotteryAccepting)
        assertTrue(
            "the earlier view choice was not erased by the owner save",
            record.appViewChoice == "EntrantView"
        )
    }

    @Test
    fun test_an_entrant_group_save_keeps_the_view_choice() {
        val appSettings = InMemoryAppSettingsService()

        appSettings.setAppViewChoice("admin-one", AppView.OwnerView) {}
        appSettings.setEntrantSettings(
            "admin-one",
            bookingsOrder = "Cheapest first",
            inboxAlerts = "Important only",
            messagesFilter = "All messages"
        ) {}

        val record = appSettings.appSettingsFor("admin-one")
        assertTrue("the saved entrant ordering is in the record", record.entrantBookingsOrder == "Cheapest first")
        assertTrue("the saved alerting is in the record", record.entrantInboxAlerts == "Important only")
        assertTrue("the saved filtering is in the record", record.entrantMessagesFilter == "All messages")
        assertTrue(
            "the earlier view choice was not erased by the entrant save",
            record.appViewChoice == "OwnerView"
        )
    }

    @Test
    fun test_users_keep_their_own_app_settings() {
        val appSettings = InMemoryAppSettingsService()

        appSettings.setAppViewChoice("admin-one", AppView.OwnerView) {}
        appSettings.setAppViewChoice("admin-two", AppView.AdminView) {}

        assertTrue(
            "the first user keeps the owner view",
            appSettings.appSettingsFor("admin-one").appViewChoice == "OwnerView"
        )
        assertTrue(
            "the second user keeps the admin view",
            appSettings.appSettingsFor("admin-two").appViewChoice == "AdminView"
        )
    }

    @Test
    fun test_blank_user_app_settings_are_not_saved() {
        val appSettings = InMemoryAppSettingsService()
        var saved = false

        appSettings.setAppViewChoice("", AppView.OwnerView) { result ->
            saved = result.isSuccess
        }

        assertTrue("a blank user ID does not report a successful save", !saved)
        assertTrue(
            "a blank user ID keeps the boring default",
            appSettings.appSettingsFor("").appViewChoice == ""
        )
    }

    @Test
    fun test_the_blank_user_failure_names_itself_exactly() {
        val appSettings = InMemoryAppSettingsService()
        var failure = ""

        appSettings.setOwnerSettings(
            "",
            listingVisibility = "Visible to entrants",
            lotteryAccepting = true,
            availabilityNotice = "Open calendar"
        ) { result ->
            val failed = result.exceptionOrNull()
            failure = failed?.message ?: ""
        }

        assertTrue(
            "the failure copy is the exact stated one",
            failure == "Cannot save app settings without a user ID"
        )
    }

    @Test
    fun test_reading_known_and_unknown_users_answers_stored_or_default() {
        val appSettings = InMemoryAppSettingsService()
        appSettings.setAppViewChoice("reader", AppView.AdminView) {}

        var loaded: AppSettingsModel? = null
        appSettings.loadAppSettings(
            "reader",
            onSettings = { record -> loaded = record },
            onFailure = {}
        )
        assertTrue(
            "a read answers what is stored for that user",
            loaded != null && loaded!!.appViewChoice == "AdminView"
        )

        var defaultForNobody: AppSettingsModel? = null
        appSettings.loadAppSettings(
            "never-seen",
            onSettings = { record -> defaultForNobody = record },
            onFailure = {}
        )
        assertTrue(
            "a user with nothing stored answers the default",
            defaultForNobody != null && defaultForNobody!!.appViewChoice == ""
        )
    }
}
