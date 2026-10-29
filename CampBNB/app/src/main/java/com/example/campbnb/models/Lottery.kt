package com.example.campbnb.models

/**
 * Display state of a lottery, derived from its dates by [Lottery.status].
 * Enum and formatting suggested by Claude AI (prompted for an effective way to store a
 * lottery's status so it can be displayed).
 */
enum class LotteryStatus { UPCOMING, OPEN, DRAWN, BOOKING, CLOSED }

/**
 * Entity: a lottery over one or more campsites (US 02.04.01-.07). Times are epoch millis.
 * Status is derived from the dates.
 */
data class Lottery(
    val id: String = "",
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
){

}