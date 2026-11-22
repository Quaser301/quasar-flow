package com.example.campbnb.models

import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.Message
import com.example.campbnb.models.PolicyReason
import com.example.campbnb.models.ReservationModel

/**
 * What a caller wants reported to whoever is concerned, as data rather than as a method call.
 *
 * An intent names an event and the data that event is about, and never names who receives it.
 * Who hears about an event is a property of the event itself, and the one thing that decides
 * it is [NotificationController], which turns an intent into the [Notification] records to
 * store. Several parts of the app report events, so naming a recipient here would let the
 * parties of one reservation drift apart between call sites, and an event could be reported
 * to one party only because a caller wrote it that way.
 *
 * Every event also names its cause, which the feature records as the record's senderId: the
 * person whose own action produced the event, so the notice appears in that person's sent
 * list. An event with no single cause, a lottery draw, stays attributed to nobody.
 *
 * @memberproperty ReservationConfirmed A reservation was completed by its entrant.
 * @memberproperty ReservationCancelled The entrant cancelled their own reservation.
 * @memberproperty ReservationRejected The owner rejected a reservation.
 * @memberproperty MessageArrived A message arrived for its recipient.
 * @memberproperty LotteryDrawn A lottery was drawn, and its entrants heard the result.
 * @memberproperty LotteryInvited Other owners were asked to join a lottery.
 * @memberproperty CampsitePolicyRemoved An app policy action removed a whole listing.
 * @memberproperty CampsiteTagRemoved An app policy action removed one tag from a listing.
 */
sealed interface NotificationIntent {
    /**
     * A reservation was completed. Both parties hear about it: the owner so they can manage
     * the booking, and the entrant, whose own reservation this is and who needs the dates
     * they just chose confirmed back to them (US 01.05.01). The entrant completed it, so
     * both records are attributed to them.
     */
    data class ReservationConfirmed(val reservation: ReservationModel) : NotificationIntent

    /**
     * The entrant cancelled their own reservation. Only the owner hears, because the
     * cancellation is the entrant's own action and the entrant already knows it.
     */
    data class ReservationCancelled(val reservation: ReservationModel) : NotificationIntent

    /**
     * The owner rejected a reservation. Only the entrant hears, since the owner asked for
     * nothing, and the owner is the cause of the rejection.
     */
    data class ReservationRejected(val reservation: ReservationModel) : NotificationIntent

    /**
     * A message arrived for its recipient; the person who wrote it is the cause.
     *
     * @property campsiteName Campsite the conversation belongs to, named by the caller:
     * [Message] carries only the campsite's id, and a notice reads a name when it has one.
     */
    data class MessageArrived(
        val message: Message,
        val campsiteName: String
    ) : NotificationIntent

    /**
     * A lottery was drawn: its winners hear they won, and the entrants who were not selected
     * hear they lost, from one intent. A draw has no single cause, so its records stay
     * attributed to nobody. An entrant listed in both lists counts as a winner only, so
     * nobody is ever told they both won and lost.
     *
     * @property lotteryId The lottery drawn.
     * @property campsiteName Campsite the draw was for, named in the messages.
     * @property winnerIds Entrants the draw selected.
     * @property loserIds Entrants who entered and were not selected.
     */
    data class LotteryDrawn(
        val lotteryId: String,
        val campsiteName: String,
        val winnerIds: List<String>,
        val loserIds: List<String>
    ) : NotificationIntent

    /**
     * Other owners were asked to join [lottery] when it was created or updated, and the
     * invited owners hear the invitation. The owner who selected them caused it, so the
     * records are attributed to them, and they themselves never hear their own invitation.
     * Owners who have already accepted are not part of this event: the model keeps the
     * invited and joining lists apart, so a joining owner is never invited again.
     *
     * @property lottery The lottery the invitation was issued for, saved with its invited
     * owners listed.
     */
    data class LotteryInvited(val lottery: Lottery) : NotificationIntent

    /**
     * An admin applied the app's policy to a listing: it was removed whole. Only the
     * listing's own creator hears, as the party affected; the admin is the cause and is
     * never told about their own action, so the intent names the actor as data.
     */
    data class CampsitePolicyRemoved(
        val campsite: Campsite,
        val reason: PolicyReason,
        val actorUserId: String
    ) : NotificationIntent

    /**
     * An admin removed one tag from a listing. The listing itself stays bookable; only
     * what it advertises changes, and the creator is told the same way.
     */
    data class CampsiteTagRemoved(
        val campsite: Campsite,
        val tag: String,
        val reason: PolicyReason,
        val actorUserId: String
    ) : NotificationIntent
}
