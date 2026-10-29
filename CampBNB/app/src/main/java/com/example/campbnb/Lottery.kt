package com.example.campbnb

data class Lottery(
    val id: String = "",
    val ownerId: String = "",
    val campsiteIds: List<String> = emptyList(),
    val registrationStart: Long = 0,
    val registrationEnd: Long = 0,
    val bookingStart: Long = 0,
    val bookingEnd: Long = 0,
    val numberOfWinners: Int = 0,
    val status: LotteryStatus = LotteryStatus.OPEN,
    val drawn: Boolean = false,
    val waitlistCapacity: Int? = null
)