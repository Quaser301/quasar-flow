package com.example.campbnb.data

import com.example.campbnb.database.QueryEngine
import com.example.campbnb.database.WriteEngine
import com.example.campbnb.models.NotificationPreferenceModel
import com.example.campbnb.models.NotificationSettings

private fun defaultNotificationPreferences(): List<NotificationSettings> = listOf(NotificationSettings.PUSH)

private fun settingsFromFlags(allowPush: Boolean, allowCsv: Boolean): List<NotificationSettings> =
    listOfNotNull(
        if (allowPush) NotificationSettings.PUSH else null,
        if (allowCsv) NotificationSettings.CSV else null
    )

private fun settingsFromModel(model: NotificationPreferenceModel?): List<NotificationSettings> {
    if (model == null) return defaultNotificationPreferences()
    return settingsFromFlags(model.allowPushNotifications, model.allowCsvReservationDownload)
}

private fun modelFromSettings(userId: String, enabled: List<NotificationSettings>): NotificationPreferenceModel =
    NotificationPreferenceModel(
        userId = userId,
        allowPushNotifications = NotificationSettings.PUSH in enabled,
        allowCsvReservationDownload = NotificationSettings.CSV in enabled
    )

/**
 * Storage boundary for per-user notification settings.
 *
 * Tests use a controlled fake; the app provider can persist the same settings to storage.
 * The read side stays simple so the UI can ask for the current value without needing an
 * async round trip after every button tap.
 */
interface NotificationPreferencesService {
    fun notificationSettingsFor(userId: String): List<NotificationSettings>

    fun loadNotificationSettings(
        userId: String,
        onSettings: (List<NotificationSettings>) -> Unit,
        onFailure: (Exception) -> Unit
    )

    fun setNotificationSettings(
        userId: String,
        enabled: List<NotificationSettings>,
        onDone: (Result<Unit>) -> Unit
    )
}

class NoNotificationPreferencesService : NotificationPreferencesService {
    override fun notificationSettingsFor(userId: String): List<NotificationSettings> =
        defaultNotificationPreferences()

    override fun loadNotificationSettings(
        userId: String,
        onSettings: (List<NotificationSettings>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSettings(defaultNotificationPreferences())
    }

    override fun setNotificationSettings(
        userId: String,
        enabled: List<NotificationSettings>,
        onDone: (Result<Unit>) -> Unit
    ) {
        onDone(Result.success(Unit))
    }
}

class InMemoryNotificationPreferencesService : NotificationPreferencesService {
    private val stored = mutableMapOf<String, List<NotificationSettings>>()

    override fun notificationSettingsFor(userId: String): List<NotificationSettings> =
        stored[userId] ?: defaultNotificationPreferences()

    override fun loadNotificationSettings(
        userId: String,
        onSettings: (List<NotificationSettings>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val current = stored[userId] ?: defaultNotificationPreferences()
        stored[userId] = current
        onSettings(current)
    }

    override fun setNotificationSettings(
        userId: String,
        enabled: List<NotificationSettings>,
        onDone: (Result<Unit>) -> Unit
    ) {
        if (userId.isBlank()) {
            onDone(Result.failure(Exception("Cannot save settings without a user ID")))
            return
        }

        stored[userId] = enabled
        onDone(Result.success(Unit))
    }
}

class FirestoreNotificationPreferencesService(
    private val readEngine: QueryEngine<NotificationPreferenceModel>,
    private val writeEngine: WriteEngine<NotificationPreferenceModel>
) : NotificationPreferencesService {
    private val stored = mutableMapOf<String, List<NotificationSettings>>()

    override fun notificationSettingsFor(userId: String): List<NotificationSettings> =
        stored[userId] ?: defaultNotificationPreferences()

    override fun loadNotificationSettings(
        userId: String,
        onSettings: (List<NotificationSettings>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (userId.isBlank()) {
            val current = defaultNotificationPreferences()
            onSettings(current)
            return
        }

        readEngine.getById(
            userId,
            { model ->
                val current = settingsFromModel(model)
                stored[userId] = current
                onSettings(current)
            },
            { error -> onFailure(error) }
        )
    }

    override fun setNotificationSettings(
        userId: String,
        enabled: List<NotificationSettings>,
        onDone: (Result<Unit>) -> Unit
    ) {
        if (userId.isBlank()) {
            onDone(Result.failure(Exception("Cannot save settings without a user ID")))
            return
        }

        stored[userId] = enabled

        writeEngine.save(modelFromSettings(userId, enabled)) { result ->
            if (result.isSuccess) {
                onDone(Result.success(Unit))
            } else {
                onDone(Result.failure(Exception("Could not save notification preferences")))
            }
        }
    }
}
