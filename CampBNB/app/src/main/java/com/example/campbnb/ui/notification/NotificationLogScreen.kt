package com.example.campbnb.ui.notification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campbnb.models.Notification
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.viewModels.NotificationLogDisplayController
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * One entry of the notification log: the records written together for one
 * notification broadcast, joined into a single line. Not an Identifiable: this
 * is a view over the saved records, nothing here is written to the database.
 *
 * @property logId The stable name of this log entry, used to open its dropdown.
 * @property senderId Who caused the notification, empty when the system created it.
 * @property recipientIds The distinct receivers of the notification.
 * @property sentAt When the notification was saved, taken from its records.
 * @property title The short title the notification carried.
 * @property message The message body, kept exactly as it was written.
 */
class NotificationLogGroup(
    val logId: String,
    val senderId: String,
    val recipientIds: List<String>,
    val sentAt: Timestamp?,
    val title: String,
    val message: String
) {}

/**
 * The stable name of one log entry: a join of the record fields that describe
 * the broadcast itself, leaving out the per-receiver fields.
 *
 * PLACEHOLDER NOTE: records that merely happen to share all these fields would
 * merge into one entry too; the app does not yet write a shared event id into
 * the notification records, so this is as close as the data gets.
 *
 * @param notification One saved notification record.
 * @return The join its log entry is grouped under.
 */
fun logKeyOf(notification: Notification): String =
    "${notification.senderId}\u0001${notification.type.name}\u0001${notification.title}\u0001${notification.message}\u0001${notification.lotteryId}\u0001${notification.reservationId}\u0001${notification.campsiteName}"

/**
 * Which log entries the screen shows, from the saved records.
 *
 * Records of one broadcast join into one entry, the receivers are collected in
 * order without repeats, and the entries are ordered newest first, matching how
 * the inbox sorts its own list.
 *
 * @param notifications The saved notification records.
 * @return The log entries, newest first.
 */
fun notificationLogGroupsFor(notifications: List<Notification>): List<NotificationLogGroup> =
    notifications.sortedByDescending { notification -> notification.sentAt?.seconds ?: Long.MAX_VALUE }
        .groupBy { notification -> logKeyOf(notification) }.values
        .map { members -> NotificationLogGroup(
            logKeyOf(members[0]),
            members[0].senderId,
            members.map { member -> member.recipientId }.distinct(),
            latestSentAtOf(members),
            members[0].title,
            members[0].message
        ) }

/**
 * The saved timestamp of a joined broadcast: the latest one its records carry,
 * since each record holds its own server timestamp.
 *
 * @param members The records of one broadcast.
 * @return The latest saved timestamp, or nothing when none was saved.
 */
fun latestSentAtOf(members: List<Notification>): Timestamp? {
    var latest: Notification? = null
    for (member in members) {
        if (latest == null ||
                (member.sentAt?.seconds ?: 0L) > (latest.sentAt?.seconds ?: 0L)) {
            latest = member
        }
    }
    return latest?.sentAt
}

/**
 * The collapsed line of a log dropdown, in order: who sent the notification,
 * how many receivers got it, and its timestamp.
 *
 * @param group The log entry to describe.
 * @return The one-line summary the dropdown bar shows.
 */
fun summaryLine(group: NotificationLogGroup): String {
    val sender = if (group.senderId.isEmpty()) "the system" else group.senderId
    return "sent by $sender  -  ${group.recipientIds.count()} recipient(s)  -  ${describeTimestamp(group.sentAt)}"
}

/**
 * Turns a saved timestamp into a date-and-time label for the log.
 *
 * @param sentAt The saved timestamp, if any.
 * @return The label, or a note when no timestamp was saved.
 */
fun describeTimestamp(sentAt: Timestamp?): String {
    if (sentAt == null) return "no saved timestamp"
    val calendar = Calendar.getInstance().apply { time = Date(sentAt.seconds * 1000L) }
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(calendar.time)
}

/**
 * The receiver at a position of a log entry&apos;s receiver list, so the second
 * dropdown can draw just the rows it scrolls into.
 *
 * @param group The log entry.
 * @param index The row position.
 * @return That receiver&apos;s profile reference, or an empty when past the end.
 */
