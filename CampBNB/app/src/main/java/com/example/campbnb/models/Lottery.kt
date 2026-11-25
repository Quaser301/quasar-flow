package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

/**
 * Display state of a lottery, derived from its dates by [Lottery.statusAt].
 * 03-11-26: Enum and formatting and statusAt for determining the status
 * suggested by Claude Sonnet 5.5 (prompted for an effective way to store a
 * lottery's status so it can be displayed).
 * 04-11-26: Used built in gemini flash 3.6 to add a few personal comments and kdoc comments
 * for functional clarity.
 * 06-11-26: Prompted gemini 3.6 to fill out kdocs
 */
enum class LotteryStatus { UPCOMING, OPEN, DRAWN, BOOKING, CLOSED }

/**
 * Entity: a lottery over one or more campsites (US 02.04.01-.08). Times are epoch millis.
 * Status is derived from the dates. Other owners may share a lottery (US 02.04.08): they
 * are invited when the lottery is created or updated, and once they accept they can see it
 * and edit only their own campsites, never its parameters.
 *
 * @property ownerId Unique identifier of the campsite owner who created this lottery.
 * @property campsiteIds List of campsite IDs associated with this lottery.
 * @property registrationStart Timestamp (epoch millis) when entrant registration opens.
 * @property registrationEnd Timestamp (epoch millis) when entrant registration closes.
 * @property bookingStart Timestamp (epoch millis) when winner booking window opens.
 * @property bookingEnd Timestamp (epoch millis) when winner booking window closes.
 * @property numberOfWinners Total number of winning entrants to select during draw.
 * @property waitlistCapacity Maximum capacity for waitlist entrants; null indicates unlimited capacity.
 * @property drawn True if winners have been selected and drawn for this lottery.
 * @property winnerIds List of entrant user IDs who were selected as winners.
 * @property invitedOwnerIds List of other owners asked to join this lottery, invitations not yet accepted.
 * @property joinedOwnerIds List of other owners who accepted an invitation and share this lottery.
 */
data class Lottery(
    val ownerId: String = "",
    val campsiteIds: List<String> = emptyList(),
    val registrationStart: Long = 0,
    val registrationEnd: Long = 0,
    val bookingStart: Long = 0,
    val bookingEnd: Long = 0,
    val numberOfWinners: Int = 0,
    val waitlistCapacity: Int? = null,      // null = unlimited (US 02.04.07)
    val drawn: Boolean = false,
    val winnerIds: List<String> = emptyList(),
    // US 02.04.08: invitedOwnerIds names the owners selected when the lottery was created
    // or updated and not yet accepted; joinedOwnerIds names the ones who accepted and share
    // the lottery. The creating owner is in neither list: they were never invited.
    val invitedOwnerIds: List<String> = emptyList(),
    val joinedOwnerIds: List<String> = emptyList()
): Identifiable() {

    /**
     * Display status at time [now] (epoch millis). A function so Firestore doesn't store it.
     *
     * @param now Current timestamp in epoch milliseconds.
     * @return Calculated [LotteryStatus] state based on registration and booking date boundaries.
     */
    fun statusAt(now: Long): LotteryStatus = when {
        now < registrationStart -> LotteryStatus.UPCOMING
        now < registrationEnd   -> LotteryStatus.OPEN
        now < bookingStart      -> LotteryStatus.DRAWN
        now <= bookingEnd       -> LotteryStatus.BOOKING
        else                    -> LotteryStatus.CLOSED
    }
    /**
     * Checks whether this lottery is allowed to be saved.
     *
     * @param now The current time in epoch millis, used to check that dates aren't in the past.
     * @return True if every rule passes, false otherwise.
     */
    fun isValid(now: Long): Boolean {
        if (campsiteIds.isEmpty()) {
            return false
        }
        // There must be at least one winner
        if (numberOfWinners < 1) {
            return false
        }
        // Waitlist capacity is optional (null = unlimited), but if set it must fit every winner
        if (waitlistCapacity != null && waitlistCapacity < numberOfWinners) {
            return false
        }
        // Both date ranges must be chosen (0 means "not set")
        if (registrationStart == 0L || registrationEnd == 0L) {
            return false
        }
        if (bookingStart == 0L || bookingEnd == 0L) {
            return false
        }
        // Dates must make sense in time
        if (registrationEnd < now) {
            return false
        }
        if (bookingStart <= registrationEnd) {
            return false
        }
        if (bookingEnd < bookingStart) {
            return false
        }
        // US 02.04.08: invitations must name owners cleanly. A blank is no one; an owner
        // cannot invite themselves to their own lottery; one person cannot be both invited
        // and already joining; the same owner cannot be named twice.
        if (invitedOwnerIds.any { it.isBlank() } || joinedOwnerIds.any { it.isBlank() }) {
            return false
        }
        if (ownerId in invitedOwnerIds || ownerId in joinedOwnerIds) {
            return false
        }
        if (invitedOwnerIds.any { it in joinedOwnerIds }) {
            return false
        }
        if (invitedOwnerIds.distinct().size != invitedOwnerIds.size ||
            joinedOwnerIds.distinct().size != joinedOwnerIds.size
        ) {
            return false
        }
        return true
    }

    /**
     * Whether [userId] may see this lottery (US 02.04.08): the owner who created it and the
     * owners who accepted an invitation may; owners invited but not yet accepted, and
     * outsiders, may not.
     *
     * @param userId The user looking at lotteries.
     * @return True when this lottery belongs in that user's list.
     */
    fun visibleTo(userId: String): Boolean = userId == ownerId || userId in joinedOwnerIds

    /**
     * This lottery as it is once [userId] accepts its invitation (US 02.04.08): they move
     * from invited to joining and every other field, the dates and winner settings down to
     * the id, stays as it is, which is what keeps a joining owner from changing the
     * lottery's parameters. Returns this unchanged when [userId] was not invited.
     *
     * @param userId The invited owner accepting.
     * @return Updated copy of this lottery, or this if [userId] was not invited.
     */
    fun withInvitationAcceptedBy(userId: String): Lottery {
        if (userId !in invitedOwnerIds) {
            return this
        }
        return Lottery(
            ownerId = ownerId,
            campsiteIds = campsiteIds,
            registrationStart = registrationStart,
            registrationEnd = registrationEnd,
            bookingStart = bookingStart,
            bookingEnd = bookingEnd,
            numberOfWinners = numberOfWinners,
            waitlistCapacity = waitlistCapacity,
            drawn = drawn,
            winnerIds = winnerIds,
            invitedOwnerIds = invitedOwnerIds - userId,
            joinedOwnerIds = joinedOwnerIds + userId
        ).also { it.id = this.id }
    }

    /**
     * This lottery covering only [chosen] campsite ids, with every other field including the
     * id unchanged. This is a joining owner's edit (US 02.04.08): they add or remove their
     * own campsites, and the parameters come from the lottery as it already is.
     *
     * @param chosen The campsite ids the edited lottery covers.
     * @return Updated copy of this lottery.
     */
    fun withCampsiteIds(chosen: List<String>): Lottery = Lottery(
        ownerId = ownerId,
        campsiteIds = chosen,
        registrationStart = registrationStart,
        registrationEnd = registrationEnd,
        bookingStart = bookingStart,
        bookingEnd = bookingEnd,
        numberOfWinners = numberOfWinners,
        waitlistCapacity = waitlistCapacity,
        drawn = drawn,
        winnerIds = winnerIds,
        invitedOwnerIds = invitedOwnerIds,
        joinedOwnerIds = joinedOwnerIds
    ).also { it.id = this.id }

}