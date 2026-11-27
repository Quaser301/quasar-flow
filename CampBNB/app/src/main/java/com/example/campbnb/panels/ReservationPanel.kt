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
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.ui.screens.ReservationScreen
import com.example.campbnb.controller.ReservationController

/**
 * The reservations view, opened by a notification tap, the owner's My Campsites link and
 * the camper's bottom tab.
 *
 * Which reservations a viewer may list is decided by the read handed here:
 * - An owner browses their own campsites and sees every reservation at each campsite.
 * - Every other viewer reads their own reservations and is offered only the campsites
 *   that carry their reservations.
 *
 * The screen itself only draws what it is handed. This panel is responsible for loading
 * and filtering the data before passing it to the screen.
 *
 * @param userId The viewer whose access determines which campsites and reservations
 *        can be browsed. Owners see reservations at their own campsites; other
 *        viewers see only their own reservations.
 * @param campsiteRepo Source of the campsites to browse.
 * @param reservationRepo Source of the reservations.
 * @param notificationPreferencesService CSV export preferences, handed on to the screen.
 * @param currentUserID The signed-in user, used for CSV download naming.
 * @param fileDownloadWriter Sink for receipt downloads, handed on to the screen.
 * @param goBack The return function handed to the screen, owned by the caller that opened
 *        this view.
 */