fun notificationLogProfileAt(group: NotificationLogGroup, index: Int): String =
    if (0 <= index && index < group.recipientIds.count()) group.recipientIds[index] else ""

/** Whether the named log dropdown is open in the screen&apos;s collected open-names value. */
private fun openIn(logName: String, openedNames: String): Boolean =
    ("\n" + logName + "\n") in openedNames

/**
 * The Notification Debugging screen body: a back button in the top left, a
 * Notification Debugging header in the top center, and every log entry as a
 * dropdown. With no logs at all, a centered message says so instead.
 *
 * Pressing back calls the passed-in lambda, so the user returns to the screen
 * just on. Registration is done separately: this composable is only the body,
 * and it is not wired into navigation yet.
 *
 * Each dropdown opens its second dropdown: a scrollable list of the profiles
 * that received the notification, with the notification content kept exactly as
 * written below it, including any placeholder text inside it.
 *
 * @param back Called when the back button is pressed.
 * @param notifications The saved notification records, supplied by the caller
 * through the existing notification repository.
 * @param profileText How a profile reference is drawn; PLACEHOLDER: profiles
 * carry no display fields yet, so the default shows the reference as it is.
 */
@Composable
fun NotificationLogScreen(
    back: () -> Unit,
    notifications: List<Notification>,
    profileText: (String) -> String = { profileReference -> profileReference },
) {
    val display = remember { NotificationLogDisplayController(back) }
    val openedNames = display.openedLogNames().collectAsStateWithLifecycle()
    val logs = notificationLogGroupsFor(notifications)

    if (logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No logs to show!", fontSize = 22.sp, color = FieldGrey)
        }
    } else {
        Column {
            NotificationLogTopBar(display)
            logs.forEach { group ->
                NotificationLogDropdown(display, group, openedNames.value) {
                    NotificationLogDetail(group, profileText)
                }
            }
        }
    }
}

/** The back button in the top left and the Notification Debugging header in the top center. */
@Composable
private fun NotificationLogTopBar(display: NotificationLogDisplayController) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .clickable(onClick = { display.pressBack() })
                .padding(6.dp)
        ) {
            Text("< back", fontSize = 16.sp, color = FieldGrey)
        }
        Box(
            Modifier.weight(1.0f),
            contentAlignment = Alignment.Center
        ) {
            Text("Notification Debugging", fontSize = 26.sp)
        }
    }
}

/**
 * One log dropdown: a bar summarizing who sent the notification, how many
 * received it, and its timestamp, which opens or closes on a tap and shows its
 * detail while open.
 *
 * @param display The holder of the dropdown expansion state.
 * @param group The log entry this dropdown stands for.
 * @param openedNames The screen&apos;s collected open log names.
 * @param content The entry&apos;s detail, rendered only while the dropdown is open.
 */
@Composable
private fun NotificationLogDropdown(
    display: NotificationLogDisplayController,
    group: NotificationLogGroup,
    openedNames: String,
    content: @Composable () -> Unit,
) {
    val expanded = openIn(group.logId, openedNames)

    Column {
        Box(
            Modifier
                .clickable(onClick = { display.toggleGroup(group.logId) })
                .padding(6.dp)
        ) {
            Text(
                summaryLine(group) + if (expanded) " - open" else " - tap to open",
                fontSize = 18.sp
            )
        }
        if (expanded) content()
    }
}

/**
 * The detail of an open log dropdown: the second dropdown, a scrollable list
 * that draws only the profile rows it scrolls into, and below it the
 * notification content exactly as it was written, placeholders left as is.
 *
 * @param group The open log entry.
 * @param profileText How a profile reference is drawn.
 */
@Composable
private fun NotificationLogDetail(
    group: NotificationLogGroup,
    profileText: (String) -> String,
) {
    Column {
        // no virtualized list exists in this toolchain: rows are drawn one per model index
        // under a scroll, so the data stays indexed-lazy and the viewport scrolls.
        Box(Modifier.verticalScroll(rememberScrollState())) {
            Column {
                for (index in 0..group.recipientIds.count() - 1) {
                    Text(profileText(notificationLogProfileAt(group, index)), fontSize = 16.sp)
                }
            }
        }
        Text(group.title, fontSize = 18.sp)
        Text(group.message, fontSize = 16.sp)
    }
}
