package com.example.campbnb.data

// AI-assisted (app settings persistence across lifetimes): written by Qwen 3.8 Flash Next Coder IQ1_M.

import com.example.campbnb.database.QueryEngine
import com.example.campbnb.database.WriteEngine
import com.example.campbnb.models.AppSettingsModel
import com.example.campbnb.viewModels.AppView

private fun defaultAppSettings(): AppSettingsModel = AppSettingsModel()

/**
 * Storage boundary for the app settings a user keeps between app lifetimes: the
 * app view the user (as admin) is in, and the owner/entrant settings edited while
 * viewing as those roles.
 *
 * Tests use a controlled fake; the app provider persists the same settings to
 * storage, the same way [NotificationPreferencesService] keeps notification
 * settings. Each setter saves only its own group so the other groups of the
 * user's record are never lost by an unrelated change.
 */
interface AppSettingsService {
    fun appSettingsFor(userId: String): AppSettingsModel

    fun loadAppSettings(
        userId: String,
        onSettings: (AppSettingsModel) -> Unit,
        onFailure: (Exception) -> Unit
    )

    fun setAppViewChoice(
        userId: String,
        appViewChoice: AppView,
        onDone: (Result<Unit>) -> Unit
    )

    fun setEntrantSettings(
        userId: String,
        bookingsOrder: String,
        inboxAlerts: String,
        messagesFilter: String,
        onDone: (Result<Unit>) -> Unit
    )

    fun setOwnerSettings(
        userId: String,
        listingVisibility: String,
        lotteryAccepting: Boolean,
        availabilityNotice: String,
        onDone: (Result<Unit>) -> Unit
    )
}

class NoAppSettingsService : AppSettingsService {
    override fun appSettingsFor(userId: String): AppSettingsModel = defaultAppSettings()

    override fun loadAppSettings(
        userId: String,
        onSettings: (AppSettingsModel) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSettings(defaultAppSettings())
    }

    override fun setAppViewChoice(
        userId: String,
        appViewChoice: AppView,
        onDone: (Result<Unit>) -> Unit
    ) {
        onDone(Result.success(Unit))
    }

    override fun setEntrantSettings(
        userId: String,
        bookingsOrder: String,
        inboxAlerts: String,
        messagesFilter: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        onDone(Result.success(Unit))
    }

    override fun setOwnerSettings(
        userId: String,
        listingVisibility: String,
        lotteryAccepting: Boolean,
        availabilityNotice: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        onDone(Result.success(Unit))
    }
}

class InMemoryAppSettingsService : AppSettingsService {
    private val stored = mutableMapOf<String, AppSettingsModel>()

    override fun appSettingsFor(userId: String): AppSettingsModel =
        stored[userId] ?: defaultAppSettings()

    override fun loadAppSettings(
        userId: String,
        onSettings: (AppSettingsModel) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (userId.isBlank()) {
            onSettings(defaultAppSettings())
            return
        }

        val current = stored[userId] ?: AppSettingsModel(userId = userId)
        stored[userId] = current
        onSettings(current)
    }

    private fun mergedRecordFor(userId: String): AppSettingsModel =
        stored[userId] ?: AppSettingsModel(userId = userId)

    override fun setAppViewChoice(
        userId: String,
        appViewChoice: AppView,
        onDone: (Result<Unit>) -> Unit
    ) {
        if (userId.isBlank()) {
            onDone(Result.failure(Exception("Cannot save app settings without a user ID")))
            return
        }

        val current = mergedRecordFor(userId)
        current.appViewChoice = appViewChoice.name
        stored[userId] = current
        onDone(Result.success(Unit))
    }

    override fun setEntrantSettings(
        userId: String,
        bookingsOrder: String,
        inboxAlerts: String,
        messagesFilter: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        if (userId.isBlank()) {
            onDone(Result.failure(Exception("Cannot save app settings without a user ID")))
            return
        }

        val current = mergedRecordFor(userId)
        current.entrantBookingsOrder = bookingsOrder
        current.entrantInboxAlerts = inboxAlerts
        current.entrantMessagesFilter = messagesFilter
        stored[userId] = current
        onDone(Result.success(Unit))
    }

    override fun setOwnerSettings(
        userId: String,
        listingVisibility: String,
        lotteryAccepting: Boolean,
        availabilityNotice: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        if (userId.isBlank()) {
            onDone(Result.failure(Exception("Cannot save app settings without a user ID")))
            return
        }

        val current = mergedRecordFor(userId)
        current.ownerListingVisibility = listingVisibility
        current.ownerLotteryAccepting = lotteryAccepting
        current.ownerAvailabilityNotice = availabilityNotice
        stored[userId] = current
        onDone(Result.success(Unit))
    }
}

class FirestoreAppSettingsService(
    private val readEngine: QueryEngine<AppSettingsModel>,
    private val writeEngine: WriteEngine<AppSettingsModel>
) : AppSettingsService {
    private val stored = mutableMapOf<String, AppSettingsModel>()

    override fun appSettingsFor(userId: String): AppSettingsModel =
        stored[userId] ?: defaultAppSettings()

    override fun loadAppSettings(
        userId: String,
        onSettings: (AppSettingsModel) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (userId.isBlank()) {
            onSettings(defaultAppSettings())
            return
        }

        readEngine.getById(
            userId,
            { model ->
                val current = model ?: AppSettingsModel(userId = userId)
                stored[userId] = current
                onSettings(current)
            },
            { error -> onFailure(error) }
        )
    }

    private fun mergedRecordFor(userId: String): AppSettingsModel =
        stored[userId] ?: AppSettingsModel(userId = userId)

    private fun saveMerged(
        userId: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        val current = mergedRecordFor(userId)
        current.userId = userId

        writeEngine.save(current) { result ->
            if (result.isSuccess) {
                onDone(Result.success(Unit))
            } else {
                onDone(Result.failure(Exception("Could not save app settings")))
            }
        }
    }

    override fun setAppViewChoice(
        userId: String,
        appViewChoice: AppView,
        onDone: (Result<Unit>) -> Unit
    ) {
        if (userId.isBlank()) {
            onDone(Result.failure(Exception("Cannot save app settings without a user ID")))
            return
        }

        val current = mergedRecordFor(userId)
        current.appViewChoice = appViewChoice.name
        stored[userId] = current
        saveMerged(userId, onDone)
    }

    override fun setEntrantSettings(
        userId: String,
        bookingsOrder: String,
        inboxAlerts: String,
        messagesFilter: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        if (userId.isBlank()) {
            onDone(Result.failure(Exception("Cannot save app settings without a user ID")))
            return
        }

        val current = mergedRecordFor(userId)
        current.entrantBookingsOrder = bookingsOrder
        current.entrantInboxAlerts = inboxAlerts
        current.entrantMessagesFilter = messagesFilter
        stored[userId] = current
        saveMerged(userId, onDone)
    }

    override fun setOwnerSettings(
        userId: String,
        listingVisibility: String,
        lotteryAccepting: Boolean,
        availabilityNotice: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        if (userId.isBlank()) {
            onDone(Result.failure(Exception("Cannot save app settings without a user ID")))
            return
        }

        val current = mergedRecordFor(userId)
        current.ownerListingVisibility = listingVisibility
        current.ownerLotteryAccepting = lotteryAccepting
        current.ownerAvailabilityNotice = availabilityNotice
        stored[userId] = current
        saveMerged(userId, onDone)
    }
}
