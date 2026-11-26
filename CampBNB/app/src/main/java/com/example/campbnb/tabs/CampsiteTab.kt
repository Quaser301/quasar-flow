package com.example.campbnb.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.ui.components.GradientButton


/** Owner's My Campsites tab: links to the campsite list, the lottery list and reservations. */
@Composable
fun MyCampsitesTab(
    onOpenMyCampsites: () -> Unit,
    onOpenLotteries: () -> Unit,
    onOpenReservations: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("My Campsites", fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))
        GradientButton("My Campsites", onOpenMyCampsites)
        Spacer(Modifier.height(12.dp))
        GradientButton("My Lotteries", onOpenLotteries)
        Spacer(Modifier.height(12.dp))
        GradientButton("Reservations", onOpenReservations)
    }
}