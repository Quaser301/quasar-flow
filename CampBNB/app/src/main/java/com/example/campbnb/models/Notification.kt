package com.example.campbnb.models

import com.example.campbnb.database.Identifiable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents all fields one notification holds for a single user
 *
 * A Notification is the permanent in-app record. Separate from the Android system notification and Firebase messaging push
 *
 * The same record is used by the notification inbox and the push functions
 *
 * @property recipientId ID of the user who receives the notification
 * @property senderId ID of the user who caused the notification, or an empty when notification was created by the system
 * @property type The category of notification, such as WIN or LOSE.
 * @property title Short title displayed in the notification inbox and phone.
 * @property message Full message displayed to the recipient.
 * @property lotteryId Lottery related to this notification, if applicable.
 * @property reservationId Reservation related to this notification, if applicable.
 * @property campsiteName Campsite name copied when the notification is created.
 * @property status Whether the recipient has opened the notification.
 * @property pushAllowed Whether the server is allowed to send a push for it.
 * @property sentAt Server timestamp recording when the notification was saved.
 * @property readAt Timestamp recording when the recipient opened it.
 */

class Notification (
    var recipientId: String = "",
    var senderId: String = "",
    var type: NotificationType = NotificationType.OWNER_NOTICE,
    var title: String = "",
    var message: String = "",
    var lotteryId: String = "",
    var reservationId: String = "",
    var campsiteName: String = "",
    var status: NotificationStatus = NotificationStatus.UNREAD,
    var pushAllowed: Boolean = false,
    @ServerTimestamp var sentAt: Timestamp? = null,
    var readAt: Timestamp? = null
) : Identifiable()

/**
 * Notification categories
 *
 * WIN and LOSE are used for the lottery results
 * CONFIRMATION, CANCELLATION and REJECTION are reserved for reservation events
 * MESSAGE is used when a new owner/entrant message is received
 */
enum class NotificationType {
    WIN,
    LOSE,
    CONFIRMATION,
    CANCELLATION,
    REJECTION,
    OWNER_NOTICE,
    MESSAGE
}

/**
 * The state of a saved notification
 */
enum class NotificationStatus {
    UNREAD,
    READ
}