@Composable
fun ReservationsPanel(
    userId: String,
    campsiteRepo: CampsiteRepository,
    reservationRepo: ReservationRepository,
    reservationController: ReservationController,
    notificationPreferencesService: NotificationPreferencesService,
    currentUserID: String,
    fileDownloadWriter: FileDownloadWriter,
    goBack: () -> Unit
) {
    val context = LocalContext.current

    // The campsites currently available for this viewer to browse.
    var campsites by remember {
        mutableStateOf<List<Campsite>>(emptyList())
    }

    // The campsite currently selected in the reservation screen.
    var selectedCampsiteId by remember {
        mutableStateOf<String?>(null)
    }

    // The currently selected reservation category.
    // Active is the default view when the screen is first opened.
    var selectedFilter by remember {
        mutableStateOf(ReservationFilter.ACTIVE)
    }

    // Reservations loaded for the currently selected campsite and filter.
    var reservations by remember {
        mutableStateOf<List<ReservationModel>>(emptyList())
    }

    // Triggers a reload after a reservation is rejected.
    var reservationRefreshKey by remember {
        mutableStateOf(0)
    }

    // Tracks whether this viewer is browsing as a campsite owner.
    // This is determined by ownership of campsites rather than a role flag,
    // since an owner may not necessarily have an OWNER role stored in their profile.
    var viewerIsOwner by remember {
        mutableStateOf(false)
    }

    // Load the campsites this viewer is allowed to browse.
    //
    // A real owner is recognised by owning sites, not by carrying an owner-role flag.
    // Gating on the role flag previously left the top screen of a plain owner empty.
    //
    // An owner loads their own campsites.
    // An entrant loads only campsites associated with their own reservations.
    LaunchedEffect(userId) {
        campsiteRepo.getAll(
            onSuccess = { allCampsites ->

                // Determine whether the viewer owns any campsites.
                val ownsCampsites = allCampsites.any {
                    it.ownerId == userId
                }

                viewerIsOwner = ownsCampsites

                if (ownsCampsites) {
                    // Owners browse only the campsites they registered.
                    val browsedCampsites = allCampsites.filter {
                        it.ownerId == userId
                    }

                    campsites = browsedCampsites

                    // Preserve the current selection if it is still valid.
                    // Otherwise select the first campsite available to this owner.
                    if (
                        selectedCampsiteId == null ||
                        browsedCampsites.none {
                            it.id == selectedCampsiteId
                        }
                    ) {
                        selectedCampsiteId =
                            browsedCampsites.firstOrNull()?.id
                    }
                } else {
                    // Non-owners do not browse all registered campsites.
                    // They only see campsites associated with their own reservations.
                    reservationRepo.getForEntrant(
                        entrantId = userId,
                        onSuccess = { ownReservations ->

                            // Find the campsites that appear in this viewer's reservations.
                            val browsedCampsites = allCampsites.filter { campsite ->
                                ownReservations.any {
                                    it.campsiteId == campsite.id
                                }
                            }

                            campsites = browsedCampsites

                            // Preserve the selected campsite if possible.
                            // If it is no longer available, select the first matching site.
                            if (
                                selectedCampsiteId == null ||
                                browsedCampsites.none {
                                    it.id == selectedCampsiteId
                                }
                            ) {
                                selectedCampsiteId =
                                    browsedCampsites.firstOrNull()?.id
                            }
                        },
                        onFailure = { error ->
                            // Inform the viewer if their reservation list could not be loaded.
                            Toast.makeText(
                                context,
                                "Could not load your reservations: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            },
            onFailure = { error ->
                // Inform the viewer if the campsite list could not be retrieved.
                Toast.makeText(
                    context,
                    "Could not load campsites: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // Load reservations whenever the selected campsite, filter or browsing mode changes.
    //
    // Owners see every reservation associated with their selected campsite.
    // Entrants see only reservations belonging to their own user ID.
    //
    // Pending reservations are read-only for owners: entrants have already selected
    // their dates, and owners cannot intervene with that selection.
    LaunchedEffect(
        selectedCampsiteId,
        selectedFilter,
        viewerIsOwner,
        reservationRefreshKey
    ) {
        val campsiteId = selectedCampsiteId

        // If there is no campsite selected, there is nothing to display.
        if (campsiteId == null) {
            reservations = emptyList()
        } else {

            // Select the appropriate repository query for the chosen filter.
            when (selectedFilter) {

                // Pending: dates selected by entrants, awaiting the reservation's
                // active period. The owner does not modify these dates.
                ReservationFilter.PENDING ->
                    reservationRepo.getPending(
                        campsiteId,
                        onSuccess = { loaded ->
                            // Owners receive the complete campsite list.
                            // Non-owners receive only their own reservations.
                            reservations =
                                if (viewerIsOwner) loaded
                                else loaded.filter {
                                    it.entrantId == userId
                                }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not load reservations: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )

                // Active: reservations currently in their active period.
                ReservationFilter.ACTIVE ->
                    reservationRepo.getActive(
                        campsiteId,
                        onSuccess = { loaded ->
                            reservations =
                                if (viewerIsOwner) loaded
                                else loaded.filter {
                                    it.entrantId == userId
                                }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not load reservations: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )

                // Past: reservations whose active period has ended.
                ReservationFilter.PAST ->
                    reservationRepo.getPast(
                        campsiteId,
                        onSuccess = { loaded ->
                            reservations =
                                if (viewerIsOwner) loaded
                                else loaded.filter {
                                    it.entrantId == userId
                                }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not load reservations: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )

                // Cancelled: cancelled or rejected reservations.
                ReservationFilter.CANCELLED ->
                    reservationRepo.getCancelled(
                        campsiteId,
                        onSuccess = { loaded ->
                            reservations =
                                if (viewerIsOwner) loaded
                                else loaded.filter {
                                    it.entrantId == userId
                                }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not load reservations: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
            }
        }
    }

    // Draw the reservations screen using the loaded data and current selection.
    // The screen remains responsible for the layout, campsite selection, filter UI,
    // reservation cards, CSV export UI and back button.
    ReservationScreen(
        campsites = campsites,
        selectedCampsiteId = selectedCampsiteId,
        selectedFilter = selectedFilter,
        reservations = reservations,
        viewerIsOwner = viewerIsOwner,

        onRejectReservation = { reservation ->
            reservationController.rejectReservation(
                reservationId = reservation.lotteryEntryId,
                requesterId = currentUserID,
                onSuccess = {
                    Toast.makeText(
                        context,
                        "Reservation rejected successfully.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Reload the currently selected reservation list.
                    reservationRefreshKey++
                },
                onFailure = { error ->
                    Toast.makeText(
                        context,
                        "Could not reject reservation: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        },

        // Update the campsite selection and let the loading effect refresh the list.
        onCampsiteSelected = { campsiteId ->
            selectedCampsiteId = campsiteId
        },

        // Update the filter and let the loading effect retrieve the matching records.
        onFilterSelected = { filter ->
            selectedFilter = filter
        },

        // Navigation is controlled by the caller that opened this panel.
        onBack = goBack,

        // CSV export settings and file handling are delegated to the screen.
        csvPreferences = notificationPreferencesService,
        currentUserId = currentUserID,
        fileDownloadWriter = fileDownloadWriter
    )
}