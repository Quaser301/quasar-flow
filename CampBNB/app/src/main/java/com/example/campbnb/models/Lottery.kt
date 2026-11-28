
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
    val maxBookingDays: Int = 1,
    /** First date winners may stay (yyyy-MM-dd). Always after the booking period ends. */
    val availableStartDate: String = "",
    /** Last date winners may stay (yyyy-MM-dd). */
    val availableEndDate: String = "",
    /** Dates inside the range the owner has closed to winners (yyyy-MM-dd). */
    val blockedDates: List<String> = emptyList()
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
        // A lottery must cover at least one campsite.
        // Every campsite ID must be nonblank and unique.
        if (
            campsiteIds.isEmpty() ||
            campsiteIds.any { it.isBlank() } ||
            campsiteIds.distinct().size != campsiteIds.size
        ) {
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

        // Winners need dates to choose from: a range that starts after the booking period
        // ends (so never before or overlapping registration and booking) and isn't in the past.
        if (availableStartDate.isBlank() || availableEndDate.isBlank()) return false
        if (availableEndDate < availableStartDate) return false
        if (availableStartDate <= DateUtils.utcDate(bookingEnd)) return false
        if (availableStartDate < DateUtils.dateOf(now)) return false

        // Blocked dates must sit inside the range, once each.
        if (blockedDates.any { it < availableStartDate || it > availableEndDate }) return false
        if (blockedDates.distinct().size != blockedDates.size) return false

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
            maxBookingDays = maxBookingDays,
            availableStartDate = availableStartDate,
            availableEndDate = availableEndDate,
            blockedDates = blockedDates
        ).also {
            it.id = this.id
        }
}
