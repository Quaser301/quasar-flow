
package com.example.campbnb.models

data class BookingCardData(
    val id: String,
    val lotteryId: String,
    val lotteryEntryId: String = "",
    val campsiteId: String = "",
    val campsiteNames: List<String>,
    val status: String,
    val startDate: String = "",
    val endDate: String = "",
    val guests: Int = 0,
    val canSelectDates: Boolean = false,
    val maxBookingDays: Int = 1
)
