
package com.example.campbnb.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.controller.ReservationFilter
import com.example.campbnb.data.NoFileDownloadWriter
import com.example.campbnb.data.FileDownloadWriter
import com.example.campbnb.data.NoNotificationPreferencesService
import com.example.campbnb.data.NotificationPreferencesService
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.NotificationSettings
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.reservationCsvBytesOf
import com.example.campbnb.models.reservationCsvFileName
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
    viewerIsOwner: Boolean = false,
    onRejectReservation: (ReservationModel) -> Unit = {},
    onCampsiteSelected: (String) -> Unit,
    onFilterSelected: (ReservationFilter) -> Unit,
    onBack: () -> Unit,
    csvPreferences: NotificationPreferencesService = NoNotificationPreferencesService(),
    currentUserId: String = "",
    fileDownloadWriter: FileDownloadWriter = NoFileDownloadWriter(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Back button and campsite selector.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .testTag("reservation_back")
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

        // Pending / Active / Past / Cancelled reservation filters.
        // Selecting a filter tells ReservationsPanel to load the matching records.
        ReservationFilterTabs(
            selectedFilter = selectedFilter,
            onFilterSelected = onFilterSelected
        )

        Spacer(Modifier.height(16.dp))

        // Reservation content.
        // The list displays an empty-state message when the selected category has
        // no reservations, rather than showing an error or a blank screen.
        ReservationList(
            reservations = reservations,
            filter = selectedFilter,
            viewerIsOwner = viewerIsOwner,
            onRejectReservation = onRejectReservation,
            campsitesById = campsites.groupBy { campsite -> campsite.id },
            csvPreferences = csvPreferences,
            currentUserId = currentUserId,
            fileDownloadWriter = fileDownloadWriter,
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

    // Horizontally scrollable campsite selector.
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(campsites, key = { it.id }) { campsite ->
            val selected = campsite.id == selectedCampsiteId

            Surface(
                modifier = Modifier
                    .testTag("campsite_choice")
                    .clickable {
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
    // Each filter receives equal width so all four categories fit in the row.
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
            label = "Pending",
            selected = selectedFilter == ReservationFilter.PENDING,
            onClick = {
                onFilterSelected(ReservationFilter.PENDING)
            },
            modifier = Modifier
                .testTag("reservation_tab_pending")
                .weight(1f)
        )

        ReservationTab(
            label = "Active",
            selected = selectedFilter == ReservationFilter.ACTIVE,
            onClick = {
                onFilterSelected(ReservationFilter.ACTIVE)
            },
            modifier = Modifier
                .testTag("reservation_tab_active")
                .weight(1f)
        )

        ReservationTab(
            label = "Past",
            selected = selectedFilter == ReservationFilter.PAST,
            onClick = {
                onFilterSelected(ReservationFilter.PAST)
            },
            modifier = Modifier
                .testTag("reservation_tab_past")
                .weight(1f)
        )

        ReservationTab(
            label = "Cancelled",
            selected = selectedFilter == ReservationFilter.CANCELLED,
            onClick = {
                onFilterSelected(ReservationFilter.CANCELLED)
            },
            modifier = Modifier
                .testTag("reservation_tab_cancelled")
                .weight(1f)
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
                horizontal = 2.dp,
                vertical = 9.dp
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
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
    viewerIsOwner: Boolean = false,
    onRejectReservation: (ReservationModel) -> Unit = {},
    campsitesById: Map<String, List<Campsite>> = emptyMap(),
    csvPreferences: NotificationPreferencesService = NoNotificationPreferencesService(),
    currentUserId: String = "",
    fileDownloadWriter: FileDownloadWriter = NoFileDownloadWriter(),
    modifier: Modifier = Modifier
) {
    // Show a category-specific empty state instead of an error when there
    // are currently no reservations matching the selected filter.
    if (reservations.isEmpty()) {
        val message = when (filter) {
            ReservationFilter.PENDING ->
                "No pending reservations found."

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

    // Display each reservation in its own card.
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = reservations,
            key = { it.lotteryEntryId }
        ) { reservation ->

            // Find the campsite associated with this reservation.
            // This is used for the campsite name and CSV receipt information.
            val campsite =
                campsitesById[reservation.campsiteId]?.firstOrNull()

            // CSV export is shown only when the signed-in user has enabled
            // the CSV notification/export preference.
            val csvAllowed = csvPreferences
                .notificationSettingsFor(currentUserId)
                .contains(NotificationSettings.CSV)

            ReservationCard(
                reservation = reservation,
                campsite = campsite,
                csvAllowed = csvAllowed,
                fileDownloadWriter = fileDownloadWriter,
                showRejectButton = viewerIsOwner && filter == ReservationFilter.PENDING,
                onRejectReservation = onRejectReservation
            )
        }
    }
}

@Composable
private fun ReservationCard(
    reservation: ReservationModel,
    campsite: Campsite? = null,
    csvAllowed: Boolean = false,
    fileDownloadWriter: FileDownloadWriter = NoFileDownloadWriter(),
    showRejectButton: Boolean = false,
    onRejectReservation: (ReservationModel) -> Unit = {},
) {
    val context = LocalContext.current
    var showRejectConfirmation by remember(reservation.lotteryEntryId) {
        mutableStateOf(false)
    }
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
            // Display the campsite name when its record is available.
            Text(
                text = campsite?.campName ?: "Campsite reservation",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

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
                    text = "From: ${reservation.startDate}",
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

            // The lottery entry ID identifies the reservation's originating entry.
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

            if (showRejectButton) {
                Button(
                    onClick = {
                        showRejectConfirmation = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reservation_reject")
                ) {
                    Text("Reject Reservation")
                }
            }

            if (showRejectConfirmation) {
                AlertDialog(
                    onDismissRequest = {
                        showRejectConfirmation = false
                    },
                    title = {
                        Text("Reject Reservation?")
                    },
                    text = {
                        Text(
                            "Are you sure you want to reject this reservation? " +
                                    "The selected dates will be released."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showRejectConfirmation = false
                                onRejectReservation(reservation)
                            }
                        ) {
                            Text("Reject")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showRejectConfirmation = false
                            }
                        ) {
                            Text("Keep Reservation")
                        }
                    }
                )
            }

            // CSV download is optional and depends on the user's preferences.
            // The file writer handles the actual file creation/download.
            if (csvAllowed) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("reservation_csv")
                        .clickable {
                            val fileName = reservationCsvFileName(reservation)
                            fileDownloadWriter.downloadFile(
                                fileName,
                                reservationCsvBytesOf(reservation, campsite)
                            ) { result ->
                                // Tell the user whether the file was saved and where.
                                val message = if (result.isSuccess) {
                                    "Saved $fileName to your Downloads folder"
                                } else {
                                    "Could not save the file"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = CampDarkGreen
                ) {
                    Text(
                        text = "Download CSV",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(
                            vertical = 8.dp,
                            horizontal = 12.dp
                        )
                    )
                }
            }
        }
    }
}
