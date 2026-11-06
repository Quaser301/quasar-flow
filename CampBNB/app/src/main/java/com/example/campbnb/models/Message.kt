package com.example.campbnb.models

/*
 * Message.kt
 *
 * Role: model class (data only, no logic) for one chat message between the owner of a
 * campsite and the entrant who reserved it. Stored in the Firestore "messages" collection.
 * Used by: MessageRepository, MessageController, MessageActivity, ChatActivity.
 *
 * Outstanding issues: none.
 */

import com.example.campbnb.database.Identifiable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

/**
 * One message sent between the owner and the entrant of a reservation (US 02.12.01).
 *
 * Every parameter has a default value. Firestore needs that: it creates an empty Message
 * first and then fills the fields in when it reads a document.
 *
 * @property reservationId Reservation this conversation belongs to. One conversation per reservation.
 * @property campsiteId Campsite the reservation is for.
 * @property ownerId User ID of the campsite owner.
 * @property entrantId User ID of the entrant who made the reservation.
 * @property participantIds The two user IDs allowed to read the conversation (owner and entrant).
 * @property senderId User ID of the person who wrote this message.
 * @property recipientId User ID of the person who receives this message.
 * @property content The text the sender wrote (already trimmed by the controller).
 * @property sentAt Time the server saved the message. null until the server fills it in.
 * @property status UNREAD until the recipient opens the conversation.
 */
class Message (
    var reservationId: String = "",
    var campsiteId: String = "",
    var ownerId: String = "",
    var entrantId: String = "",
    var participantIds: List<String> = emptyList(),
    var senderId: String = "",
    var recipientId: String = "",
    var content: String = "",
    // @ServerTimestamp: if this is null when saved, Firestore writes ITS clock here.
    // Every phone then agrees on message order, even if one phone's clock is wrong.
    @ServerTimestamp var sentAt: Timestamp? = null,
    var status: MessageStatus = MessageStatus.UNREAD
    ): Identifiable()   // Identifiable gives every message a unique id (the Firestore document ID)

    /**
     * Read state of a message, from the recipient's point of view.
     *
     * UNREAD: the recipient has not opened the conversation since the message arrived.
     * READ: the recipient has seen it.
     */
    enum class MessageStatus {
        UNREAD,
        READ
    }