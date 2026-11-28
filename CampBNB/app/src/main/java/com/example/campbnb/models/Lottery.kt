
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
    /** Legacy shared blocked dates, retained for existing Firestore documents. */
    val blockedDates: List<String> = emptyList(),
    /** Blocked dates stored separately for each campsite ID (yyyy-MM-dd). */
    val blockedDatesByCampsite: Map<String, List<String>> = emptyMap()
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


    fun validationErrors(
        now: Long,
        isEditMode: Boolean = false
    ): List<String> {
        val errors = mutableListOf<String>()

        // Campsites
        if (campsiteIds.isEmpty()) {
            errors.add("Select at least one campsite.")
        }

        if (campsiteIds.any { it.isBlank() }) {
            errors.add("One or more campsite IDs are invalid.")
        }

        if (campsiteIds.distinct().size != campsiteIds.size) {
            errors.add("The same campsite cannot be selected more than once.")
        }

        // Owner
        if (ownerId.isBlank()) {
            errors.add("The lottery must have a valid owner.")
        }

        // Winners and waitlist
        if (numberOfWinners < 1) {
            errors.add("The number of winners must be at least 1.")
        }

        if (maxBookingDays < 1) {
            errors.add("The maximum booking duration must be at least 1 day.")
        }

        if (waitlistCapacity != null && waitlistCapacity < numberOfWinners) {
            errors.add("Waitlist capacity cannot be smaller than the number of winners.")
        }

        // Registration and booking periods
        if (registrationStart == 0L || registrationEnd == 0L) {
            errors.add("Select both registration start and end dates.")
        }

        if (bookingStart == 0L || bookingEnd == 0L) {
            errors.add("Select both booking start and end dates.")
        }

        if (!isEditMode &&
            registrationEnd != 0L &&
            DateUtils.utcDate(registrationEnd) < DateUtils.dateOf(now)
        ) {
            errors.add("Registration cannot end before today.")
        }

        if (registrationEnd != 0L && bookingStart != 0L &&
            bookingStart <= registrationEnd
        ) {
            errors.add("The booking period must start after registration ends.")
        }

        if (bookingStart != 0L && bookingEnd != 0L &&
            bookingEnd < bookingStart
        ) {
            errors.add("The booking end date cannot be before its start date.")
        }

        // Invited and joined owners
        if (invitedOwnerIds.any { it.isBlank() }) {
            errors.add("One or more invited owner IDs are invalid.")
        }

        if (joinedOwnerIds.any { it.isBlank() }) {
            errors.add("One or more joined owner IDs are invalid.")
        }

        if (ownerId.isNotBlank() &&
            (ownerId in invitedOwnerIds || ownerId in joinedOwnerIds)
        ) {
            errors.add("The lottery owner cannot invite or join their own lottery.")
        }

        if (invitedOwnerIds.any { it in joinedOwnerIds }) {
            errors.add("An owner cannot be both invited and already joined.")
        }

        if (invitedOwnerIds.distinct().size != invitedOwnerIds.size) {
            errors.add("An owner cannot be invited more than once.")
        }

        if (joinedOwnerIds.distinct().size != joinedOwnerIds.size) {
            errors.add("An owner cannot join more than once.")
        }

        // Available dates
        if (availableStartDate.isBlank() || availableEndDate.isBlank()) {
            errors.add("Select the available date range for the campsites.")
        } else {
            if (availableEndDate < availableStartDate) {
                errors.add("The available end date cannot be before its start date.")
            }

            if (bookingEnd != 0L &&
                availableStartDate <= DateUtils.utcDate(bookingEnd)
            ) {
                errors.add("Available dates must start after the booking period ends.")
            }

            if (!isEditMode &&
                availableStartDate < DateUtils.dateOf(now)
            ) {
                errors.add("Available dates cannot start before today.")
            }
        }

        // Blocked dates
        if (availableStartDate.isNotBlank() && availableEndDate.isNotBlank()) {
            if (blockedDates.any {
                    it < availableStartDate || it > availableEndDate
                }
            ) {
                errors.add("Blocked dates must fall within the available date range.")
            }
        }

        if (blockedDates.distinct().size != blockedDates.size) {
            errors.add("A date cannot be blocked more than once.")
        }

        return errors
    }

    fun isValid(
        now: Long,
        isEditMode: Boolean = false
    ): Boolean {
        return validationErrors(now, isEditMode).isEmpty()
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
            maxBookingDays = maxBookingDays,
            availableStartDate = availableStartDate,
            availableEndDate = availableEndDate,
            blockedDates = blockedDates,
            blockedDatesByCampsite = blockedDatesByCampsite
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
            blockedDates = blockedDates,
            blockedDatesByCampsite = blockedDatesByCampsite
        ).also {
            it.id = this.id
        }
}
