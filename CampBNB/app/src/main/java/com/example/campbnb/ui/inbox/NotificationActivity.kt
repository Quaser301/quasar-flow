package com.example.campbnb.ui.inbox

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationStatus
import com.example.campbnb.notifications.NotificationPermissionCard

@Composable
fun NotificationInboxScreen(
    viewModel: NotificationViewModel,
    navToNotification: (Notification) -> Unit,
    // Asked with the database key of the notice a row shows, when this viewer may remove
    // their own notices, and with what to run once the store confirms it. This listing is
    // followed live, so the confirmation needs nothing here. Passing nothing leaves the
    // rows without a delete control.
    deleteNotice: ((String, (removed: Boolean) -> Unit) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        NotificationPermissionCard(
            modifier = Modifier.padding(16.dp)
        )

        when (val current = state) {
            InboxState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is InboxState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(current.message)
                    Button(modifier = Modifier.testTag("try_again"), onClick = viewModel::start) {
                        Text("Try again")
                    }
                }
            }

            is InboxState.Ready -> {
                if (current.items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No notifications yet.")
                    }
                } else {
                    TextButton(
                        onClick = viewModel::markAllRead,
                        modifier = Modifier.testTag("mark_all_read").padding(horizontal = 8.dp)
                    ) {
                        Text("Mark all as read")
                    }

                    HorizontalDivider()

                    LazyColumn {
                        items(
                            items = current.items,
                            key = { it.id }
                        ) { notification ->
                            NotificationRow(
                                notification = notification,
                                deleteNotice = deleteNotice
                            ) {
                                viewModel.open(notification)
                                navToNotification(notification)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: Notification,
    deleteNotice: ((String, (removed: Boolean) -> Unit) -> Unit)? = null,
    onClick: () -> Unit
) {
    val unread = notification.status == NotificationStatus.UNREAD

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_row")
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(9.dp)
                .background(
                    if (unread) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape
                )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = notification.sentAt?.toDate()?.time?.let { timestamp ->
                    DateUtils.getRelativeTimeSpanString(
                        timestamp,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                } ?: "Just now",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // The notice itself is what a viewer removes, so the control sits on the row
            // rather than behind a menu. This listing is followed live, so the store
            // answering that the notice is gone needs nothing here.
            val remove = deleteNotice
            if (remove != null) {
                TextButton(
                    onClick = {
                        remove(notification.id) { }
                    },
                    modifier = Modifier
                        .testTag("notification_delete")
                        .padding(start = 12.dp)
                ) {
                    Text("Delete", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
