package com.example.campbnb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.FirestoreNotificationRepository
import com.example.campbnb.data.NotificationRepository

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

    /**
     * Creates the notification controller once, when first needed.
     */
    val notificationController: NotificationController by lazy {
        NotificationController(notificationRepository)
    }
}