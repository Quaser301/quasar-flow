package com.example.campbnb

import com.example.campbnb.controller.MessageController
import com.example.campbnb.controller.AdminController
import com.example.campbnb.controller.MessageNotificationAdapter
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.NotificationPreferencesService
import com.example.campbnb.data.AppSettingsService
import com.example.campbnb.data.FileDownloadWriter
import com.example.campbnb.viewModels.EntrantSettingsController
import com.example.campbnb.viewModels.OwnerSettingsController
import com.example.campbnb.data.MessageRepository
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.UserModel
import com.example.campbnb.viewModels.AppViewSettingController
import com.example.campbnb.controller.ReservationController
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.models.Availability
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

    /** Screen the app should open first when an external event queued one. Null means none is waiting. */
    val pendingScreen: Screen?

    /** Clears the pending route after the app graph accepts it. */
    val onPendingRouteHandled: () -> Unit

    /**
     * Screen the home bottom bar opens on the next time the app enters Home, named by
     * the Screen its content shows. Null means no tab was named and the bar opens on
     * its first tab. An entry to Home writes it immediately before navigating, so the
     * value an entry asked for is the value that Home reads.
     */
    var homeTab: Screen?

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

    /** Shared service for campsite availability. */
    val availability: Availability

    /** Shared lottery behavior used by reservation workflows. */
    val lotteryController: LotteryController

    /** Shared reservation workflow actions, including owner rejection. */
    val reservationController: ReservationController

    /** Shared boundary for message data. */
    val messageRepository: MessageRepository

    /** Shared boundary for campsite data. */
    val campsiteRepository: CampsiteRepository

    /** Shared notification behavior service. */
    val notificationController: NotificationController

    /** Shared admin policy actions over other users' listings (US 02.10.02, US 02.10.01). */
    val adminController: AdminController

    /** Shared message behavior service. */
    val messageController: MessageController

    /** Shared user behavior service. */
    val userController: UserController

    /** Shared bridge from message events to notification events. */
    val messageNotificationAdapter: MessageNotificationAdapter

    /** Shared current app view setting. */
    val appViewSettings: AppViewSettingController

    /** Shared settings state shown by the entrant settings panel. */
    val entrantSettings: EntrantSettingsController

    /** Shared settings state shown by the owner settings panel. */
    val ownerSettings: OwnerSettingsController

    /** Shared storage boundary for user notification settings. */
    val notificationPreferencesService: NotificationPreferencesService

    /** Shared storage boundary for the app settings kept between app lifetimes. */
    val appSettingsService: AppSettingsService

    /** Shared boundary for writing files the user downloads (reservation CSVs, QR code files). */
    val fileDownloadWriter: FileDownloadWriter
}
