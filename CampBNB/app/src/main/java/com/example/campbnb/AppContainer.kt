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
import com.example.campbnb.data.NotificationUpdate
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationStatus
import com.example.campbnb.models.UserModel
import kotlin.collections.forEach

/**
 * Central place for creating and connecting app dependencies.
 *
 * The demo user IDs are temporary.
 * Replace them when the real UserController is integrated.
 */
object AppContainer {

    // Temporary demo users.
    const val DEMO_ENTRANT = "demo-entrant"
    const val DEMO_OWNER = "demo-owner"

    /**
     * Current user.
     * Compose screens observing this value redraw when it changes.
     */
    var currentUserId by mutableStateOf(DEMO_ENTRANT)

    /**
     * Temporary owner-role check.
     * Replace this with the real UserController role check.
     */
    val currentUserIsOwner: Boolean
        get() = currentUserId == DEMO_OWNER

    /**
     * Creates the notification repository once, when first needed.
     */
    val notificationRepository: NotificationRepository by lazy {
        FirestoreNotificationRepository()
    }

    val userRepository: UserRepository by lazy {
        UserRepository()
    }

    /**
     * Creates the reservation repository once, when first needed.
     */
    val reservationRepository: ReservationRepository by lazy {
        ReservationRepository(
            Database.Reservations.readEngine(),
            Database.Reservations.writeEngine(),
            Database.BookedDates.writeEngine()
        )
    }

    /**
     * Creates the message repository once, when first needed.
     */
    val messageRepository: MessageRepository by lazy {
        FirestoreMessageRepository()
    }

    val campsiteRepository: CampsiteRepository by lazy {
        CampsiteRepository()
    }

    /**
     * Creates the notification controller once, when first needed.
     * The UserController is the opt-out check: users who turned notifications off still get the
     * in-app record but no banner (US 01.10.01).
     */
    val notificationController: NotificationController by lazy {
        NotificationController(
            notificationRepository,
            UserController(UserModel(), UserRepository())
        )
    }

    /**
     * Creates the message controller once, when first needed.
     * Every sent message notifies the recipient through the notification controller.
     */
    val messageController: MessageController by lazy {
        MessageController(
            repo = messageRepository,
            reservationRepository = reservationRepository,
            notifier = MessageNotificationAdapter(notificationController),
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

    val userController: UserController by lazy {
        UserController(userModel, userRepository)
    }

    val userModel: UserModel by lazy {
        UserModel()
    }

    val messageNotificationAdapter: MessageNotificationAdapter by lazy {
        MessageNotificationAdapter(notificationController)
    }
}