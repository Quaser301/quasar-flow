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

interface Dependencies {
    val deviceID: String
    val userModel: UserModel

    val pendingRoute: String?

    val onPendingRouteHandled: () -> Unit

    var currentUserId: String
    val currentUserIsOwner: Boolean
    val notificationRepository: NotificationRepository
    val userRepository: UserRepository
    val reservationRepository: ReservationRepository
    val messageRepository: MessageRepository
    val campsiteRepository: CampsiteRepository
    val notificationController: NotificationController
    val messageController: MessageController
    val userController: UserController
    val messageNotificationAdapter: MessageNotificationAdapter
    val appViewSettings: AppViewSettingController
}