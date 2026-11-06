package com.example.campbnb.notifications

import com.example.campbnb.models.NotificationType
import com.example.campbnb.models.Notification

/**
 * The route names carried in a tapped platform notification, and the mapping from a
 * notification type to the app screen it opens.
 */
object NotificationRoutes {
    const val EXTRA_ROUTE = "campbnb.route"
    const val EXTRA_NOTIFICATION_ID = "campbnb.notificationId"

    const val NOTIFICATIONS = "notifications"
    const val MESSAGES = "messages"
    const val BOOKING = "booking"
    const val RESERVATIONS = "reservations"
    const val CHAT = "chat"

    /** The app screen a tap should open: a WIN routes to its booking, a MESSAGE to its
     *     chat; the rest go to their list screens. */
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