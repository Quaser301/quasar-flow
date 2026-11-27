package com.example.campbnb.graphs

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.example.campbnb.Screen
import com.example.campbnb.controller.MessageController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.NotificationCommand
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.destinations.CorrespondenceDestination
import com.example.campbnb.destinations.InvitationDestination
import com.example.campbnb.models.BookingCardData
import com.example.campbnb.models.ReservationStatus
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.inbox.CorrespondenceTabs
import com.example.campbnb.ui.components.unresolvableProfileLabel
import com.example.campbnb.ui.screens.BookingTab
import com.example.campbnb.ui.screens.BookingsScreen
import com.example.campbnb.ui.screens.CampsiteCardData
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryEntryStatus
import com.example.campbnb.models.WaitingListEntry
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.ReservationController
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.Availability
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.campbnb.ui.lottery.DateRangePickerModal
import com.example.campbnb.ui.lottery.fromPickerMillis
import java.time.LocalDate
import androidx.compose.material3.Text


@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.CorrespondenceGraph(
    // Whether the view the app is currently in acts as an owner: asked again each time a
    // screen draws, because the live view setting changes while the app is running and
    // decides the tabs a viewer reaches. The signed-in role does not: an entrant never
    // reaches the sent notices, and an owner who is being viewed as an entrant does not
    // either.
    ownerViewActive: () -> Boolean,
    userId: String,

    userRepo: UserRepository,
    notifRepo: NotificationRepository,
    reservationRepo: ReservationRepository,
    campsiteRepo: CampsiteRepository,

    userControl: UserController,
    notifControl: NotificationController,
    msgControl: MessageController,

    userModel: UserModel,

    onNavBack: () -> Unit,
    // The notice bridges run on the same navigation that [navToNotif] and [navToReservations]
    // use, so the pop that clears a bridge's own frame must be asked of that same navigation —
    // under the home bar the back CONTROLS move on the bar's graph while the bridge's frame
    // sits on the app's, and one callback cannot serve both.
    popNoticeFrame: () -> Unit = onNavBack,
    navToNotif: (screen: Screen) -> Unit,
    navToReservations: () -> Unit,
    navToLotteryMon: () -> Unit,
    goToReservations: () -> Unit,

    toastGen: (String, Int) -> Unit,

    // How a profile reference the sent listing cannot look up is drawn. Only a viewer
    // whose view may read raw database keys gets the key, and that is the caller's to
    // say, since only it knows the view the app is in.
    unresolvedProfileText: (String) -> String = unresolvableProfileLabel,
    // Only the home-bar mount passes these. Under the bar a chat is not a place the app
    // navigates to: a pushed bridge frame draws the messages tab again, so the back press
    // that clears it reads as a press that did nothing — the reported extra press. The bar
    // instead opens the chat as state in the tab the notice was tapped from, and closes it
    // again when the bar enters the tab.
    openChatHere: ((String?) -> Unit)? = null,
    chatOpenFor: () -> String? = { null },
) {
    // Removing one of their own notices: the record is gone rather than marked, so neither
    // the inbox nor a log lists it again. A viewer who cannot reach the store is told.
    // The removal answers with whether the store confirmed it, which is what lets a log
    // re-read itself once the entry really is gone.
    val removeNotice: (String, (removed: Boolean) -> Unit) -> Unit = { id, onRemoved ->
        notifRepo.run(
            NotificationCommand.Delete(
                notificationId = id,
                onDone = { result ->
                    if (result.isFailure) {
                        val reason = result.exceptionOrNull()?.message ?: "the notice could not be removed"
                        toastGen(
                            "Could not remove that notice: $reason",
                            Toast.LENGTH_SHORT
                        )
                        onRemoved(false)
                    } else {
                        onRemoved(true)
                    }
                }
            )
        )
    }

    navigation<NavGraphNodes.CorrespondenceGraph>(startDestination = Screen.Notifications) {
        composable<Screen.Notifications> {
            // Notification and message destinations. Tapping a notification opens the notices a
            // viewer received: an owner's own list is the sent one, so their received notices
            // live under the Received tab, while every other viewer has a single notices tab.
            CorrespondenceDestination(
                openTab = if (ownerViewActive()) CorrespondenceTabs.RECEIVED_TAB else CorrespondenceTabs.NOTIFICATIONS_TAB,
                onNavBack = onNavBack,
                ownerViewActive = ownerViewActive,
                userId = userId,
                repo = notifRepo,
                notifController = notifControl,
                msgController = msgControl,
                onNavToNotif = navToNotif,
                deleteNotice = removeNotice,
                unresolvedProfileText = unresolvedProfileText,
                openChatHere = openChatHere,
                chatOpenFor = chatOpenFor
            )
        }

        composable<Screen.Bookings> {
            val lotteryRepo = remember { LotteryRepository() }

            val lotteryModel = remember { LotteryModel() }

            val lotteryController = remember {
                LotteryController(
                    lotteryModel,
                    lotteryRepo,
                    Database.Lotteries.readEngine(),
                    notifControl
                )
            }

            val availability = remember {
                Availability(Database.Campsites.readEngine())
            }

            var bookingsRefreshKey by remember(userId) {
                mutableIntStateOf(0)
            }

            val reservationController = remember {
                ReservationController(
                    lotteryController,
                    availability,
                    reservationRepo,
                    notifControl
                )
            }

            var showBookingDatePicker by remember(userId) {
                mutableStateOf(false)
            }

            var selectedDateRangeText by remember(userId) {
                mutableStateOf("")
            }

            var selectedBookingForDates by remember(userId) {
                mutableStateOf<BookingCardData?>(null)
            }

            var availableBookingDates by remember(userId) {
                mutableStateOf<Set<String>>(emptySet())
            }

            var selectedBookingDates by remember(userId) {
                mutableStateOf<Set<String>>(emptySet())
            }

            var selectedBookingOwnerId by remember(userId) {
                mutableStateOf("")
            }

            var bookingGuestCount by remember(userId) {
                mutableStateOf("1")
            }

            var bookingPoliciesAccepted by remember(userId) {
                mutableStateOf(false)
            }

            var bookingRulesAccepted by remember(userId) {
                mutableStateOf(false)
            }

            var dateSelectionError by remember(userId) {
                mutableStateOf<String?>(null)
            }

            var isLoadingAvailableDates by remember(userId) {
                mutableStateOf(false)
            }

            var pastBookings by remember(userId) {
                mutableStateOf<List<BookingCardData>>(emptyList())
            }

            var lotteriesForBookings by remember(userId) {
                mutableStateOf<List<Lottery>>(emptyList())
            }

            var isCreatingReservation by remember(userId) {
                mutableStateOf(false)
            }

            var pendingBookings by remember(userId) {
                mutableStateOf<List<BookingCardData>>(emptyList())
            }

            var confirmedBookings by remember(userId) {
                mutableStateOf<List<BookingCardData>>(emptyList())
            }

            var selectedBookingTab by remember {
                mutableStateOf(BookingTab.CONFIRMED)
            }

            LaunchedEffect(userId, bookingsRefreshKey) {
                if (userId.isBlank()) {
                    toastGen("Unable to load bookings: user ID is missing.", Toast.LENGTH_LONG)
                    return@LaunchedEffect
                }

                // Load the entrant's reservations first.
                reservationRepo.getForEntrant(
                    entrantId = userId,
                    onSuccess = { reservations ->

                        // PENDING reservations are newly created reservations.
                        // ACTIVE reservations are reservations that have started.
                        val confirmedReservations = reservations.filter {
                            it.status == ReservationStatus.PENDING ||
                                    it.status == ReservationStatus.ACTIVE
                        }

                        val pastReservations = reservations.filter {
                            it.status == ReservationStatus.PAST
                        }

                        campsiteRepo.getAll(
                            onSuccess = { allCampsites ->

                                confirmedBookings = confirmedReservations.map { reservation ->
                                    val campsiteName = allCampsites
                                        .firstOrNull {
                                            it.id == reservation.campsiteId
                                        }
                                        ?.campName ?: "Campsite"

                                    BookingCardData(
                                        id = reservation.lotteryEntryId,
                                        lotteryId = "",
                                        lotteryEntryId = reservation.lotteryEntryId,
                                        campsiteId = reservation.campsiteId,
                                        campsiteNames = listOf(campsiteName),
                                        status = "Confirmed",
                                        startDate = reservation.startDate,
                                        endDate = reservation.endDate,
                                        guests = reservation.guests
                                    )
                                }

                                pastBookings = pastReservations.map { reservation ->
                                    val campsiteName = allCampsites
                                        .firstOrNull {
                                            it.id == reservation.campsiteId
                                        }
                                        ?.campName ?: "Campsite"

                                    BookingCardData(
                                        id = reservation.lotteryEntryId,
                                        lotteryId = "",
                                        lotteryEntryId = reservation.lotteryEntryId,
                                        campsiteId = reservation.campsiteId,
                                        campsiteNames = listOf(campsiteName),
                                        status = "Past",
                                        startDate = reservation.startDate,
                                        endDate = reservation.endDate,
                                        guests = reservation.guests
                                    )
                                }

                                // Load lottery information so each waiting-list entry
                                // can be displayed with its campsite name.
                                lotteryRepo.getAllLotteries(
                                    onSuccess = { allLotteries ->
                                        lotteriesForBookings = allLotteries
                                        lotteryRepo.getEntriesByUser(
                                            userId = userId,
                                            onSuccess = { entries ->

                                                val relevantEntries = entries.filter {
                                                    it.status == LotteryEntryStatus.WAITING_FOR_DRAW ||
                                                            it.status == LotteryEntryStatus.WON_AWAITING_RESPONSE ||
                                                            it.status == LotteryEntryStatus.ACCEPTED
                                                }

                                                val pendingCards =
                                                    mutableListOf<BookingCardData>()

                                                var completedEntries = 0

                                                fun finishEntry(card: BookingCardData?) {
                                                    if (card != null) {
                                                        pendingCards.add(card)
                                                    }

                                                    completedEntries++

                                                    if (completedEntries == relevantEntries.size) {
                                                        pendingBookings = pendingCards.toList()
                                                    }
                                                }

                                                if (relevantEntries.isEmpty()) {
                                                    pendingBookings = emptyList()
                                                    return@getEntriesByUser
                                                }

                                                relevantEntries.forEach { entry ->

                                                    val lottery = allLotteries.firstOrNull {
                                                        it.id == entry.lotteryId
                                                    }

                                                    if (lottery == null) {
                                                        finishEntry(null)
                                                        return@forEach
                                                    }

                                                    val campsiteNames = lottery.campsiteIds
                                                        .mapNotNull { campsiteId ->
                                                            allCampsites.firstOrNull {
                                                                it.id == campsiteId
                                                            }?.campName
                                                        }
                                                        .ifEmpty {
                                                            listOf("Campsite")
                                                        }

                                                    val cardStatus = when (entry.status) {
                                                        LotteryEntryStatus.WAITING_FOR_DRAW ->
                                                            "Waiting for draw"

                                                        LotteryEntryStatus.WON_AWAITING_RESPONSE ->
                                                            "Won — response needed"

                                                        LotteryEntryStatus.ACCEPTED ->
                                                            "Select dates"

                                                        else -> null
                                                    }

                                                    if (cardStatus == null) {
                                                        finishEntry(null)
                                                        return@forEach
                                                    }

                                                    val card = BookingCardData(
                                                        id = entry.id,
                                                        lotteryId = entry.lotteryId,
                                                        lotteryEntryId = entry.id,
                                                        campsiteId = lottery.campsiteIds.firstOrNull()
                                                            .orEmpty(),
                                                        campsiteNames = campsiteNames,
                                                        status = cardStatus,
                                                        canSelectDates =
                                                            entry.status == LotteryEntryStatus.ACCEPTED,
                                                        maxBookingDays = lottery.maxBookingDays
                                                    )

                                                    if (entry.status == LotteryEntryStatus.ACCEPTED) {
                                                        // An accepted win should remain in Pending
                                                        // only until a reservation has been created.
                                                        reservationRepo.getByLotteryEntry(
                                                            lotteryEntryId = entry.id,
                                                            onSuccess = { reservation ->
                                                                if (reservation == null) {
                                                                    finishEntry(card)
                                                                } else {
                                                                    finishEntry(null)
                                                                }
                                                            },
                                                            onFailure = { error ->
                                                                toastGen(
                                                                    "Could not check an existing reservation: ${error.message}",
                                                                    Toast.LENGTH_LONG
                                                                )
                                                                finishEntry(null)
                                                            }
                                                        )
                                                    } else {
                                                        finishEntry(card)
                                                    }
                                                }
                                            },
                                            onFailure = { error ->
                                                toastGen(
                                                    "Could not load your lottery entries: ${error.message}",
                                                    Toast.LENGTH_LONG
                                                )
                                            }
                                        )
                                    },
                                    onFailure = { error ->
                                        toastGen(
                                            "Could not load lotteries: ${error.message}",
                                            Toast.LENGTH_LONG
                                        )
                                    }
                                )
                            },
                            onFailure = { error ->
                                toastGen(
                                    "Could not load campsite details: ${error.message}",
                                    Toast.LENGTH_LONG
                                )
                            }
                        )
                    },
                    onFailure = { error ->
                        toastGen(
                            "Could not load bookings: ${error.message}",
                            Toast.LENGTH_LONG
                        )
                    }
                )
            }

            BookingsScreen(
                confirmedBookings = confirmedBookings,
                pendingBookings = pendingBookings,
                selectedTab = selectedBookingTab,
                onTabSelected = { selectedBookingTab = it },

                onBookingClick = { booking ->
                    // Keep your existing booking-click behavior here.
                },

                onMessageOwnerClick = { booking ->
                    if (booking.id.isNotBlank()) {
                        navToNotif(
                            Screen.ChatReservationEntry(
                                reservationID = booking.id
                            )
                        )
                    } else {
                        toastGen(
                            "Unable to open chat: booking ID is missing.",
                            Toast.LENGTH_SHORT
                        )
                    }
                },

                onAcceptWin = { booking ->
                    lotteryController.acceptLotteryWin(
                        lotteryId = booking.lotteryId,
                        userId = userId,
                        onSuccess = {
                            toastGen(
                                "Lottery win accepted! You can now select your dates.",
                                Toast.LENGTH_LONG
                            )
                            bookingsRefreshKey++
                        },
                        onFailure = { error ->
                            toastGen(
                                "Could not accept lottery win: ${error.message}",
                                Toast.LENGTH_LONG
                            )
                        }
                    )
                },

                onDeclineWin = { booking ->
                    lotteryController.declineLotteryWin(
                        lotteryId = booking.lotteryId,
                        userId = userId,
                        onSuccess = {
                            toastGen("Lottery win declined.", Toast.LENGTH_SHORT)
                            bookingsRefreshKey++
                        },
                        onFailure = { error ->
                            toastGen(
                                "Could not decline lottery win: ${error.message}",
                                Toast.LENGTH_LONG
                            )
                        }
                    )
                },

                onSelectDates = { booking ->

                    val lottery = lotteriesForBookings.firstOrNull {
                        it.id == booking.lotteryId
                    }

                    if (lottery == null) {
                        toastGen(
                            "Could not find the lottery for this booking.",
                            Toast.LENGTH_LONG
                        )
                    } else {
                        selectedBookingForDates = booking
                        selectedBookingOwnerId = lottery.ownerId

                        availableBookingDates = emptySet()
                        selectedBookingDates = emptySet()
                        selectedDateRangeText = ""
                        bookingGuestCount = "1"
                        bookingPoliciesAccepted = false
                        bookingRulesAccepted = false
                        dateSelectionError = null

                        isLoadingAvailableDates = true

                        reservationController.getAvailableDates(
                            lotteryId = booking.lotteryId,
                            lotteryEntryId = booking.lotteryEntryId,
                            entrantId = userId,
                            campsiteId = booking.campsiteId,

                            onSuccess = { dates ->
                                isLoadingAvailableDates = false
                                availableBookingDates = dates

                                if (dates.isEmpty()) {
                                    dateSelectionError =
                                        "There are currently no available dates for this campsite."
                                }
                            },

                            onFailure = { error ->
                                isLoadingAvailableDates = false
                                dateSelectionError =
                                    "Could not load available dates: ${error.message}"
                            }
                        )
                    }
                },
                pastBookings = pastBookings
            )

            val booking = selectedBookingForDates

            if (booking != null) {

                AlertDialog(
                    onDismissRequest = {
                        if (!isCreatingReservation) {
                            selectedBookingForDates = null
                        }
                    },
                    title = {
                        Text("Select Reservation Dates")
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Choose up to ${booking.maxBookingDays} consecutive days."
                            )

                            if (isLoadingAvailableDates) {
                                Text("Loading available dates...")
                            } else {
                                Text(
                                    "Available dates: ${availableBookingDates.size}"
                                )

                                OutlinedTextField(
                                    value = selectedDateRangeText,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = {
                                        Text("Reservation period")
                                    },
                                    placeholder = {
                                        Text("Select your dates")
                                    },
                                    trailingIcon = {
                                        TextButton(
                                            enabled = availableBookingDates.isNotEmpty(),
                                            onClick = {
                                                showBookingDatePicker = true
                                            }
                                        ) {
                                            Text("Choose")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            OutlinedTextField(
                                value = bookingGuestCount,
                                onValueChange = { value ->
                                    if (value.all { it.isDigit() }) {
                                        bookingGuestCount = value
                                        dateSelectionError = null
                                    }
                                },
                                label = {
                                    Text("Number of guests")
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = bookingPoliciesAccepted,
                                    onCheckedChange = {
                                        bookingPoliciesAccepted = it
                                    }
                                )

                                Text("I acknowledge the reservation and refund policies.")
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = bookingRulesAccepted,
                                    onCheckedChange = {
                                        bookingRulesAccepted = it
                                    }
                                )

                                Text("I agree to the campsite rules.")
                            }

                            if (dateSelectionError != null) {
                                Text(
                                    text = dateSelectionError!!,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !isLoadingAvailableDates &&
                                    !isCreatingReservation &&
                                    selectedBookingDates.isNotEmpty() &&
                                    bookingGuestCount.toIntOrNull() != null &&
                                    bookingGuestCount.toIntOrNull()!! > 0 &&
                                    bookingPoliciesAccepted &&
                                    bookingRulesAccepted,
                            onClick = {
                                val guests = bookingGuestCount.toIntOrNull()

                                if (guests == null || guests < 1) {
                                    dateSelectionError = "Enter a valid guest count."
                                    return@TextButton
                                }

                                if (selectedBookingOwnerId.isBlank()) {
                                    dateSelectionError = "Campsite owner information is missing."
                                    return@TextButton
                                }

                                if (selectedBookingDates.size > booking.maxBookingDays) {
                                    dateSelectionError =
                                        "You can select at most ${booking.maxBookingDays} days."
                                    return@TextButton
                                }

                                reservationController.confirmPolicies(
                                    booking.lotteryEntryId
                                )

                                isCreatingReservation = true

                                reservationController.createReservation(
                                    lotteryId = booking.lotteryId,
                                    lotteryEntryId = booking.lotteryEntryId,
                                    entrantId = userId,
                                    ownerId = selectedBookingOwnerId,
                                    campsiteId = booking.campsiteId,
                                    dates = selectedBookingDates,
                                    onSuccess = {
                                        isCreatingReservation = false

                                        toastGen(
                                            "Your reservation has been created!",
                                            Toast.LENGTH_LONG
                                        )

                                        selectedBookingForDates = null
                                        showBookingDatePicker = false
                                        bookingsRefreshKey++
                                    },
                                    onFailure = { error ->
                                        isCreatingReservation = false

                                        dateSelectionError = error.message
                                            ?: "Could not create your reservation."

                                        toastGen(
                                            "Could not create reservation: ${error.message}",
                                            Toast.LENGTH_LONG
                                        )
                                    },
                                    guests = guests,
                                    refundRecorded = bookingPoliciesAccepted,
                                    rules = bookingRulesAccepted
                                )
                            }
                        ) {
                            Text(
                                if (isCreatingReservation) {
                                    "Creating..."
                                } else {
                                    "Confirm Reservation"
                                }
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !isCreatingReservation,
                            onClick = {
                                selectedBookingForDates = null
                                showBookingDatePicker = false
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showBookingDatePicker) {
                DateRangePickerModal(
                    onDateRangeSelected = { (startMillis, endMillis) ->

                        if (startMillis == null || endMillis == null) {
                            dateSelectionError = "Please select both a start and end date."
                        } else {
                            try {
                                val startDate = LocalDate.parse(
                                    fromPickerMillis(startMillis)
                                )

                                val endDate = LocalDate.parse(
                                    fromPickerMillis(endMillis)
                                )

                                val numberOfDays =
                                    java.time.temporal.ChronoUnit.DAYS
                                        .between(startDate, endDate) + 1

                                if (booking != null) {
                                    if (numberOfDays > booking.maxBookingDays) {
                                        dateSelectionError =
                                            "You can select at most ${booking.maxBookingDays} days."
                                    } else {
                                        val requestedDates = mutableSetOf<String>()
                                        var currentDate = startDate

                                        while (!currentDate.isAfter(endDate)) {
                                            requestedDates.add(currentDate.toString())
                                            currentDate = currentDate.plusDays(1)
                                        }

                                        val unavailableDates =
                                            requestedDates - availableBookingDates

                                        if (unavailableDates.isNotEmpty()) {
                                            dateSelectionError =
                                                "Some dates in that range are unavailable. Please choose another range."
                                        } else {
                                            selectedBookingDates = requestedDates
                                            selectedDateRangeText =
                                                "$startDate to $endDate"
                                            dateSelectionError = null
                                        }
                                    }
                                }
                            } catch (error: Exception) {
                                dateSelectionError =
                                    "Could not read the selected dates."
                            }
                        }
                    },
                    onDismiss = {
                        showBookingDatePicker = false
                    }
                )
            }
        }

        composable<Screen.Messages> {
            // Entering the tab closes whatever chat the last tap left open, so a fresh
            // visit shows the list and not a chat left over from before. This runs once
            // per time the frame opens, not on every draw, so a chat a tap opens while
            // the frame is up stays open.
            if (openChatHere != null) {
                LaunchedEffect(Unit) {
                    openChatHere(null)
                }
            }
            CorrespondenceDestination(
                openTab = CorrespondenceTabs.MESSAGES_TAB,
                onNavBack = onNavBack,
                ownerViewActive = ownerViewActive,
                userId = userId,
                repo = notifRepo,
                notifController = notifControl,
                msgController = msgControl,
                onNavToNotif = navToNotif,
                deleteNotice = removeNotice,
                unresolvedProfileText = unresolvedProfileText,
                openChatHere = openChatHere,
                chatOpenFor = chatOpenFor
            )
        }

        composable<Screen.Inbox> {
            CorrespondenceDestination(
                openTab = CorrespondenceTabs.NOTIFICATIONS_TAB,
                onNavBack = onNavBack,
                ownerViewActive = ownerViewActive,
                userId = userId,
                repo = notifRepo,
                notifController = notifControl,
                msgController = msgControl,
                onNavToNotif = navToNotif,
                deleteNotice = removeNotice,
                unresolvedProfileText = unresolvedProfileText,
                openChatHere = openChatHere,
                chatOpenFor = chatOpenFor
            )
        }


        // Tapping a RESERVATION-type notification opens the reservations view. The
        // bridge pops its own frame before the push: a frame left on the stack — even
        // this invisible typed one — would otherwise keep it under the reservations
        // screen, and the first back press would land on it instead of the view the
        // notification was tapped from. The pop is asked of the SAME navigation the
        // pending frame was pushed onto ([navToNotif]'s), not of [onNavBack]: under
        // the home bar the back controls move on the bar's own graph, and popping
        // there left the typed frame on the app's stack between Home and the
        // reservations view — the reported back-button extra press. Naming the live
        // Home frame with a typed popUpTo could not work either (Home is a
        // parameterized route; the pop matched nothing and emptied the stack).
        composable<Screen.NotifReservations> {
            LaunchedEffect(Unit) {
                popNoticeFrame()
                navToReservations()
            }
        }


        // Taps from WIN and INVITE notifications share the booking route. The
        // destination below opens the invitation panel only while the invitation is
        // still pending for the current viewer, and otherwise keeps navigating to the
        // reservations screen (there is still no separate booking screen).
        composable<Screen.BookingEntry> { entry ->
            val lotteryID = entry.toRoute<Screen.BookingEntry>().lotteryID
            InvitationDestination(
                viewerId = userId,
                notificationController = notifControl,
                navToLotteryMon = navToLotteryMon,
                navToReservations = navToReservations,
                goToReservations = goToReservations,
                lotteryId = lotteryID
            )
        }

        // Tapping a MESSAGE notification opens that reservation's chat.
        composable<Screen.ChatReservationEntry> { entry ->
            val reservationID = entry.toRoute<Screen.ChatReservationEntry>().reservationID
            CorrespondenceDestination(
                openTab = CorrespondenceTabs.MESSAGES_TAB,
                onNavBack = onNavBack,
                ownerViewActive = ownerViewActive,
                userId = userId,
                repo = notifRepo,
                notifController = notifControl,
                msgController = msgControl,
                onNavToNotif = navToNotif,
                openReservationId = reservationID,
                deleteNotice = removeNotice,
                unresolvedProfileText = unresolvedProfileText
            )
        }
    }
}