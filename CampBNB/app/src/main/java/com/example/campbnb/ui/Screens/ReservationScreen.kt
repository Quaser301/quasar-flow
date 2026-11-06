package com.example.campbnb.ui.Screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.controller.ReservationFilter
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.ReservationModel

@Composable
fun ReservationScreen(
    campsites: List<Campsite>,
    selectedCampsiteId: String?,
    selectedFilter: ReservationFilter,
    reservations: List<ReservationModel>,
    onCampsiteSelected: (String) -> Unit,
    onFilterSelected: (ReservationFilter) -> Unit
) {
    Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
) {
    
    Text(
        text = "Reservations",
        fontSize = 24.sp
    )
    
    Spacer(Modifier.height(16.dp))
    
    CampsiteSelector(
        campsites = campsites,
        selectedCampsiteId = selectedCampsiteId,
        onCampsiteSelected = onCampsiteSelected
    )
    
    Spacer(Modifier.height(16.dp))
    
    ReservationFilterTabs(
        selectedFilter = selectedFilter,
        onFilterSelected = onFilterSelected
    )
    
    Spacer(Modifier.height(16.dp))
    
    ReservationList(
        reservations = reservations,
        filter = selectedFilter
    )
}
}

@Composable
private fun CampsiteSelector(
    campsites: List<Campsite>,
    selectedCampsiteId: String?,
    onCampsiteSelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        items(campsites) { campsite ->

            FilterChip(
                selected = campsite.id == selectedCampsiteId,
                onClick = {
                    onCampsiteSelected(campsite.id)
                },
                label = {
                    Text(campsite.campName)
                }
            )
        }
    }
}

@Composable
private fun ReservationFilterTabs(
    selectedFilter: ReservationFilter,
    onFilterSelected: (ReservationFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {

        FilterChip(
            selected = selectedFilter == ReservationFilter.ACTIVE,
            onClick = {
                onFilterSelected(ReservationFilter.ACTIVE)
            },
            label = { Text("Active") }
        )

        FilterChip(
            selected = selectedFilter == ReservationFilter.PAST,
            onClick = {
                onFilterSelected(ReservationFilter.PAST)
            },
            label = { Text("Past") }
        )

        FilterChip(
            selected = selectedFilter == ReservationFilter.CANCELLED,
            onClick = {
                onFilterSelected(ReservationFilter.CANCELLED)
            },
            label = { Text("Cancelled") }
        )
    }
}

@Composable
private fun ReservationList(
    reservations: List<ReservationModel>,
    filter: ReservationFilter
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
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(message)
        }

        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = "Entrant ID: ${reservation.entrantId}"
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = reservation.startDate
            )

            Text(
                text = reservation.endDate
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Guests: ${reservation.guests}"
            )

            Text(
                text = "Status: ${reservation.status}"
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Reservation ID: ${reservation.lotteryEntryId}",
                fontSize = 12.sp
            )

            Text(
                text = "Booked: ${reservation.createdAt}",
                fontSize = 12.sp
            )

            Text(
                text = if (reservation.refundRecorded)
                    "Refund Recorded"
                else
                    "Refund Status: N/A",
                fontSize = 12.sp
            )
        }
    }
}