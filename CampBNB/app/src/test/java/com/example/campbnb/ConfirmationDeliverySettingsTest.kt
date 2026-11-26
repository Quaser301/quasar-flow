package com.example.campbnb

import com.example.campbnb.models.ConfirmationDelivery
import com.example.campbnb.models.NotificationSettings
import com.example.campbnb.models.confirmationDeliveryOf
import com.example.campbnb.models.settingsWithDelivery
import com.example.campbnb.viewModels.EntrantSettingsController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * US 01.05.02: the Confirmation Settings dropdown (Notification or CSV), the pending
 * change that the Update button saves, and the discard that leaving without saving does.
 */
class ConfirmationDeliverySettingsTest {

    // ---- the dropdown choice read from, and written to, the saved settings list ----

    @Test
    fun noCsvFlagReadsAsNotification() {
        assertEquals(ConfirmationDelivery.NOTIFICATION, confirmationDeliveryOf(emptyList()))
        assertEquals(
            ConfirmationDelivery.NOTIFICATION,
            confirmationDeliveryOf(listOf(NotificationSettings.PUSH))
        )
    }

    @Test
    fun csvFlagReadsAsCsvEvenWithPushOn() {
        assertEquals(
            ConfirmationDelivery.CSV,
            confirmationDeliveryOf(listOf(NotificationSettings.CSV))
        )
        assertEquals(
            ConfirmationDelivery.CSV,
            confirmationDeliveryOf(listOf(NotificationSettings.PUSH, NotificationSettings.CSV))
        )
    }

    @Test
    fun choosingNotificationDropsCsvAndKeepsPushOn() {
        val result = settingsWithDelivery(
            listOf(NotificationSettings.CSV),
            ConfirmationDelivery.NOTIFICATION
        )

        assertEquals(listOf(NotificationSettings.PUSH), result)
    }

    @Test
    fun choosingCsvKeepsPushAndNeverDuplicatesCsv() {
        val result = settingsWithDelivery(
            listOf(NotificationSettings.PUSH, NotificationSettings.CSV),
            ConfirmationDelivery.CSV
        )

        assertEquals(listOf(NotificationSettings.PUSH, NotificationSettings.CSV), result)
    }

    // ---- the controller's pending change, as the Update button sees it ----

    @Test
    fun defaultIsNotificationWithNothingPending() {
        val settings = EntrantSettingsController()

        assertEquals(ConfirmationDelivery.NOTIFICATION, settings.savedDelivery())
        assertNull(settings.pendingDelivery.value)
    }

    @Test
    fun choosingADifferentOptionIsPendingUntilSaved() {
        val settings = EntrantSettingsController()

        settings.chooseDelivery(ConfirmationDelivery.CSV)

        assertEquals(ConfirmationDelivery.CSV, settings.pendingDelivery.value)
        assertEquals(
            "the saved setting does not move until Update is pressed",
            ConfirmationDelivery.NOTIFICATION,
            settings.savedDelivery()
        )
    }

    @Test
    fun choosingTheSavedOptionAgainClearsThePendingChange() {
        val settings = EntrantSettingsController()

        settings.chooseDelivery(ConfirmationDelivery.CSV)
        settings.chooseDelivery(ConfirmationDelivery.NOTIFICATION)

        assertNull("Update goes back to disabled", settings.pendingDelivery.value)
    }

    @Test
    fun updateAppliesThePendingChangeAtOnce() {
        val settings = EntrantSettingsController()
        settings.chooseDelivery(ConfirmationDelivery.CSV)

        val toSave = settings.commitPending()

        assertEquals(ConfirmationDelivery.CSV, settings.savedDelivery())
        assertNull(settings.pendingDelivery.value)
        assertTrue(NotificationSettings.CSV in toSave)
        assertTrue(NotificationSettings.PUSH in toSave)
    }

    @Test
    fun updateWithNothingPendingChangesNothing() {
        val settings = EntrantSettingsController()
        val before = settings.notificationSettings.value

        val toSave = settings.commitPending()

        assertEquals(before, toSave)
        assertEquals(before, settings.notificationSettings.value)
    }

    @Test
    fun discardingDropsThePendingChangeAndKeepsTheSavedOne() {
        val settings = EntrantSettingsController()
        settings.chooseDelivery(ConfirmationDelivery.CSV)

        settings.discardPending()

        assertNull(settings.pendingDelivery.value)
        assertEquals(ConfirmationDelivery.NOTIFICATION, settings.savedDelivery())
    }

    @Test
    fun savedCsvSwitchesBackToNotificationThroughUpdate() {
        val settings = EntrantSettingsController()
        settings.applyLoadedNotificationSettings(listOf(NotificationSettings.CSV))
        assertEquals(ConfirmationDelivery.CSV, settings.savedDelivery())

        settings.chooseDelivery(ConfirmationDelivery.NOTIFICATION)
        val toSave = settings.commitPending()

        assertEquals(ConfirmationDelivery.NOTIFICATION, settings.savedDelivery())
        assertTrue(NotificationSettings.CSV !in toSave)
        assertTrue(NotificationSettings.PUSH in toSave)
    }

    @Test
    fun loadedSettingsShowAsTheSavedChoice() {
        val settings = EntrantSettingsController()

        settings.applyLoadedNotificationSettings(
            listOf(NotificationSettings.PUSH, NotificationSettings.CSV)
        )

        assertEquals(ConfirmationDelivery.CSV, settings.savedDelivery())
    }
}