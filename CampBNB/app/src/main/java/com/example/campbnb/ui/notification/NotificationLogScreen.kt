package com.example.campbnb.ui.notification

// AI-assisted (re-pull of US 03.08.01, debug screen): written by Qwen 3.8 Flash Next Coder
// IQ1_M. No disclosure-table row: the user asked to stop logging prompts.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.models.NotificationLogGroup
import com.example.campbnb.ui.components.BackArrow
import com.example.campbnb.ui.components.profileLabelOf
import com.example.campbnb.viewModels.LogViewState
import com.example.campbnb.viewModels.NotificationLogViewModel
import com.example.campbnb.ui.theme.FieldGrey
import com.google.firebase.Timestamp
import java.nio.file.WatchEvent
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The collapsed line of a log dropdown, in order: who sent the notification, how many
 * receivers got it, its timestamp, and where opening it goes.
 *
 * @param group The log entry to describe.
 * @return The one-line summary the dropdown bar shows.
 */
fun summaryLine(group: NotificationLogGroup): String {
    val sender = group.senderId.ifEmpty { "the system" }
    return "sent by $sender  -  ${group.recipientIds.count()} recipient(s)  -  " +
        "${describeTimestamp(group.sentAt)}  -  opens ${group.routeText}"
}

/**
 * Turns a saved timestamp into a date-and-time label for the log.
 *
 * @param sentAt The saved timestamp, if any.
 * @return The label, or a note when no timestamp was saved.
 */
private fun describeTimestamp(sentAt: Timestamp?): String {
    if (sentAt == null) return "no saved timestamp"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(sentAt.seconds * 1000L)
}

/**
 * The Notification Debugging screen body: a back button in the top left, a Notification
 * Debugging header in the top center, and every log entry as a dropdown. What the log shows
 * comes from [NotificationLogViewModel], which reads every saved notification through the
 * controller it was handed; the screen never reaches the data itself.
 *
 * Registration is done separately: this composable is only the body, and it is not wired
 * into navigation yet.
 *
 * Each dropdown opens its second list: a scrollable list of the profiles that received the
 * notification, with the notification content kept exactly as it was written below it,
 * including any placeholder text inside it.
 *
 * @param back Called when the back button is pressed.
 * @param controller Where notifications are read from; the app hands its one instance.
 * @param profileText How a profile reference is drawn. The default only knows the reference,
 * so it labels unknown ones as such: a caller that wants real names hands this its own
 * lookup, for example the app&apos;s saved users fed to [profileLabelOf].
 */
@Composable
fun NotificationLogScreen(
    back: () -> Unit,
    controller: NotificationController,
    profileText: (String) -> String = { userId -> profileLabelOf(userId, emptyList()) },
) {
    val display = viewModel { NotificationLogViewModel(controller, back) }
    val state by display.state.collectAsState()
    val openNames by display.openLogs.collectAsStateWithLifecycle()

    Column(
      modifier = Modifier.padding(top = 32.dp, start = 12.dp, end = 12.dp)
    ) {
        NotificationLogTopBar(display)
        when (val shown = state) {
            is LogViewState.Loading -> {
                Text("reading notifications...", fontSize = 18.sp, color = FieldGrey)
            }

            is LogViewState.Error -> {
                Text(shown.message, fontSize = 18.sp, color = FieldGrey)
            }

            is LogViewState.Ready -> {
                if (shown.logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No logs to show!", fontSize = 22.sp, color = FieldGrey)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        shown.logs.forEach { group ->
                            val expanded = group.logId in openNames
                            item(key = group.logId) {
                                NotificationLogDropdown(display, group, expanded)
                            }

                            if (expanded) {
                                items(items = group.recipientIds, key = {"${group.logId}_$it"}) {
                                    userId ->
                                    Text(
                                        text = profileText(userId),
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)
                                    )
                                }
                                item(key = "${group.logId}_content") {
                                    Column(modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)) {
                                        Text(group.title, fontSize = 18.sp)
                                        Text(group.message, fontSize = 16.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The back button in the top left and the Notification Debugging header in the top center. */
@Composable
private fun NotificationLogTopBar(display: NotificationLogViewModel) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .clickable(onClick = { display.pressBack() } )
                .padding(12.dp)
        ) {

            BackArrow()
        }

        Box(
            Modifier.weight(1.0f),
            contentAlignment = Alignment.Center
        ) {
            Text("Notification Debugging", fontSize = 26.sp)
        }

        Box(
            modifier = Modifier
                .padding(12.dp)
        ) {
            BackArrow(color = Color.Transparent)
        }
    }
}

/**
 * One log dropdown: a bar summarizing who sent the notification, how many received it, its
 * timestamp and where it opens, which opens or closes on a tap and shows its detail while
 * open.
 *
 * @param display The holder of the dropdown expansion state.
 * @param group The log entry this dropdown stands for.
 * @param expanded If this dropdown is expanded.
 */
@Composable
private fun NotificationLogDropdown(
    display: NotificationLogViewModel,
    group: NotificationLogGroup,
    expanded: Boolean,
) {
    Column {
        Box(
            Modifier
                .clickable(onClick = { display.toggleLog(group.logId) })
                .padding(6.dp)
        ) {
            Text(
                summaryLine(group) + if (expanded) " - open" else " - tap to open",
                fontSize = 18.sp
            )
        }
    }
}

