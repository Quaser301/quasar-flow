package com.example.campbnb.models

// AI-assisted (re-pull of US 03.08.01, model side): written by Qwen 3.8 Flash Next Coder
// IQ1_M.

import com.example.campbnb.notifications.NotificationRoutes
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.campbnb.controller.NotificationController

/**
 * One entry of the notification log: the records written together for one broadcast,
 * joined into a single entry. Not an Identifiable: this is a view over the saved records,
 * nothing here is written to the database.
 *
 * @property logId The stable name of this log entry, used to open its dropdown.
 * @property senderId Who caused the notification, empty when the system created it.
 * @property recipientIds The distinct receivers of the notification, in first-seen order.
 * @property sentAtLabel When the notification was saved, already written out as a date-and-time
 *     label or a note that none was saved. Built once here, so the screen never formats.
 * @property title The short title the notification carried.
 * @property message The message body, kept exactly as it was written.
 * @property routeText Where opening that notification goes, from [NotificationRoutes].
 */
data class NotificationLogGroup(
    val logId: String,
    val senderId: String,
    val recipientIds: List<String>,
    val sentAtLabel: String,
    val title: String,
    val message: String,
    val routeText: String,
)

/**
 * Which of the four looks the debug screen gives a log line.
 *
 * The model decides which lines exist and what they say; only the screen decides how a
 * line looks.
 */
enum class LogLineStyle {
    DROPDOWN,
    RECIPIENT,
    NOTICE_TITLE,
    NOTICE_BODY
}

/**
 * One line the debug screen draws: the dropdown bar of a log entry, one of its receivers,
 * or one half of the notice it carried.
 *
 * The lines are built once, when the log is read or a dropdown is opened, and the screen
 * only draws what it is handed, so nothing here is built again while scrolling.
 *
 * @property lineKey The LazyColumn key: one stable key per line, never shared by two lines,
 *     so opening or closing one dropdown leaves every other line cached.
 * @property logId The entry this line belongs to; the dropdown bar names it so a tap can
 *     open the right one.
 * @property text The whole line, wording included.
 * @property style Which look the screen gives this line, from [LogLineStyle].
 */
data class NotificationLogLine(
    val lineKey: String,
    val logId: String,
    val text: String,
    val style: LogLineStyle,
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
    "${notification.senderId}_${notification.type.name}_" +
        "${notification.title}_${notification.message}_" +
        "${notification.lotteryId}_${notification.reservationId}_${notification.campsiteName}"

/**
 * The saved timestamp of a joined broadcast. Records of one broadcast share one server
 * timestamp; a record that has not been served yet carries none, so the first saved one
 * wins.
 *
 * @param members The records of one broadcast.
 * @return The saved timestamp, or nothing when none was saved.
 */
private fun sentAtOf(members: List<Notification>): Timestamp? =
    members.firstNotNullOfOrNull { it.sentAt }

/**
 * The date-and-time label one saved timestamp takes in the log.
 *
 * Built once per entry while the log is read, never while the screen draws.
 *
 * @param sentAt The saved timestamp, if any.
 * @return The label, or a note when none was saved.
 */
private fun describeSavedAt(sentAt: Timestamp?): String {
    if (sentAt == null) {
        return "no saved timestamp"
    }
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(sentAt.seconds * 1000L))
}

/**
 * The collapsed line a log dropdown shows: who sent the notification, how many receivers
 * got it, when it was saved, and where opening it goes.
 *
 * @param group The log entry to describe.
 * @return The one-line summary the dropdown bar shows.
 */
private fun summaryLineOf(group: NotificationLogGroup): String {
    val sender = group.senderId.ifEmpty { "the system" }
    return "sent by $sender  -  ${group.recipientIds.count()} recipient(s)  -  " +
        "${group.sentAtLabel}  -  opens ${group.routeText}"
}

/**
 * The log entries, from the saved records.
 *
 * Records of one broadcast join into one entry, the receivers are collected in order
 * without repeats, and the entries take the order their input gives them - the caller
 * sorts the records newest first, which is what [NotificationController] does.
 * The label and the timestamp label are written here, once, so the screen has no
 * formatting left to do per line.
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
                describeSavedAt(sentAtOf(members)),
                members.first().title,
                members.first().message,
                NotificationRoutes.routeFor(members.first())
            )
        }

/**
 * The lines to draw, from the entries.
 *
 * A closed entry contributes only its dropdown bar. An open one contributes its bar, then
 * one line per receiver - resolved through [profileLabel] here, once per receiver, never
 * per frame - then the notice it carried, title first and body second. Entries keep the
 * order they were given, so the newest broadcast is still first.
 *
 * A name that matches no entry changes nothing, and an entry with no receivers adds no
 * receiver lines.
 *
 * @param logs The log entries, in the order they are shown.
 * @param openLogIds The entries whose dropdown is open.
 * @param profileLabel How one profile reference is drawn; the caller resolves references.
 * @return The lines to draw, one per entry plus the detail of each open entry.
 */
fun notificationLogLinesFor(
    logs: List<NotificationLogGroup>,
    openLogIds: Set<String>,
    profileLabel: (String) -> String,
): List<NotificationLogLine> =
    logs.flatMap { group -> logLinesOf(group, group.logId in openLogIds, profileLabel) }

/**
 * The lines of one entry: its bar, and when that bar is open, its receivers and its notice.
 */
private fun logLinesOf(
    group: NotificationLogGroup,
    expanded: Boolean,
    profileLabel: (String) -> String,
): List<NotificationLogLine> {
    val bar = NotificationLogLine(
        group.logId,
        group.logId,
        summaryLineOf(group) + if (expanded) " - open" else " - tap to open",
        LogLineStyle.DROPDOWN
    )
    if (!expanded) {
        return listOf(bar)
    }
    return listOf(bar) + group.recipientIds.map { receiver ->
        NotificationLogLine(
            "${group.logId}#receiver#$receiver",
            group.logId,
            profileLabel(receiver),
            LogLineStyle.RECIPIENT
        )
    } + listOf(
        NotificationLogLine("${group.logId}#notice#title", group.logId, group.title, LogLineStyle.NOTICE_TITLE),
        NotificationLogLine("${group.logId}#notice#body", group.logId, group.message, LogLineStyle.NOTICE_BODY)
    )
}
