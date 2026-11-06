package com.example.campbnb.controller

import com.example.campbnb.models.Message
import com.example.campbnb.models.MessageStatus

/**
 * One chat as shown in the Messages list. There is one conversation per reservation.
 *
 * @property reservationId The reservation the chat belongs to (also the conversation ID).
 * @property otherUserId The user on the other side of the chat. Blank if not known yet.
 * @property campsiteId The campsite the reservation is for.
 * @property ownerId The campsite owner. Blank if not known yet.
 * @property entrantId The entrant. Blank if not known yet.
 * @property lastText Text of the newest message.
 * @property lastSentAtSeconds When the newest message was sent, or null if the server has not stamped it yet.
 * @property unreadCount Messages in this chat the current user has not read.
 */
data class Conversation(
    val reservationId: String,
    val otherUserId: String = "",
    val campsiteId: String = "",
    val ownerId: String = "",
    val entrantId: String = "",
    val lastText: String = "",
    val lastSentAtSeconds: Long? = null,
    val unreadCount: Int = 0
) {
    /** True if the app already knows both people in the chat, so a reply needs no reservation lookup. */
    val hasBothParticipants: Boolean
        get() = ownerId.isNotBlank() && entrantId.isNotBlank()
}

/** Helpers that turn a flat list of messages into chats and threads. */
object Conversations {

    private fun sortKey(m: Message): Long =
        m.sentAt?.let { it.seconds * 1000L + it.nanoseconds / 1_000_000 } ?: Long.MAX_VALUE

    /** Groups [all] into one [Conversation] per reservation for [userId], newest chat first. */
    fun group(userId: String, all: List<Message>): List<Conversation> =
        all.filter { userId in it.participantIds }
            .groupBy { it.reservationId }
            .map { (reservationId, msgs) ->
                val last = msgs.maxByOrNull { sortKey(it) }!!
                Conversation(
                    reservationId = reservationId,
                    otherUserId = if (last.senderId == userId) last.recipientId else last.senderId,
                    campsiteId = last.campsiteId,
                    ownerId = last.ownerId,
                    entrantId = last.entrantId,
                    lastText = last.content,
                    lastSentAtSeconds = last.sentAt?.seconds,
                    unreadCount = msgs.count {
                        it.recipientId == userId && it.status == MessageStatus.UNREAD
                    }
                ) to sortKey(last)
            }
            .sortedByDescending { it.second }
            .map { it.first }

    /** Every message in one chat that [userId] is part of, oldest first. */
    fun thread(userId: String, reservationId: String, all: List<Message>): List<Message> =
        all.filter { it.reservationId == reservationId && userId in it.participantIds }
            .sortedBy { sortKey(it) }
}
