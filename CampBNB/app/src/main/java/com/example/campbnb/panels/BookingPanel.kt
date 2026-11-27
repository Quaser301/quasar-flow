
package com.example.campbnb.ui.screens

import androidx.compose.runtime.*
import com.example.campbnb.models.BookingCardData
import com.example.campbnb.models.LotteryEntryStatus
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus
import com.example.campbnb.models.WaitingListEntry

/**
 * Connects the entrant's Bookings screen to the data layer.
 *
 * Pending:
 * - Waiting for a lottery draw
 * - Won a lottery and waiting for the entrant's response
 *
 * Confirmed:
 * - Reservations for which the entrant has selected dates
 */
@Composable
fun BookingsPanel(
    currentUserId: String,

    loadWaitingListEntries: (
        String,
        (List<WaitingListEntry>) -> Unit,
        (Exception) -> Unit
    ) -> Unit,

    loadReservations: (
        String,
        (List<ReservationModel>) -> Unit,
        (Exception) -> Unit
    ) -> Unit,

    resolveCampsiteName: (
        String,
        (String) -> Unit,
        (Exception) -> Unit
    ) -> Unit,

    onBookingClick: (BookingCardData) -> Unit = {},
    onMessageOwnerClick: (BookingCardData) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    var selectedTab by remember {
        mutableStateOf(BookingTab.CONFIRMED)
    }

    var waitingListEntries by remember {
        mutableStateOf<List<WaitingListEntry>>(emptyList())
    }

    var reservations by remember {
        mutableStateOf<List<ReservationModel>>(emptyList())
    }

    var campsiteNames by remember {
        mutableStateOf<Map<String, String>>(emptyMap())
    }

    var entriesLoaded by remember {
        mutableStateOf(false)
    }

    var reservationsLoaded by remember {
        mutableStateOf(false)
    }

    // Load the entrant's waiting-list entries and reservations.
    LaunchedEffect(currentUserId) {
        if (currentUserId.isBlank()) {
            onError("Could not identify the signed-in user.")
            return@LaunchedEffect
        }

        entriesLoaded = false
        reservationsLoaded = false
        waitingListEntries = emptyList()
        reservations = emptyList()
        campsiteNames = emptyMap()

        loadWaitingListEntries(
            currentUserId,
            { entries ->
                waitingListEntries = entries
                entriesLoaded = true
            },
            { exception ->
                entriesLoaded = true
                onError(
                    exception.message
                        ?: "Could not load your lottery entries."
                )
            }
        )

        loadReservations(
            currentUserId,
            { loadedReservations ->
                reservations = loadedReservations
                reservationsLoaded = true
            },
            { exception ->
                reservationsLoaded = true
                onError(
                    exception.message
                        ?: "Could not load your bookings."
                )
            }
        )
    }

    // Retrieve campsite names for the entries and reservations.
    LaunchedEffect(
        waitingListEntries,
        reservations,
        entriesLoaded,
        reservationsLoaded
    ) {
        if (!entriesLoaded || !reservationsLoaded) {
            return@LaunchedEffect
        }

        val campsiteIds = buildSet {
            waitingListEntries.forEach { add(it.lotteryId) }
            reservations.forEach { add(it.campsiteId) }
        }

        // Reservation campsite names are resolved below.
        // Lottery IDs are not necessarily campsite IDs, so they
        // are intentionally not used as campsite names.
        val reservationCampsiteIds =
            reservations.map { it.campsiteId }.toSet()

        reservationCampsiteIds.forEach { campsiteId ->
            if (campsiteId !in campsiteNames) {
                resolveCampsiteName(
                    campsiteId,
                    { name ->
                        campsiteNames =
                            campsiteNames + (campsiteId to name)
                    },
                    { exception ->
                        onError(
                            exception.message
                                ?: "Could not load campsite information."
                        )
                    }
                )
            }
        }
    }

    // Lottery entries that still require an entrant response.
    val pendingBookings = waitingListEntries
        .filter {
            it.status == LotteryEntryStatus.WAITING_FOR_DRAW ||
                    it.status == LotteryEntryStatus.WON_AWAITING_RESPONSE
        }
        .map { entry ->
            BookingCardData(
                id = entry.id,
                lotteryId = entry.lotteryId,
                lotteryEntryId = entry.id,
                campsiteId = "",
                campsiteNames = listOf("Lottery ${entry.lotteryId}"),
                status = when (entry.status) {
                    LotteryEntryStatus.WAITING_FOR_DRAW ->
                        "Waiting for draw"

                    LotteryEntryStatus.WON_AWAITING_RESPONSE ->
                        "You won! Respond to your invitation"

                    else -> ""
                },
                canSelectDates =
                    entry.status == LotteryEntryStatus.WON_AWAITING_RESPONSE
            )
        }

    // Reservations with selected dates appear in Confirmed.
    val confirmedBookings = reservations
        .filter {
            it.status == ReservationStatus.PENDING ||
                    it.status == ReservationStatus.ACTIVE
        }
        .map { reservation ->
            BookingCardData(
                id = reservation.lotteryEntryId,
                lotteryId = "",
                lotteryEntryId = reservation.lotteryEntryId,
                campsiteId = reservation.campsiteId,
                campsiteNames = listOf(
                    campsiteNames[reservation.campsiteId]
                        ?: "Campsite"
                ),
                status = when (reservation.status) {
                    ReservationStatus.ACTIVE -> "Active"
                    ReservationStatus.PENDING -> "Pending"
                    else -> ""
                },
                startDate = reservation.startDate,
                endDate = reservation.endDate,
                guests = reservation.guests
            )
        }
    // Reservations whose dates have passed appear in the Past.
    val pastBookings = reservations
        .filter {
            it.status == ReservationStatus.PAST
        }
        .map { reservation ->
            BookingCardData(
                id = reservation.lotteryEntryId,
                lotteryId = "",
                lotteryEntryId = reservation.lotteryEntryId,
                campsiteId = reservation.campsiteId,
                campsiteNames = listOf(
                    campsiteNames[reservation.campsiteId] ?: "Campsite"
                ),
                status = "Past",
                startDate = reservation.startDate,
                endDate = reservation.endDate,
                guests = reservation.guests
            )
        }

    BookingsScreen(
        confirmedBookings = confirmedBookings,
        pendingBookings = pendingBookings,
        pastBookings = pastBookings,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onBookingClick = onBookingClick,
        onMessageOwnerClick = onMessageOwnerClick,
        onAcceptWin = {},
        onDeclineWin = {},
        onSelectDates = {}
    )
}
