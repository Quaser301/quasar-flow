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
 * Entity: a lottery over one or more campsites (US 02.04.01-.07). Times are epoch millis.
 * Status is derived from the dates.
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
    val winnerIds: List<String> = emptyList()
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
        return true
    }

}