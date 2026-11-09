package com.example.campbnb.models

// AI-assisted (re-pull of US 03.08.01, model side): written by Qwen 3.8 Flash Next Coder
// IQ1_M. No disclosure-table row: the user asked to stop logging prompts at this point.

import com.example.campbnb.models.Notification
import com.example.campbnb.notifications.NotificationRoutes
import com.google.firebase.Timestamp

/**
 * One entry of the notification log: the records written together for one broadcast,
 * joined into a single line. Not an Identifiable: this is a view over the saved records,
 * nothing here is written to the database.
 *
 * @property logId The stable name of this log entry, used to open its dropdown.
 * @property senderId Who caused the notification, empty when the system created it.
 * @property recipientIds The distinct receivers of the notification, in first-seen order.
 * @property sentAt When the notification was saved, taken from its records.
 * @property title The short title the notification carried.
 * @property message The message body, kept exactly as it was written.
 * @property routeText Where opening that notification goes, from [NotificationRoutes].
 */
data class NotificationLogGroup(
    val logId: String,
    val senderId: String,
    val recipientIds: List<String>,
    val sentAt: Timestamp?,
    val title: String,
    val message: String,
    val routeText: String,
)

/**
 * The name one log entry is grouped under: a join of the record fields that describe
 * the broadcast itself, leaving out the per-receiver fields.
 *
 * The generators write every record of one broadcast with the same fields except the
 * receiver, so an equal key means the same event: [NotificationController] builds one
 * record per receiver of a draw with a shared lotteryId, and its send path writes a
 * single record per reservation or message event. Two different broadcasts cannot share
 * this key without also sharing the generated document id, which would overwrite the
 * first one in the collection.
 *
 * @param notification One saved notification record.
 * @return The join its log entry is grouped under.
 */
fun notificationLogKeyOf(notification: Notification): String =
    "${notification.senderId}${notification.type.name}" +
        "${notification.title}${notification.message}" +
        "${notification.lotteryId}${notification.reservationId}${notification.campsiteName}"

/**
 * The saved timestamp of a joined broadcast. Records of one broadcast share one server
 * timestamp; a record that has not been served yet carries none, so the first saved one
 * wins.
 *
 * @param members The records of one broadcast.
 * @return The saved timestamp, or nothing when none was saved.
 */
private fun sentAtOf(members: List<Notification>): Timestamp? =
    members.mapNotNull { it.sentAt }.firstOrNull()

/**
 * The log entries, from the saved records.
 *
 * Records of one broadcast join into one entry, the receivers are collected in order
 * without repeats, and the entries take the order their input gives them - the caller
 * sorts the records newest first first, which is what [NotificationController] does.
 *
 * @param notifications The saved notification records.
 * @return The log entries, one per broadcast; no records means no entries.
 */
fun notificationLogGroupsFor(notifications: List<Notification>): List<NotificationLogGroup> =
    notifications.groupBy { notificationLogKeyOf(it) }.values
        .map { members ->
            NotificationLogGroup(
                notificationLogKeyOf(members.first()),
                members.first().senderId,
                members.map { it.recipientId }.distinct(),
                sentAtOf(members),
                members.first().title,
                members.first().message,
                NotificationRoutes.routeFor(members.first())
            )
        }
