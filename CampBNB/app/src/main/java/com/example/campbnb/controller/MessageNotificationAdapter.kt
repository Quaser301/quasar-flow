
package com.example.campbnb.controller

import com.example.campbnb.models.Message

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
        notifications.send(
            recipientId = message.recipientId,
            type = "MESSAGE",
            details = mapOf(
                "reservationId" to message.reservationId,
                "campsiteName" to campsiteName,
                "senderId" to message.senderId,
                "preview" to message.content.take(100)
            )
        )
    }
}

