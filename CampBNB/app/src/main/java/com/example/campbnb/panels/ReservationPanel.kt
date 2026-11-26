package com.example.campbnb.panels

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.campbnb.controller.ReservationFilter
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.FileDownloadWriter
import com.example.campbnb.data.NotificationPreferencesService
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.ui.screens.ReservationScreen


/**
 * The reservations view, opened by a notification tap, the owner's My Campsites link and
 * the camper's bottom tab. Which reservations a viewer may list is decided by the read
 * handed here: an owner browses their own campsites and sees every reservation at each,
 * while every other viewer reads their own reservations and is offered only the sites
 * that carry them - an entrant reaching this view gets their own bookings, not an empty
 * campsite selector. The screen only draws what it is handed.
 *
 * @param userId The viewer the browsing is decided against: the owner of any site browses
 *     their own sites and sees every reservation at each; every other viewer reads their
 *     own reservations and is offered only the sites that carry them.
 * @param campsiteRepo Source of the campsites to browse.
 * @param reservationRepo Source of the reservations.
 * @param notificationPreferencesService CSV export preferences, handed on to the screen.
 * @param currentUserID The signed-in user, used for CSV download naming (controller.md's
 *     `generateReceiptCsv`).
 * @param fileDownloadWriter Sink for receipt downloads, handed on to the screen.
 * @param goBack The return function handed to the screen, owned by the caller that opened
 *     this view.
 */
@Composable
fun ReservationsPanel(
    userId: String,

    campsiteRepo: CampsiteRepository,
    reservationRepo: ReservationRepository,
    notificationPreferencesService: NotificationPreferencesService,
    currentUserID: String,
    fileDownloadWriter: FileDownloadWriter,
    goBack: () -> Unit
) {
    val context = LocalContext.current

    var campsites by remember {
        mutableStateOf<List<Campsite>>(emptyList())
    }

    var selectedCampsiteId by remember {
        mutableStateOf<String?>(null)
    }

    var selectedFilter by remember {
        mutableStateOf(ReservationFilter.ACTIVE)
    }

    var reservations by remember {
        mutableStateOf<List<com.example.campbnb.models.ReservationModel>>(emptyList())
    }

    // Load the campsites this viewer browses: the sites of a user who owns any (by
    // holding sites, not by carrying an owner-role flag), or for anyone else
    // the sites that carry their own reservations.
    LaunchedEffect(userId) {
        campsiteRepo.getAll(
            onSuccess = { allCampsites ->
                // A real owner is recognised by owning sites: their saved profile does not
                // have to carry the OWNER role flag, and gating on the flag left the
                // top screen of a plain owner empty (the reported regression).
                if (allCampsites.any { it.ownerId == userId }) {
                    val browsedCampsites = allCampsites.filter {
                        it.ownerId == userId
                    }
                    campsites = browsedCampsites
                    if (selectedCampsiteId == null ||
                        browsedCampsites.none { it.id == selectedCampsiteId }
                    ) {
                        selectedCampsiteId = browsedCampsites.firstOrNull()?.id
                    }
                } else {
                    reservationRepo.getForEntrant(
                        entrantId = userId,
                        onSuccess = { ownReservations ->
                            val browsedCampsites = allCampsites.filter { campsite ->
                                ownReservations.any { it.campsiteId == campsite.id }
                            }
                            campsites = browsedCampsites
                            if (selectedCampsiteId == null ||
                                browsedCampsites.none { it.id == selectedCampsiteId }
                            ) {
                                selectedCampsiteId = browsedCampsites.firstOrNull()?.id
                            }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not load your reservations: ${'$'}{error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            },
            onFailure = { error ->
                Toast.makeText(
                    context,
                    "Could not load campsites: ${'$'}{error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // Load reservations whenever the campsite or filter changes. An owner browsing their
    // own sites sees every reservation at a site; anyone else only their own.
    LaunchedEffect(selectedCampsiteId, selectedFilter) {
        val campsiteId = selectedCampsiteId
        // Match the browse-mode decision above: the browsing sites of an owner are all
        // their own, the browsing sites of anyone else are not.
        val viewerIsOwner = campsites.any { it.ownerId == userId }

        if (campsiteId == null) {
            reservations = emptyList()
        } else {
            reservationRepo.run {
                when (selectedFilter) {
                    ReservationFilter.ACTIVE -> getActive(
                        campsiteId,
                        onSuccess = { loaded ->
                            reservations =
                                if (viewerIsOwner) loaded
                                else loaded.filter { it.entrantId == userId }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not load reservations: ${'$'}{error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )

                    ReservationFilter.PAST -> getPast(
                        campsiteId,
                        onSuccess = { loaded ->
                            reservations =
                                if (viewerIsOwner) loaded
                                else loaded.filter { it.entrantId == userId }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not load reservations: ${'$'}{error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )

                    ReservationFilter.CANCELLED -> getCancelled(
                        campsiteId,
                        onSuccess = { loaded ->
                            reservations =
                                if (viewerIsOwner) loaded
                                else loaded.filter { it.entrantId == userId }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not load reservations: ${'$'}{error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    }

    ReservationScreen(
        campsites = campsites,
        selectedCampsiteId = selectedCampsiteId,
        selectedFilter = selectedFilter,
        reservations = reservations,
        onCampsiteSelected = { campsiteId ->
            selectedCampsiteId = campsiteId
        },
        onFilterSelected = { filter ->
            selectedFilter = filter
        },
        onBack = goBack,
        // The csv download names the user whose reservations were written and writes
        // through the app's file boundary; without these the export silently offers
        // nothing (the reported regression).
        csvPreferences = notificationPreferencesService,
        currentUserId = currentUserID,
        fileDownloadWriter = fileDownloadWriter
    )
}
