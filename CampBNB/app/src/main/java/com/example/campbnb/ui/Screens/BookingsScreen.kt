package com.example.campbnb.ui.Screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.models.BookingCardData
import com.example.campbnb.ui.components.UserRole

private val BookingGreen = Color(0xFF64A83F)
private val BookingGray = Color(0xFFD9D9D9)

/** Which half of the bookings list the screen shows. */
enum class BookingTab {
    CONFIRMED,
    PENDING
}

/** The "Bookings" home tab: a Confirmed/Pending switch over one list of booking
 *  cards, with a placeholder notice while the list is empty.
 *
 * @param confirmedBookings the settled bookings
 * @param pendingBookings the bookings not settled yet
 * @param selectedTab which list is open
 * @param onTabSelected called with the tab the switch was tapped to open
 * @param onBookingClick called with a tapped booking card */
@Composable
fun BookingsScreen(
    confirmedBookings: List<BookingCardData>,
    pendingBookings: List<BookingCardData>,
    selectedTab: BookingTab,
    onTabSelected: (BookingTab) -> Unit,
    onBookingClick: (BookingCardData) -> Unit = {}
) {
    val displayedBookings = when (selectedTab) {
        BookingTab.CONFIRMED -> confirmedBookings
        BookingTab.PENDING -> pendingBookings
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Text(
                text = "My Bookings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BookingGray, RoundedCornerShape(50.dp))
                    .padding(3.dp)
            ) {
                BookingTabButton(
                    label = "Confirmed",
                    selected = selectedTab == BookingTab.CONFIRMED,
                    modifier = Modifier.weight(1f)
                ) {
                    onTabSelected(BookingTab.CONFIRMED)
                }

                BookingTabButton(
                    label = "Pending",
                    selected = selectedTab == BookingTab.PENDING,
                    modifier = Modifier.weight(1f)
                ) {
                    onTabSelected(BookingTab.PENDING)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (displayedBookings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BookingGray
                    ) {
                        Text(
                            text = when (selectedTab) {
                                BookingTab.CONFIRMED ->
                                    "No confirmed bookings"

                                BookingTab.PENDING ->
                                    "No pending bookings"
                            },
                            modifier = Modifier.padding(18.dp),
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        displayedBookings,
                        key = { it.id }
                    ) { booking ->
                        BookingCard(booking) {
                            onBookingClick(booking)
                        }
                    }
                }
            }
        }
    }
}

/** One pill-shaped tab of the switch; only the selected one is filled in. */
@Composable
private fun BookingTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50.dp),
        color = if (selected) BookingGreen else Color.Transparent
    ) {
        Box(
            modifier = Modifier.padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (selected) Color.White else Color.DarkGray,
                fontWeight = if (selected) FontWeight.SemiBold
                else FontWeight.Normal
            )
        }
    }
}

/** One booking card: the site names first, then the status text, the dates and the
 *  party size only when they are recorded.
 *
 * @param booking the booking to show
 * @param onClick called when the card is tapped */
@Composable
private fun BookingCard(
    booking: BookingCardData,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = BookingGray
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = booking.campsiteNames.joinToString(", ").ifBlank {
                    "Campsite name unavailable"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = booking.status,
                fontSize = 12.sp
            )

            if (booking.startDate.isNotBlank()) {
                Text("${booking.startDate} – ${booking.endDate}")
            }

            if (booking.guests > 0) {
                Text("Guests: ${booking.guests}")
            }
        }
    }
}