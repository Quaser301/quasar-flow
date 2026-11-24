package com.example.campbnb.notifications

import com.example.campbnb.models.NotificationType
import com.example.campbnb.models.Notification
import com.example.campbnb.Screen

/**
 * Resolves what a tapped notification opens, both ways it crosses the app:
 * [screenFor] names the in-app destination as a typed [Screen] route — the
 * compiler-checked vocabulary every in-app tap navigates by — and [routeFor]
 * writes the same decision as the ONE text format the Android Intent extra
 * carries across the OS boundary, which [screenFromIntentText] reads back.
 * The two never disagree: both switch on the same notification fields.
 */
object NotificationRoutes {
    const val EXTRA_ROUTE = "campbnb.route"
    const val EXTRA_NOTIFICATION_ID = "campbnb.notificationId"

    const val NOTIFICATIONS = "notifications"
    const val MESSAGES = "messages"
    const val BOOKING = "booking"
    const val RESERVATIONS = "reservations"
    const val CHAT = "chat"

    fun routeFor(n: Notification): String = when (n.type) {
        NotificationType.WIN,
        // An invitation opens the lottery the invited owner was asked to join.
        NotificationType.INVITE ->
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

    /**
     * The in-app destination a tap of [n] opens, as a typed [Screen] route: every
     * in-app notice tap navigates by this, so a renamed or removed screen breaks the
     * caller at compile time rather than opening a dead string route at runtime.
     *
     * @param n The tapped notification.
     * @return The screen the notice names; the notices view when it names none.
     */
    fun screenFor(n: Notification): Screen = when (n.type) {
        NotificationType.WIN,
        // An invitation opens the lottery the invited owner was asked to join.
        NotificationType.INVITE ->
            if (n.lotteryId.isNotBlank()) Screen.BookingEntry(n.lotteryId) else Screen.Notifications

        NotificationType.LOSE,
        NotificationType.OWNER_NOTICE,
        NotificationType.CONFIRMATION,
        NotificationType.CANCELLATION,
        NotificationType.REJECTION -> Screen.NotifReservations

        NotificationType.MESSAGE ->
            if (n.reservationId.isNotBlank()) Screen.ChatReservationEntry(n.reservationId) else
                Screen.Messages
    }

    /**
     * Reads one [routeFor] text back as the screen to open: the pending-route
     * bridge hands the Intent's text to the app graph, which navigates the screen
     * it names. Text that names no known route returns null: nothing is opened.
     *
     * @param intentRoute The text carried by the tap Intent's [EXTRA_ROUTE] extra.
     * @return The screen the text names, or null when it names none.
     */
    fun screenFromIntentText(intentRoute: String): Screen? = when {
        intentRoute == RESERVATIONS -> Screen.NotifReservations
        intentRoute == NOTIFICATIONS -> Screen.Notifications
        intentRoute == MESSAGES -> Screen.Messages
        intentRoute.startsWith("$BOOKING/") ->
            Screen.BookingEntry(intentRoute.substringAfter("$BOOKING/"))
        intentRoute.startsWith("$CHAT/") ->
            Screen.ChatReservationEntry(intentRoute.substringAfter("$CHAT/"))
        else -> null
    }
}