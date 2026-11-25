
package com.example.campbnb.controller

import com.example.campbnb.models.Message
import com.example.campbnb.models.NotificationIntent

/**
 * Notifies interested parties when a new message is sent.
 */
fun interface NewMessageNotifier {
    fun messageSent(message: Message, campsiteName: String)
}

/**
 * Connects the messaging system to the notification system.
 *
 * Called after a message has been successfully saved.
 */
class MessageNotificationAdapter(
    private val notifications: NotificationController
) : NewMessageNotifier {

    override fun messageSent(
        message: Message,
        campsiteName: String
    ) {
        notifications.notify(NotificationIntent.MessageArrived(message, campsiteName))
    }
}

