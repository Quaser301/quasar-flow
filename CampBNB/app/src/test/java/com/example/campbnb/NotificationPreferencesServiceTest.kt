package com.example.campbnb

import com.example.campbnb.data.InMemoryNotificationPreferencesService
import com.example.campbnb.models.NotificationSettings
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreferencesServiceTest {

    @Test
    fun test_default_settings_keep_push_enabled_and_csv_disabled() {
        val settings = InMemoryNotificationPreferencesService().notificationSettingsFor("demo-entrant")

        assertTrue(
            "push is on by default",
            NotificationSettings.PUSH in settings
        )
        assertTrue(
            "csv is off by default",
            NotificationSettings.CSV !in settings
        )
    }

    @Test
    fun test_setting_toggle_enables_csv_download() {
        val settings = InMemoryNotificationPreferencesService()
        var saved = false

        settings.setNotificationSettings("demo-entrant", listOf(NotificationSettings.CSV)) { result ->
            saved = result.isSuccess
        }

        assertTrue(
            "the service reports a successful save",
            saved
        )
        assertTrue(
            "the user's current settings now enable CSV",
            NotificationSettings.CSV in settings.notificationSettingsFor("demo-entrant")
        )
    }

    @Test
    fun test_users_keep_their_own_notification_settings() {
        val settings = InMemoryNotificationPreferencesService()
        var saved = false

        settings.setNotificationSettings("camp-one", listOf(NotificationSettings.CSV)) { result ->
            saved = result.isSuccess
        }
        assertTrue(
            "the first user can save csv settings",
            saved
        )

        settings.setNotificationSettings("camp-two", listOf(NotificationSettings.PUSH)) { result ->
            saved = result.isSuccess
        }
        assertTrue(
            "the second user can keep push settings",
            saved
        )

        val firstSettings = settings.notificationSettingsFor("camp-one")
        val secondSettings = settings.notificationSettingsFor("camp-two")

        assertTrue(
            "csv stays enabled for the first user",
            NotificationSettings.CSV in firstSettings
        )
        assertTrue(
            "push stays disabled for the first user",
            NotificationSettings.PUSH !in firstSettings
        )
        assertTrue(
            "csv stays disabled for the second user",
            NotificationSettings.CSV !in secondSettings
        )
    }

    @Test
    fun test_notification_settings_can_disable_every_option() {
        val settings = InMemoryNotificationPreferencesService()
        var saved = false

        settings.setNotificationSettings("no-notifications", emptyList()) { result ->
            saved = result.isSuccess
        }

        assertTrue(
            "the service reports a successful save",
            saved
        )
        assertTrue(
            "the user can turn off every option",
            settings.notificationSettingsFor("no-notifications").isEmpty()
        )
    }

    @Test
    fun test_blank_user_settings_are_not_saved() {
        val settings = InMemoryNotificationPreferencesService()
        var saved = false

        settings.setNotificationSettings("", listOf(NotificationSettings.CSV)) { result ->
            saved = result.isSuccess
        }

        assertTrue(
            "a blank user ID does not report a successful save",
            !saved
        )
        assertTrue(
            "a blank user ID keeps the boring default",
            settings.notificationSettingsFor("") == listOf(NotificationSettings.PUSH)
        )
    }

    @Test
    fun test_the_blank_user_failure_names_itself_exactly() {
        val settings = InMemoryNotificationPreferencesService()
        var failure = ""

        settings.setNotificationSettings("", listOf(NotificationSettings.PUSH)) { result ->
            val failed = result.exceptionOrNull()
            failure = failed?.message ?: ""
        }

        assertTrue(
            "the failure copy is the exact stated one",
            failure == "Cannot save settings without a user ID"
        )
    }

    @Test
    fun test_reading_known_and_unknown_users_answers_stored_or_default() {
        val settings = InMemoryNotificationPreferencesService()
        settings.setNotificationSettings("reader", listOf(NotificationSettings.CSV)) {}

        var loaded: List<NotificationSettings>? = null
        settings.loadNotificationSettings(
            "reader",
            onSettings = { stored -> loaded = stored },
            onFailure = {}
        )
        assertTrue(
            "a read answers what is stored for that user",
            loaded != null && NotificationSettings.CSV in loaded!! &&
                    NotificationSettings.PUSH !in loaded!!
        )

        var defaultForNobody: List<NotificationSettings>? = null
        settings.loadNotificationSettings(
            "never-seen",
            onSettings = { stored -> defaultForNobody = stored },
            onFailure = {}
        )
        assertTrue(
            "a user with nothing stored answers the default",
            defaultForNobody == listOf(NotificationSettings.PUSH)
        )
    }

    @Test
    fun test_a_whitespace_user_id_is_blank_and_refused() {
        val settings = InMemoryNotificationPreferencesService()
        var failure = ""

        settings.setNotificationSettings("  ", listOf(NotificationSettings.CSV)) { result ->
            val failed = result.exceptionOrNull()
            failure = failed?.message ?: ""
        }

        assertTrue(
            "a whitespace-only id is blank, so it answers the exact refusal copy",
            failure == "Cannot save settings without a user ID"
        )
        assertTrue(
            "nothing is stored for a blank user, so the read answers the default",
            settings.notificationSettingsFor("  ") == listOf(NotificationSettings.PUSH)
        )
    }
}