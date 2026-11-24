package com.example.campbnb.graphs

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.campbnb.Screen
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.FileDownloadWriter
import com.example.campbnb.data.NotificationPreferencesService
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.panels.ReservationsPanel
import kotlinx.serialization.Serializable





fun NavGraphBuilder.ReservationGraph(
    userID:String,
    currentUserId: String,


    campsiteRepo: CampsiteRepository,
    reservationRepo: ReservationRepository,
    notificationPreferences: NotificationPreferencesService,
    fileDownloadWriter: FileDownloadWriter,

    onNavBack: () -> Unit,
) {
    navigation<NavGraphNodes.ReservationGraph>(startDestination = Screen.Reservations) {
        // Reservations: the view opened by a notification tap, the owner's My Campsites
        // link and the camper's bottom tab. The private panel below owns the loading and
        // drawing, so all entries share one wiring.

        // The camper's Reservations tab hosts the panel directly: pressing the
        // tab lands in the view itself, no second button asking again. Back pops
        // the tab's own frame on the nested graph, which lands the bar back on
        // the first tab — Explore stays under every other tab.
        composable<Screen.Reservations> {
            ReservationsPanel(
                userID,
                campsiteRepo,
                reservationRepo,
                notificationPreferences,
                currentUserId,
                fileDownloadWriter,
                onNavBack
            )
        }


    }
}