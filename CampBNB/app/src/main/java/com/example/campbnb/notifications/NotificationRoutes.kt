package com.example.campbnb.notifications

import com.example.campbnb.models.NotificationType
import com.example.campbnb.models.Notification

/** */

object NotificationRoutes {
    const val EXTRA_ROUTE = "campbnb.route"
    const val EXTRA_NOTIFICATION_ID = "campbnb.notificationId"

    const val NOTIFICATIONS = "notifications"
    const val MESSAGES = "messages"
    const val BOOKING = "booking"
    const val RESERVATIONS = "reservations"
    const val CHAT = "chat"

    fun routeFor(n: Notification): String = when (n.type) {
        NotificationType.WIN ->
            if (n.lotteryId.isNotBlank()) "$BOOKING/${n.lotteryId}" else NOTIFICATIONS

        NotificationType.LOSE,
        NotificationType.OWNER_NOTICE,
        NotificationType.CONFIRMATION,
        NotificationType.CANCELLATION,
        NotificationType.REJECTION -> RESERVATIONS

        NotificationType.MESSAGE ->
            if (n.reservationId.isNotBlank()) "$CHAT/${n.reservationId}" else
                MESSAGES
    }
}