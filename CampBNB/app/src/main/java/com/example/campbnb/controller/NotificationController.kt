package com.example.campbnb.controller

import com.example.campbnb.data.NotificationCommand
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.NotificationUpdate
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.Message
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationIntent
import com.example.campbnb.models.PolicyReason
import com.example.campbnb.models.policyReasonText
import com.example.campbnb.models.NotificationType
import com.example.campbnb.models.ReservationModel

fun interface OptOutChecker {
    fun filterAllowed(userIds: List<String>, onResult: (List<String>) -> Unit)

    companion object {
        val AllowAll = OptOutChecker { ids, done -> done(ids) }
    }
}

/**
 * Turns one event into the records that tell whoever is concerned about it.
 *
 * Every part of the app that reports something hands an event to [notify] as a
 * [NotificationIntent]; nothing outside this class builds a [Notification] record, chooses
 * who receives one, or attributes one to a cause. That is the point of the one entry point:
 * the app has many things that report events, and an event's parties and its cause are
 * decided once here rather than restated at each caller. The record's wording, and which
 * of the event's users may also get a push, belong to this class; where records are stored
 * belongs to [NotificationRepository].
 *
 * Callers do not wait for a result: a record that fails to store never fails the reservation,
 * message or draw that reported the event. Only the callers that count what was stored, such
 * as drawing a lottery, pass [onDone].
 *
 * Commands:
 *  - [notify]: Stores a record for every user an event is about.
 *  - [listen], [readAll], [markRead]: What a view reads, follows, or marks as opened.
 *
 * @author Zino
 * @see NotificationIntent
 */
