package com.example.campbnb.graphs

import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.destinations.CorrespondenceDestination
import com.example.campbnb.destinations.InvitationDestination
import com.example.campbnb.models.BookingCardData
import com.example.campbnb.models.ReservationStatus
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.inbox.CorrespondenceTabs
import com.example.campbnb.ui.screens.BookingTab
import com.example.campbnb.ui.screens.BookingsScreen
import com.example.campbnb.ui.screens.CampsiteCardData


fun NavGraphBuilder.CorrespondenceGraph(
    // Asked again each time a screen draws, rather than read once when this graph is
    // built: the sign-in runs after the app's graph exists, so a value taken here would
    // name the wrong correspondence shape for every user who signs in later.
    currentUserIsOwner: () -> Boolean,
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
    navToNotif: (screen: Screen) -> Unit,
    navToReservations: () -> Unit,
    navToLotteryMon: () -> Unit,
    goToReservations: () -> Unit,

    toastGen: (String, Int) -> Unit,

) {
    navigation<NavGraphNodes.CorrespondenceGraph>(startDestination = Screen.Notifications) {
        composable<Screen.Notifications> {
            // Notification and message destinations. Tapping a notification opens the notices a
            // viewer received: an owner's own list is the sent one, so their received notices
            // live under the Received tab, while every other viewer has a single notices tab.
            CorrespondenceDestination(
                openTab = if (currentUserIsOwner()) CorrespondenceTabs.RECEIVED_TAB else CorrespondenceTabs.NOTIFICATIONS_TAB,
                onNavBack = onNavBack,
                currentUserIsOwner = currentUserIsOwner,
                userId = userId,
                repo = notifRepo,
                notifController = notifControl,
                msgController = msgControl,
                onNavToNotif = navToNotif
            )
        }

        composable<Screen.Bookings> {

            var campsiteCards by remember {
                mutableStateOf<List<CampsiteCardData>>(emptyList())
            }
            var pendingBookings by remember {
                mutableStateOf<List<BookingCardData>>(emptyList())
            }

            var confirmedBookings by remember(userId) {
                mutableStateOf<List<BookingCardData>>(emptyList())
            }

            var selectedBookingTab by remember {
                mutableStateOf(BookingTab.CONFIRMED)
            }

            LaunchedEffect(userId) {
                reservationRepo.getForEntrant(
                    entrantId = userId,
                    onSuccess = { reservations ->

                        val activeReservations = reservations.filter {
                            it.status == ReservationStatus.ACTIVE
                        }

                        campsiteRepo.getAll(
                            onSuccess = { allCampsites ->

                                confirmedBookings = activeReservations.map { reservation ->
                                    val campsiteName = allCampsites
                                        .firstOrNull { it.id == reservation.campsiteId }
                                        ?.campName ?: "Campsite"

                                    BookingCardData(
                                        id = reservation.lotteryEntryId,
                                        lotteryId = "",
                                        campsiteNames = listOf(campsiteName),
                                        status = "Confirmed",
                                        startDate = reservation.startDate,
                                        endDate = reservation.endDate,
                                        guests = reservation.guests
                                    )
                                }
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

            LaunchedEffect(Unit) {
                campsiteRepo.getAll(
                    onSuccess = { campsites ->
                        campsiteCards = campsites.map { campsite ->
                            CampsiteCardData(
                                id = campsite.id,
                                name = campsite.campName,
                                location = campsite.campLoc,
                                maxOccupancy = campsite.maxOccupancy
                            )
                        }
                    },
                    onFailure = { error ->
                        toastGen(
                            "Could not load campsites: ${error.message}",
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
                onBookingClick = { _ ->
                    // Booking details navigation can be added here later.
                }
            )
        }

        composable<Screen.Messages> {
            CorrespondenceDestination(
                openTab = CorrespondenceTabs.MESSAGES_TAB,
                onNavBack = onNavBack,
                currentUserIsOwner = currentUserIsOwner,
                userId = userId,
                repo = notifRepo,
                notifController = notifControl,
                msgController = msgControl,
                onNavToNotif = navToNotif
            )
        }

        composable<Screen.Inbox> {
            CorrespondenceDestination(
                openTab = CorrespondenceTabs.NOTIFICATIONS_TAB,
                onNavBack = onNavBack,
                currentUserIsOwner = currentUserIsOwner,
                userId = userId,
                repo = notifRepo,
                notifController = notifControl,
                msgController = msgControl,
                onNavToNotif = navToNotif
            )
        }


        // Tapping a RESERVATION-type notification opens the reservations view. The
        // bridge pops its own frame before the push: a frame left on the stack — even
        // this invisible typed one — would otherwise keep it under the reservations
        // screen, and the first back press would land on it instead of the view the
        // notification was tapped from. Naming the live Home frame with a typed popUpTo
        // could not work (Home is a parameterized route; the pop matched nothing,
        // emptied the stack and left the reservations view as the only entry — reported
        // regression 7's reservations half).
        composable<Screen.NotifReservations> {
            LaunchedEffect(Unit) {
                onNavBack()
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
                currentUserIsOwner = currentUserIsOwner,
                userId = userId,
                repo = notifRepo,
                notifController = notifControl,
                msgController = msgControl,
                onNavToNotif = navToNotif,
                openReservationId = reservationID
            )
        }
    }
}