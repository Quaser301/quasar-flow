package com.example.campbnb.ui.inbox

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.campbnb.controller.Conversation
import com.example.campbnb.models.Message
import com.example.campbnb.viewModels.MessageViewModel
import com.example.campbnb.viewModels.MessagesState

/**
 * The "Messages" tab of the Correspondence screen.
 *
 * Shows the list of chats. Tapping a chat opens its thread, where the user can read and reply.
 *
 * @param currentUserId the signed-in user, used to decide which messages are "mine"
 */
@Composable
fun MessagesScreen(
    viewModel: MessageViewModel,
    currentUserId: String
) {
    val state by viewModel.state.collectAsState()

    when (val current = state) {
        MessagesState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is MessagesState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(current.message)
                Button(onClick = viewModel::start) {
                    Text("Try again")
                }
            }
        }

        is MessagesState.Ready -> {
            val open = current.openConversation
            if (open == null) {
                ConversationList(
                    conversations = current.conversations,
                    onOpen = { viewModel.open(it.reservationId) }
                )
            } else {
                ChatThread(
                    currentUserId = currentUserId,
                    conversation = open,
                    thread = current.thread,
                    onBack = viewModel::close,
                    onSend = { text, onResult -> viewModel.send(text, onResult) }
                )
            }
        }
    }
}

/** The chat list: one row per chat, a placeholder when there are none.
 * @param onOpen called with the chat of a tapped row */
@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    onOpen: (Conversation) -> Unit
) {
    if (conversations.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No messages yet.")
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = conversations, key = { it.reservationId }) { conversation ->
            ConversationRow(conversation) { onOpen(conversation) }
            HorizontalDivider()
        }
    }
}

/** One chat: last text and elapsed time, bold when unread, with a count badge.
 * @param onClick called when the row is tapped */
@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit
) {
    val unread = conversation.unreadCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("conversation_row")
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "Reservation chat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = conversation.lastText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = relativeTime(conversation.lastSentAtSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (unread) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .testTag("unread_badge")
            ) {
                Text(
                    text = conversation.unreadCount.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/** An open chat as bubbles, newest kept in view; back and send route through
 *     onBack and onSend. */
@Composable
private fun ChatThread(
    currentUserId: String,
    conversation: Conversation,
    thread: List<Message>,
    onBack: () -> Unit,
    onSend: (String, (Result<Message>) -> Unit) -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    BackHandler(onBack = onBack)

    // Keep the newest message in view.
    LaunchedEffect(thread.size) {
        if (thread.isNotEmpty()) listState.animateScrollToItem(thread.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("chat_back")) {
                Text("Back")
            }
            Text(
                text = "Reservation chat",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = thread, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    mine = message.senderId == currentUserId
                )
            }
        }

        HorizontalDivider()

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("send_error")
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    error = null
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("message_input"),
                placeholder = { Text("Type a message") },
                maxLines = 4
            )
            Button(
                onClick = {
                    onSend(draft) { result ->
                        result.fold(
                            onSuccess = { draft = "" },
                            onFailure = { error = it.message ?: "Could not send message" }
                        )
                    }
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier.testTag("send_button")
            ) {
                Text("Send")
            }
        }
    }
}

/** A single message: right-aligned if it belongs to the viewer, left otherwise,
 *     with its relative time.
 * @param mine whether this message belongs to the viewer */
@Composable
private fun MessageBubble(
    message: Message,
    mine: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (mine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .testTag("message_bubble")
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = relativeTime(message.sentAt?.seconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Human elapsed-time label for a timestamp; undated reads as just now. */
private fun relativeTime(seconds: Long?): String =
    seconds?.let {
        DateUtils.getRelativeTimeSpanString(
            it * 1000L,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    } ?: "Just now"
