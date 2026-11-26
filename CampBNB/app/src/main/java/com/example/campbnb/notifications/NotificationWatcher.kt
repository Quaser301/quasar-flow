package com.example.campbnb.notifications

import android.util.Log
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.models.NotificationStatus
import com.example.campbnb.models.NotificationType


/**
 * Listens for new notifications belonging to the current user.
 *
 * Displays a system notification banner for each new unread notification
 * that allows push notifications.
 *
 * This Level 1 implementation works while the app process is alive.
 * It does not deliver notifications while the app is closed.
 */
class NotificationWatcher(
    private val controller: NotificationController,
    private val notifier: SystemNotifier,
    // Optional so existing callers and tests keep working without CSV delivery.
    private val csvDelivery: ConfirmationCsvDelivery? = null
) {

    // Stops the currently active listener.
    private var stopListening: (() -> Unit)? = null

    /**
     * Starts listening for notifications belonging to [userId].
     * Stops any previous listener before starting a new one.
     */
    fun start(userId: String) {
        stop()

        if (userId.isBlank()) return

        // The first snapshot contains old records.
        // Do not show banners for those records.
        var firstSnapshot = true

        stopListening = controller.listen(
            userId = userId,

            onUpdate = { update ->

                if (firstSnapshot) {
                    // Treat the first snapshot as the baseline.
                    firstSnapshot = false
                } else {

                    // Only show banners for new unread records
                    // where the user has allowed push notifications.
                    update.added
                        .filter {
                            it.status == NotificationStatus.UNREAD
                        }
                        .forEach { notification ->
                            val wantsCsv = csvDelivery != null &&
                                    notification.type == NotificationType.CONFIRMATION &&
                                    csvDelivery.wantsCsv(userId)

                            if (wantsCsv) {
                                // CSV chosen: the file replaces the banner. If it cannot be
                                // written, fall back to the banner so the entrant is still told.
                                csvDelivery.deliver(notification, userId) { delivered ->
                                    if (!delivered && notification.pushAllowed) {
                                        notifier.show(notification)
                                    }
                                }
                            } else if (notification.pushAllowed) {
                                notifier.show(notification)
                            }
                        }
                }
            },

            onError = { error ->
                Log.w(
                    "NotificationWatcher",
                    "Notification listener failed",
                    error
                )
            }
        )
    }

    /**
     * Stops listening for notifications.
     * Safe to call even when no listener is active.
     */
    fun stop() {
        stopListening?.invoke()
        stopListening = null
    }
}