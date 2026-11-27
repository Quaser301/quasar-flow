
package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

/**
 * Display state of a lottery, derived from its dates by [Lottery.statusAt].
 */
enum class LotteryStatus {
    UPCOMING,
    OPEN,
    DRAWN,
    BOOKING,
    CLOSED
}

/**
 * A lottery for one campsite.
 *
 * Times are stored as epoch milliseconds.
 * The owner who creates the lottery controls its parameters.
 * Invited owners may join and manage their own campsite participation.
 */
data class Lottery(
    val ownerId: String = "",
    val campsiteIds: List<String> = emptyList(),
    val registrationStart: Long = 0,
    val registrationEnd: Long = 0,
    val bookingStart: Long = 0,
    val bookingEnd: Long = 0,
    val numberOfWinners: Int = 0,
    val waitlistCapacity: Int? = null,
    val drawn: Boolean = false,
    val winnerIds: List<String> = emptyList(),
    val invitedOwnerIds: List<String> = emptyList(),
    val joinedOwnerIds: List<String> = emptyList(),
    val maxBookingDays: Int = 1
) : Identifiable() {

    /**
     * Calculates the display status using the supplied timestamp.
     */
    fun statusAt(now: Long): LotteryStatus = when {
        now < registrationStart -> LotteryStatus.UPCOMING
        now < registrationEnd -> LotteryStatus.OPEN
        now < bookingStart -> LotteryStatus.DRAWN
        now <= bookingEnd -> LotteryStatus.BOOKING
        else -> LotteryStatus.CLOSED
    }

    /**
     * Checks whether this lottery is valid for saving.
     */
    fun isValid(now: Long): Boolean {
        // Exactly one campsite is allowed per lottery.
        if (campsiteIds.size != 1 || campsiteIds[0].isBlank()) {
            return false
        }

        if (ownerId.isBlank()) {
            return false
        }

        if (numberOfWinners < 1) {
            return false
        }

        if (maxBookingDays < 1) {
            return false
        }

        if (waitlistCapacity != null &&
            waitlistCapacity < numberOfWinners
        ) {
            return false
        }

        if (registrationStart == 0L || registrationEnd == 0L) {
            return false
        }

        if (bookingStart == 0L || bookingEnd == 0L) {
            return false
        }

        if (registrationEnd < now) {
            return false
        }

        if (bookingStart <= registrationEnd) {
            return false
        }

        if (bookingEnd < bookingStart) {
            return false
        }

        if (invitedOwnerIds.any { it.isBlank() } ||
            joinedOwnerIds.any { it.isBlank() }
        ) {
            return false
        }

        if (ownerId in invitedOwnerIds ||
            ownerId in joinedOwnerIds
        ) {
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
     * Whether a user can see this lottery.
     */
    fun visibleTo(userId: String): Boolean =
        userId == ownerId || userId in joinedOwnerIds

    /**
     * Returns a copy with the invitation accepted by the given owner.
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
            joinedOwnerIds = joinedOwnerIds + userId,
            maxBookingDays = maxBookingDays
        ).also {
            it.id = this.id
        }
    }

    /**
     * Returns a copy with updated campsite IDs.
     * Other lottery settings remain unchanged.
     */
    fun withCampsiteIds(chosen: List<String>): Lottery =
        Lottery(
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
            joinedOwnerIds = joinedOwnerIds,
            maxBookingDays = maxBookingDays
        ).also {
            it.id = this.id
        }
}
