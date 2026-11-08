
package com.example.campbnb.models

/** One booking as shown on the bookings screens: Home builds these from the user's
 *  reservations and the Bookings tab renders them.
 *
 * @property id the booking's identifier, used as the list key.
 * @property lotteryId the lottery the booking came from; empty when it is a plain reservation.
 * @property campsiteNames names of the sites the booking covers.
 * @property status status text shown as-is under the names.
 * @property startDate first night of the booking; empty means not yet known.
 * @property endDate last night wanted; empty means only [startDate].
 * @property guests party size booked; 0 means not recorded. */
data class BookingCardData(
    val id: String,
    val lotteryId: String,
    val campsiteNames: List<String>,
    val status: String,
    val startDate: String = "",
    val endDate: String = "",
    val guests: Int = 0
)
