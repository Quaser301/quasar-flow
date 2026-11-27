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
                unresolvedProfileText = unresolvedProfileText
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
                },

                // opens the conversation associated with the selected booking
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
                }
            )
        }

        composable<Screen.Messages> {
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
                unresolvedProfileText = unresolvedProfileText
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
                unresolvedProfileText = unresolvedProfileText
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