class NotificationController(
    private val repo: NotificationRepository,
    private val optOutChecker: OptOutChecker = OptOutChecker.AllowAll
) {
    /**
     * Stores one record for every user [intent] is about.
     *
     * A blank or repeated user is not told, and an event whose two parties are the same
     * person is told once. A draw that names no lottery is refused rather than stored.
     *
     * @param intent The event to report.
     * @param onDone on success holds how many records were stored; a caller that only wants
     * the event reported omits it.
     */
    fun notify(
        intent: NotificationIntent,
        onDone: (Result<Int>) -> Unit = {}
    ) {
        if (intent is NotificationIntent.LotteryDrawn && intent.lotteryId.isBlank()) {
            onDone(Result.failure(IllegalArgumentException("lotteryId is required")))
            return
        }

        val recipients = recipientsOf(intent)
        if (recipients.isEmpty()) {
            onDone(Result.success(0))
            return
        }

        optOutChecker.filterAllowed(recipients) { allowed ->
            val allowedSet = allowed.toSet()
            val records = recipients.map { userId ->
                recordFor(intent, userId, userId in allowedSet)
            }
            repo.run(
                NotificationCommand.Save(
                    records = records,
                    onDone = { result -> onDone(result.map { records.size }) }
                )
            )
        }
    }

    /**
     * Starts a live listener for one user's notifications.
     *
     * @return a function that stops the listener. Call when the screen goes away, otherwise
     * the listener keeps running
     */
    fun listen(
        userId: String,
        onUpdate: (NotificationUpdate) -> Unit,
        onError: (Exception) -> Unit
    ): (() -> Unit) {
        var stopFollowing: (() -> Unit)? = null
        repo.run(
            NotificationCommand.Watch(
                userId = userId,
                onUpdate = onUpdate,
                onError = onError,
                onListening = { stop -> stopFollowing = stop }
            )
        )
        return stopFollowing ?: {}
    }

    /**
     * Reads every saved notification once, for the notification debug screen only.
     *
     * Read on demand rather than listened to: the log is a record of what has already been
     * sent, and a whole-collection listener would re-deliver every record on every write.
     *
     * @param onSuccess called once with every saved notification
     * @param onFailure called once when the read fails
     */
    fun readAll(
        onSuccess: (List<Notification>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        repo.run(NotificationCommand.Read(onSuccess = onSuccess, onFailure = onFailure))
    }

    fun markRead(
        notificationId: String,
        onDone: (Result<Unit>) -> Unit = {}
    ) {
        repo.run(
            NotificationCommand.MarkRead(
                notificationId = notificationId,
                onDone = onDone
            )
        )
    }

    /**
     * Who an event has to be told to, taken from the event's own data.
     *
     * A reservation's confirmation reaches both of its parties because the entrant booked the
     * dates and the owner is the other half of the booking (US 01.05.01); a cancellation or
     * rejection reaches only the party that did not cause it. Blanks are dropped and
     * duplicates folded, so one record is ever stored per user per event.
     */
    private fun recipientsOf(intent: NotificationIntent): List<String> = when (intent) {
        is NotificationIntent.ReservationConfirmed ->
            listOf(intent.reservation.ownerId, intent.reservation.entrantId)

        is NotificationIntent.ReservationCancelled -> listOf(intent.reservation.ownerId)
        is NotificationIntent.ReservationRejected -> listOf(intent.reservation.entrantId)
        is NotificationIntent.MessageArrived -> listOf(intent.message.recipientId)

        // An entrant in both lists is a winner only, so no user is told they both won and
        // lost.
        is NotificationIntent.LotteryDrawn ->
            intent.winnerIds + intent.loserIds.filterNot { it in intent.winnerIds }

        // The invited owners are the ones told; the owner who selected them is a cause, and
        // a cause is never told about their own action (US 02.04.08).
        is NotificationIntent.LotteryInvited ->
            intent.lottery.invitedOwnerIds.filterNot { it == intent.lottery.ownerId }

        // A policy action over a listing is heard by the listing's own creator, who is the
        // party affected; the admin who acted is the cause and never hears it (US 02.10.02).
        is NotificationIntent.CampsitePolicyRemoved -> listOf(intent.campsite.ownerId)
        is NotificationIntent.CampsiteTagRemoved -> listOf(intent.campsite.ownerId)
    }.filter { it.isNotBlank() }.distinct()

    private fun recordFor(
        intent: NotificationIntent,
        recipientId: String,
        pushAllowed: Boolean
    ): Notification = when (intent) {
        is NotificationIntent.ReservationConfirmed ->
            reservationNotice(intent.reservation, NotificationType.CONFIRMATION, recipientId, pushAllowed)

        is NotificationIntent.ReservationCancelled ->
            reservationNotice(intent.reservation, NotificationType.CANCELLATION, recipientId, pushAllowed)

        is NotificationIntent.ReservationRejected ->
            reservationNotice(intent.reservation, NotificationType.REJECTION, recipientId, pushAllowed)

        is NotificationIntent.MessageArrived ->
            messageNotice(intent.message, intent.campsiteName, recipientId, pushAllowed)

        is NotificationIntent.LotteryDrawn ->
            lotteryNotice(
                intent.lotteryId,
                intent.campsiteName,
                if (recipientId in intent.winnerIds) NotificationType.WIN else NotificationType.LOSE,
                recipientId,
                pushAllowed
            )

        is NotificationIntent.LotteryInvited ->
            inviteNotice(intent.lottery, recipientId, pushAllowed)

        is NotificationIntent.CampsitePolicyRemoved ->
            policyNotice(intent.campsite, "", intent.reason, intent.actorUserId, recipientId,
                pushAllowed)

        is NotificationIntent.CampsiteTagRemoved ->
            policyNotice(intent.campsite, intent.tag, intent.reason, intent.actorUserId,
                recipientId, pushAllowed)
    }

    /**
     * The record one party of a reservation event gets.
     *
     * The record names the reservation it is about, so opening the notice opens the
     * reservation. The campsite stays unnamed here because a [ReservationModel] carries only
     * its id, so the wording reads "the campsite". The cause is the party who acted: the
     * entrant completed or cancelled the reservation, the owner rejected it.
     */
    private fun reservationNotice(
        reservation: ReservationModel,
        type: NotificationType,
        recipientId: String,
        pushAllowed: Boolean
    ): Notification {
        val (title, body) = when (type) {
            NotificationType.CONFIRMATION ->
                "Reservation confirmed for the campsite" to
                        "Your reservation has been confirmed."

            NotificationType.CANCELLATION ->
                "Reservation cancelled for the campsite" to
                        "The reservation has been cancelled."

            NotificationType.REJECTION ->
                "Reservation rejected for the campsite" to
                        "The reservation request has been rejected."

            else -> throw IllegalArgumentException("$type is not a reservation event")
        }

        val causedBy =
            if (type == NotificationType.REJECTION) reservation.ownerId else reservation.entrantId

        return Notification(
            recipientId = recipientId,
            senderId = causedBy,
            type = type,
            title = title,
            message = body,
            reservationId = reservation.lotteryEntryId,
            pushAllowed = pushAllowed
        )
    }

    /**
     * The record a message's recipient gets when a new message arrives.
     *
     * Only the start of the message is copied, so the notice stays short; the person who
     * wrote it is the cause.
     */
    /**
     * The notice the creator of a listing hears after an app policy action.
     *
     * With no tag named, the whole listing was removed; with one, only that tag was. Either
     * way the notice says why in the app's own words (policyReasonText), and it names the
     * campsite so it can be opened back at the listing. The cause is the admin who acted,
     * so the notice appears in that admin's sent list (US 02.10.02).
     */
    private fun policyNotice(
        campsite: Campsite,
        removedTag: String,
        reason: PolicyReason,
        actorUserId: String,
        recipientId: String,
        pushAllowed: Boolean
    ): Notification {
        val place = campsite.campName.ifBlank { "the campsite" }
        val title =
            if (removedTag.isBlank()) {
                "Your campsite was removed"
            } else {
                "A tag was removed from your campsite"
            }
        val body =
            if (removedTag.isBlank()) {
                "Your campsite $place was removed from the app because ${policyReasonText(reason)}."
            } else {
                "The tag $removedTag was removed from your campsite $place because " +
                    "${policyReasonText(reason)}."
            }

        return Notification(
            recipientId = recipientId,
            senderId = actorUserId,
            type = NotificationType.OWNER_NOTICE,
            title = title,
            message = body,
            campsiteName = place,
            pushAllowed = pushAllowed
        )
    }

    private fun messageNotice(
        message: Message,
        campsiteName: String,
        recipientId: String,
        pushAllowed: Boolean
    ): Notification {
        val place = campsiteName.ifBlank { "the campsite" }
        val preview = message.content.take(100)
        val body =
            if (preview.isBlank()) {
                "You received a new message. Open CampBNB to read it."
            } else {
                "You received a new message: $preview"
            }

        return Notification(
            recipientId = recipientId,
            senderId = message.senderId,
            type = NotificationType.MESSAGE,
            title = "New message about $place",
            message = body,
            reservationId = message.reservationId,
            campsiteName = place,
            pushAllowed = pushAllowed
        )
    }

    /**
     * The record one entrant hears about a draw.
     *
     * The record's id names the draw and the entrant, so drawing the same lottery twice
     * over the same entrant stores one record rather than a second copy. A draw has no
     * single cause, so the record stays unattributed.
     */
    private fun lotteryNotice(
        lotteryId: String,
        campsiteName: String,
        type: NotificationType,
        userId: String,
        pushAllowed: Boolean
    ): Notification {
        val place = campsiteName.ifBlank { "the campsite" }
        val (title, body) = when (type) {
            NotificationType.WIN ->
                "You won the lottery for $place!" to
                        "Congratulations! You were selected in the draw for $place. " +
                        "Action needed: open the app and choose your dates before the booking window closes."

            NotificationType.LOSE ->
                "Lottery result for $place" to
                        "Unfortunately you were not selected in the draw this time." +
                        "Thank you for entering"
            else -> throw IllegalArgumentException("$type is not a lottery result")
        }

        return Notification(
            recipientId = userId,
            type = type,
            title = title,
            message = body,
            lotteryId = lotteryId,
            campsiteName = campsiteName,
            pushAllowed = pushAllowed
        ).also { it.id = "${lotteryId}_${userId}_${type.name}" }
    }

    /**
     * The record one invited owner hears when they are asked to join a lottery.
     *
     * The record names the lottery it is for, so opening the notice opens the lottery. The
     * campsites are counted rather than named because the notice is built from the saved
     * lottery, which carries ids only. The owner who selected the invited owners is the
     * cause. As with a draw result, the record's id names the lottery and the invited
     * owner, so saving the same invitation again stores one record rather than a copy.
     */
    private fun inviteNotice(
        lottery: Lottery,
        recipientId: String,
        pushAllowed: Boolean
    ): Notification {
        val places = when (val count = lottery.campsiteIds.size) {
            1 -> "one campsite"
            0 -> "the campsite"
            else -> "$count campsites"
        }

        return Notification(
            recipientId = recipientId,
            senderId = lottery.ownerId,
            type = NotificationType.INVITE,
            title = "You were invited to a lottery for $places",
            message =
                "You have been invited to join a campsite lottery. Open CampBNB to see the " +
                "lottery and its campsites. If you accept, you can add your own campsites to it.",
            lotteryId = lottery.id,
            pushAllowed = pushAllowed
        ).also { it.id = "${lottery.id}_${recipientId}_${NotificationType.INVITE.name}" }
    }

    companion object {
        fun sortNewestFirst(list: List<Notification>): List<Notification> =
            list.sortedByDescending { it.sentAt?.seconds ?: Long.MAX_VALUE }
    }
}
