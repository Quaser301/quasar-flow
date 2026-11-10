
package com.example.campbnb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.controller.ReservationFilter
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.ReservationModel

private val CampGreen = Color(0xFF64A83F)
private val CampDarkGreen = Color(0xFF397C28)
private val CampLightGray = Color(0xFFE0E0E0)
private val CampCardGray = Color(0xFFD9D9D9)

@Composable
fun ReservationScreen(
    campsites: List<Campsite>,
    selectedCampsiteId: String?,
    selectedFilter: ReservationFilter,
    reservations: List<ReservationModel>,
    onCampsiteSelected: (String) -> Unit,
    onFilterSelected: (ReservationFilter) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Back button and campsite selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clickable(onClick = onBack),
                shape = CircleShape,
                color = CampGreen
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "‹",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            CampsiteSelector(
                campsites = campsites,
                selectedCampsiteId = selectedCampsiteId,
                onCampsiteSelected = onCampsiteSelected,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))

        // Active / Past / Cancelled
        ReservationFilterTabs(
            selectedFilter = selectedFilter,
            onFilterSelected = onFilterSelected
        )

        Spacer(Modifier.height(16.dp))

        // Reservation content
        ReservationList(
            reservations = reservations,
            filter = selectedFilter,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CampsiteSelector(
    campsites: List<Campsite>,
    selectedCampsiteId: String?,
    onCampsiteSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (campsites.isEmpty()) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(10.dp),
            color = CampLightGray
        ) {
            Text(
                text = "No campsite selected",
                modifier = Modifier.padding(14.dp),
                fontSize = 14.sp
            )
        }
        return
    }

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(campsites, key = { it.id }) { campsite ->
            val selected = campsite.id == selectedCampsiteId

            Surface(
                modifier = Modifier.clickable {
                    onCampsiteSelected(campsite.id)
                },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) CampLightGray
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 10.dp
                    )
                ) {
                    Text(
                        text = campsite.campName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (selected) "Selected" else "Tap to select",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}

@Composable
private fun ReservationFilterTabs(
    selectedFilter: ReservationFilter,
    onFilterSelected: (ReservationFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                CampLightGray,
                RoundedCornerShape(50.dp)
            )
            .padding(3.dp)
    ) {
        ReservationTab(
            label = "Active",
            selected = selectedFilter == ReservationFilter.ACTIVE,
            onClick = {
                onFilterSelected(ReservationFilter.ACTIVE)
            },
            modifier = Modifier.weight(1f)
        )

        ReservationTab(
            label = "Past",
            selected = selectedFilter == ReservationFilter.PAST,
            onClick = {
                onFilterSelected(ReservationFilter.PAST)
            },
            modifier = Modifier.weight(1f)
        )

        ReservationTab(
            label = "Cancelled",
            selected = selectedFilter == ReservationFilter.CANCELLED,
            onClick = {
                onFilterSelected(ReservationFilter.CANCELLED)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ReservationTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50.dp),
        color = if (selected) CampGreen else Color.Transparent
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = 4.dp,
                vertical = 9.dp
            ),
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

@Composable
private fun ReservationList(
    reservations: List<ReservationModel>,
    filter: ReservationFilter,
    modifier: Modifier = Modifier
) {
    if (reservations.isEmpty()) {
        val message = when (filter) {
            ReservationFilter.ACTIVE ->
                "No active reservations found."

            ReservationFilter.PAST ->
                "No past reservations found."

            ReservationFilter.CANCELLED ->
                "No cancelled reservations found."
        }

        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = CampLightGray
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(18.dp),
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(reservations) { reservation ->
            ReservationCard(reservation)
        }
    }
}

@Composable
private fun ReservationCard(
    reservation: ReservationModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = CampCardGray
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "Entrant ID: ${reservation.entrantId}",
                fontSize = 11.sp,
                color = Color.DarkGray
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reservation",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Guests: ${reservation.guests}",
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = reservation.startDate,
                    fontSize = 11.sp
                )
                Text(
                    text = "Status: ${reservation.status}",
                    fontSize = 11.sp
                )
            }

            Text(
                text = "Until: ${reservation.endDate}",
                fontSize = 11.sp
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = "Reservation ID: ${reservation.lotteryEntryId}",
                fontSize = 10.sp
            )

            Text(
                text = "Booking Date: ${reservation.createdAt}",
                fontSize = 10.sp
            )

            Text(
                text = if (reservation.refundRecorded)
                    "Refund Recorded"
                else
                    "Refund Status: N/A",
                fontSize = 10.sp
            )
        }
    }
}
