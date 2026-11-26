package com.example.campbnb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.campbnb.controller.AdminController
import com.example.campbnb.controller.MessageController
import com.example.campbnb.controller.MessageNotificationAdapter
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.FirestoreMessageRepository
import com.example.campbnb.data.FirestoreNotificationRepository
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.MessageRepository
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.NotificationPreferencesService
import com.example.campbnb.data.AppSettingsService
import com.example.campbnb.data.FirestoreAppSettingsService
import com.example.campbnb.data.FileDownloadWriter
import com.example.campbnb.data.PublicDownloadsFileDownloadWriter
import com.example.campbnb.data.FirestoreNotificationPreferencesService
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Role
import com.example.campbnb.Screen
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.shared.DeviceId
import com.example.campbnb.viewModels.AppViewSettingController
import com.example.campbnb.viewModels.EntrantSettingsController
import com.example.campbnb.viewModels.OwnerSettingsController

/**
 * Central place for creating and connecting app dependencies.
 *
 * The demo user IDs are temporary.
 * Replace them when the real UserController is integrated.
 */
object AppContainer: Dependencies {

    // Temporary demo users.
    const val DEMO_ENTRANT = "demo-entrant"
    const val DEMO_OWNER = "demo-owner"

    override var deviceID by mutableStateOf("")

    override val userModel: UserModel by lazy {
        UserModel()
    }

    /**
     * Current user.
     * Compose screens observing this value redraw when it changes.
     */
    override var currentUserId by mutableStateOf(DEMO_ENTRANT)

    /**
     * Whether the signed-in user may act as an owner, as recorded on their saved profile:
     * the owner role itself, or an admin, who acts as an owner and reaches every screen
     * an owner does. This is what decides the owner's own sent notices view, so a user
     * reaches it however they signed in, not only as one of the demo users.
     */
    override val currentUserIsOwner: Boolean
        get() = userModel.currentUser?.roles?.any { it == Role.OWNER || it == Role.ADMIN } ?: false

    /**
     * Creates the notification repository once, when first needed.
     */
    override val notificationRepository: NotificationRepository by lazy {
        FirestoreNotificationRepository()
    }

    override val userRepository: UserRepository by lazy {
        UserRepository()
    }

    /**
     * Creates the reservation repository once, when first needed.
     */
    override val reservationRepository: ReservationRepository by lazy {
        ReservationRepository(
            Database.Reservations.readEngine(),
            Database.Reservations.writeEngine(),
            Database.BookedDates.writeEngine()
        )
    }

    /**
     * Creates the message repository once, when first needed.
     */
    override val messageRepository: MessageRepository by lazy {
        FirestoreMessageRepository()
    }

    override val campsiteRepository: CampsiteRepository by lazy {
        CampsiteRepository()
    }

    /**
     * Creates the notification controller once, when first needed.
     * The UserController is the opt-out check: users who turned notifications off still get the
     * in-app record but no banner (US 01.10.01).
     */
    override val notificationController: NotificationController by lazy {
        NotificationController(
            notificationRepository,
            userController
        )
    }

    /**
     * Creates the admin controller once, when first needed.
     * It carries both halves an admin needs: the deletions of profiles and campsites that
     * break app policy, and the policy actions over someone else's listing (US 02.10.02).
     * Both act through the shared user, campsite, lottery and notification services so the
     * removed party is told what happened and why.
     */
    override val adminController: AdminController by lazy {
        AdminController(
            userController,
            userRepository,
            campsiteRepository,
            LotteryRepository(),
            notificationController
        )
    }

    /**
     * Creates the message controller once, when first needed.
     * Every sent message notifies the recipient through the notification controller.
     */
    override val messageController: MessageController by lazy {
        MessageController(
            repo = messageRepository,
            reservationRepository = reservationRepository,
            notifier = messageNotificationAdapter,
            campsiteNameLookup = { campsiteId, onName ->
                if (campsiteId.isBlank()) {
                    onName("")
                } else {
                    Database.Campsites.readEngine().getById(
                        campsiteId,
                        { campsite -> onName(campsite?.campName.orEmpty()) },
                        { onName("") }
                    )
                }
            }
        )
    }

    override val userController: UserController by lazy {
        UserController(userModel, userRepository)
    }

    override val messageNotificationAdapter: MessageNotificationAdapter by lazy {
        MessageNotificationAdapter(notificationController)
    }

    override val appViewSettings by mutableStateOf(AppViewSettingController())

    override val entrantSettings by lazy { EntrantSettingsController() }

    override val ownerSettings by lazy { OwnerSettingsController() }

    override val notificationPreferencesService: NotificationPreferencesService by lazy {
        FirestoreNotificationPreferencesService(
            Database.NotificationPreferences.readEngine(),
            Database.NotificationPreferences.writeEngine()
        )
    }

    override val appSettingsService: AppSettingsService by lazy {
        FirestoreAppSettingsService(
            Database.AppSettings.readEngine(),
            Database.AppSettings.writeEngine()
        )
    }

    override val fileDownloadWriter: FileDownloadWriter by lazy {
        PublicDownloadsFileDownloadWriter()
    }

    override var pendingScreen: Screen? by mutableStateOf(null)

    override val onPendingRouteHandled: () -> Unit = { pendingScreen = null }

    override var homeTab: Screen? by mutableStateOf(null)

    // The app asked the device for its camera at start; this is the answer that came
    // back, and the scanner screen opens a camera only on a yes.
    override var cameraAllowed by mutableStateOf(false)

    fun init(context: android.content.Context) {
        deviceID = DeviceId.get(context)
        currentUserId = deviceID
        // Notification settings are seeded here so the entrant's saved toggles are
        // alive before the Settings screen first reads them.
        notificationPreferencesService.loadNotificationSettings(
            deviceID,
            { enabled -> entrantSettings.applyLoadedNotificationSettings(enabled) },
            { _ -> }
        )
        // App settings are loaded here so a user's saved view and saved role settings
        // are applied to their controllers before any screen reads them.
        appSettingsService.loadAppSettings(
            deviceID,
            { record ->
                appViewSettings.applyLoadedSettings(record)
                entrantSettings.applyLoadedSettings(record)
                ownerSettings.applyLoadedSettings(record)
            },
            { _ -> }
        )
    }
}