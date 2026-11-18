package com.example.campbnb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.campbnb.controller.MessageController
import com.example.campbnb.controller.MessageNotificationAdapter
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.FirestoreMessageRepository
import com.example.campbnb.data.FirestoreNotificationRepository
import com.example.campbnb.data.MessageRepository
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.shared.DeviceId
import com.example.campbnb.viewModels.AppViewSettingController

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
     * Temporary owner-role check.
     * Replace this with the real UserController role check.
     */
    override val currentUserIsOwner: Boolean
        get() = currentUserId == DEMO_OWNER

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

    override var pendingRoute: String? by mutableStateOf(null)

    override val onPendingRouteHandled: () -> Unit = { pendingRoute = null }

    fun init(context: android.content.Context) {
        deviceID = DeviceId.get(context)
        currentUserId = deviceID
    }
}