package com.example.campbnb

import com.example.campbnb.controller.MessageController
import com.example.campbnb.controller.MessageNotificationAdapter
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.MessageRepository
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.UserModel
import com.example.campbnb.viewModels.AppViewSettingController

/**
 * Contract for services and session state that live for the whole app run.
 *
 * A provider supplies one stable value for each member. Screens and activities must
 * receive or read the supplied value instead of creating their own copy. Repository
 * and controller members are shared app services and storage boundaries. A test
 * provider may replace any member with a controlled fake.
 */
interface Dependencies {
    /** Stable identifier for the current device. */
    val deviceID: String

    /** Shared user state, including roles and the signed-in user. */
    val userModel: UserModel

    /** Pending external route target. Null means no route is waiting. */
    val pendingRoute: String?

    /** Clears the pending route after the app graph accepts it. */
    val onPendingRouteHandled: () -> Unit

    /** Current user id. The production provider still uses temporary demo ids. */
    var currentUserId: String

    /** Temporary owner-role shortcut. Replace it with the real user role check. */
    val currentUserIsOwner: Boolean

    /** Shared boundary for notification data. */
    val notificationRepository: NotificationRepository

    /** Shared boundary for user data. */
    val userRepository: UserRepository

    /** Shared boundary for reservation data. */
    val reservationRepository: ReservationRepository

    /** Shared boundary for message data. */
    val messageRepository: MessageRepository

    /** Shared boundary for campsite data. */
    val campsiteRepository: CampsiteRepository

    /** Shared notification behavior service. */
    val notificationController: NotificationController

    /** Shared message behavior service. */
    val messageController: MessageController

    /** Shared user behavior service. */
    val userController: UserController

    /** Shared bridge from message events to notification events. */
    val messageNotificationAdapter: MessageNotificationAdapter

    /** Shared current app view setting. */
    val appViewSettings: AppViewSettingController
}
