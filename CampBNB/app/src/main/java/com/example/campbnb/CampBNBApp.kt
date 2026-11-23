package com.example.campbnb

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.campbnb.ui.screens.ReservationScreen
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.ReservationFilter
import com.example.campbnb.models.ReservationStatus
import com.example.campbnb.models.BookingCardData
import com.example.campbnb.ui.screens.BookingsScreen
import com.example.campbnb.ui.screens.BookingTab
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.Notification
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.ui.screens.DeviceSignInScreen
import com.example.campbnb.notifications.NotificationRoutes
import com.example.campbnb.ui.screens.CampsiteCardData
import com.example.campbnb.ui.screens.ExploreScreen
import com.example.campbnb.ui.screens.HomeDestination
import com.example.campbnb.ui.screens.HomeScreen
import com.example.campbnb.ui.screens.PlaceholderTab
import com.example.campbnb.ui.screens.RoleSelectScreen
import com.example.campbnb.ui.screens.SignUpScreen
import com.example.campbnb.ui.screens.StartScreen
import com.example.campbnb.ui.admin.AdminProfilesScreen
import com.example.campbnb.ui.campsite.CampsiteMonitorActivity
import com.example.campbnb.ui.campsite.CampsiteRatingSection
import com.example.campbnb.ui.campsite.CampsiteRegistrationForm
import com.example.campbnb.ui.campsite.LotteryJoinSection
import com.example.campbnb.ui.campsite.lotteryButtonText
import com.example.campbnb.ui.campsite.toCardList
import com.example.campbnb.ui.components.GradientButton
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.details.CampsiteDetails
import com.example.campbnb.ui.details.CampsiteDetailsScreen
import com.example.campbnb.ui.inbox.CorrespondenceScreen
import com.example.campbnb.ui.inbox.MessagesScreen
import com.example.campbnb.ui.inbox.NotificationInboxScreen
import com.example.campbnb.ui.inbox.NotificationViewModel
import com.example.campbnb.ui.lottery.LotteryDetailsActivity
import com.example.campbnb.ui.lottery.LotteryEditActivity
import com.example.campbnb.ui.lottery.LotteryMonitorActivity
import com.example.campbnb.ui.lottery.LotteryWaitlistActivity
import com.example.campbnb.ui.notification.NotificationLogScreen
import com.example.campbnb.ui.search.SearchScreen
import com.example.campbnb.ui.settings.SettingsScreen
import com.example.campbnb.ui.shared.observeModel
import com.example.campbnb.ui.shared.roleFor
import com.example.campbnb.ui.shared.toUserRole
import com.example.campbnb.ui.shared.viewFor
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.MessageViewModel
import com.example.campbnb.viewModels.simpleViewModelFactory
import kotlin.collections.emptyList

@Composable
fun CampBnbApp(
    deps: Dependencies
) {

    // needed for nav obviously
    val nav = rememberNavController()
    // needed for Toasts
    val context = LocalContext.current


    // The lottery the owner picked on the lottery list, shared by the lottery edit,
    // details and waitlist screens.
    var selectedLottery by remember { mutableStateOf<Lottery?>(null) }

    NavHost(navController = nav, startDestination = Screen.Start) {

        // Home (Explore plus the role's bottom bar)
        composable<Screen.Home> { entry ->
            val route = entry.toRoute<Screen.Home>()
            val role = route.role

            val context = LocalContext.current

            var campsiteCards by remember {
                mutableStateOf<List<CampsiteCardData>>(emptyList())
            }
            var pendingBookings by remember {
                mutableStateOf<List<BookingCardData>>(emptyList())
            }

            var confirmedBookings by remember(deps.deviceID) {
                mutableStateOf<List<BookingCardData>>(emptyList())
            }

            var selectedBookingTab by remember {
                mutableStateOf(BookingTab.CONFIRMED)
            }

            LaunchedEffect(deps.deviceID) {
                deps.reservationRepository.getForEntrant(
                    entrantId = deps.deviceID,
                    onSuccess = { reservations ->

                        val activeReservations = reservations.filter {
                            it.status == ReservationStatus.ACTIVE
                        }

                        deps.campsiteRepository.getAll(
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
                                Toast.makeText(
                                    context,
                                    "Could not load campsite details: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            context,
                            "Could not load bookings: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }

            LaunchedEffect(Unit) {
                deps.campsiteRepository.getAll(
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
                        Toast.makeText(
                            context,
                            "Could not load campsites: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }

            HomeScreen(
                role = role,
                initialTab = route.tab,
                destinationContent = { destination ->
                    when (destination) {
                        // NOTE: do NOT use a direct nav call to switch to different screens.
                        // That WILL repeatedly invoke that nav call and redraw your screen
                        // INFINITELY.

                        HomeDestination.BOOKINGS -> {
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

                        HomeDestination.MESSAGES -> {
                            CorrespondenceDestination(
                                initialTab = 1,
                                onNavBack = { nav.navigateUp() },
                                navToNotification = { NotificationRoutes.routeFor(it) },
                                deps = deps
                            )
                        }

                        HomeDestination.INBOX -> {
                            CorrespondenceDestination(
                                initialTab = 0,
                                onNavBack = { nav.navigateUp() },
                                navToNotification = { NotificationRoutes.routeFor(it) },
                                deps = deps
                            )
                        }

                        HomeDestination.ADMIN_PROFILE_VIEW -> {
                            LaunchedEffect(Unit) {
                                deps.userController.loadAllUsers()
                            }
                            AdminProfilesScreen(
                                { nav.goHome(role) },
                                deps.userModel
                            )
                        }

                        HomeDestination.ADMIN_NOTIFICATION_VIEW -> {
                            NotificationLogScreen(
                                controller = deps.notificationController,
                                returnFunc = { nav.goHome(role) }
                            )
                        }

                        else -> {
                            PlaceholderTab(destination)
                        }
                    }
                },
                appViewSettings = deps.appViewSettings.toAppViewSetting()
            )
        }


        // Campsite monitor (owner's list of campsites)
        composable<Screen.CampsiteMonitor> { entry ->
            val route = entry.toRoute<Screen.CampsiteMonitorActivity>()
            val activity = remember {
                CampsiteMonitorActivity(
                    // The previous screen is Home, which picks its tabs from the current view
                    goHome = { nav.navigateUp() },
                    goBack = { nav.navigateUp() },
                    goToRegisterCampsite = { nav.navigate(Screen.CampsiteEditActivity) },
                    goToCampsiteDetails = { campsite ->
                        nav.navigate(
                            Screen.CampsiteDetailsScreen(
                                route.role,
                                campsite.id
                            )
                        )
                    },
                    goToEditCampsite = { campsite ->
                        nav.navigate(Screen.EditCampsite(campsite.id))
                    },
                    appViewSettings = deps.appViewSettings.toAppViewSetting(),
                    deviceID = deps.deviceID
                )
            }
            val model = remember { CampsiteModel() }
            val controller = remember { CampsiteController(
                model,
                deps.campsiteRepository
                )
            }
            activity.CampsiteMonitor(model = model, controller = controller)
        }

        // Lottery waitlist
        composable<Screen.LotteryWaitlistActivity> {
            val activity = remember {
                LotteryWaitlistActivity(
                    { nav.navigate(Screen.Start) },
                    { nav.navigateUp() },
                    appViewSettings = deps.appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember {
                LotteryController(
                    model,
                    repo,
                    Database.Lotteries.readEngine()
                ) { System.currentTimeMillis() }
            }
            activity.LotteryWaitlist(
                model = model,
                controller = controller,
                selectedLottery = selectedLottery
            )
        }

        // Reservations
        composable<Screen.Reservations> {
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

            // Load only this owner's campsites.
            LaunchedEffect(deps.deviceID) {
                deps.campsiteRepository.getAll(
                    onSuccess = { allCampsites ->
                        val ownedCampsites = allCampsites.filter {
                            it.ownerId == deps.deviceID
                        }

                        campsites = ownedCampsites

                        // Automatically select the first campsite.
                        if (selectedCampsiteId == null ||
                            ownedCampsites.none { it.id == selectedCampsiteId }
                        ) {
                            selectedCampsiteId = ownedCampsites.firstOrNull()?.id
                        }
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            context,
                            "Could not load campsites: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }

            // Load reservations whenever the campsite or filter changes.
            LaunchedEffect(selectedCampsiteId, selectedFilter) {
                val campsiteId = selectedCampsiteId

                if (campsiteId == null) {
                    reservations = emptyList()
                } else {
                    deps.reservationRepository.run {
                        when (selectedFilter) {
                            ReservationFilter.ACTIVE -> getActive(
                                campsiteId,
                                onSuccess = { reservations = it },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        "Could not load reservations: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )

                            ReservationFilter.PAST -> getPast(
                                campsiteId,
                                onSuccess = { reservations = it },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        "Could not load reservations: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )

                            ReservationFilter.CANCELLED -> getCancelled(
                                campsiteId,
                                onSuccess = { reservations = it },
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
                onBack = {
                    nav.navigateUp()
                }
            )
        }

        composable<Screen.Settings> { entry ->
            // need to know our role so we can show
            // the relevant settings
            val role = entry.toRoute<Screen.Settings>().role
            SettingsScreen(
                { nav.navigateUp() },
                role,
                deps.appViewSettings
            )
        }

        composable<Screen.LotteryDetailsActivity> {
            val activity = remember {
                LotteryDetailsActivity(
                    returnHome = { nav.navigate(Screen.Start) },
                    navigateBackwards = { nav.navigateUp() },
                    onEditLottery = { _ -> nav.navigate(Screen.LotteryEditActivity) },
                    onViewWaitlist = { _ -> nav.navigate(Screen.LotteryWaitlistActivity) },
                    appViewSettings = deps.appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            activity.LotteryDetails(model = model, selectedLottery = selectedLottery)
        }

        // Notification and message destinations
        composable(NotificationRoutes.NOTIFICATIONS) {
            CorrespondenceDestination(
                initialTab = 0,
                onNavBack = { nav.navigateUp() },
                navToNotification = { NotificationRoutes.routeFor(it) },
                deps = deps
            )
        }
        composable(NotificationRoutes.MESSAGES) {
            CorrespondenceDestination(
                initialTab = 1,
                onNavBack = { nav.navigateUp() },
                navToNotification = { NotificationRoutes.routeFor(it) },
                deps = deps
            )
        }
        // Notification taps for reservations and lottery wins go to the reservations screen
        // (there is no separate booking screen yet).
        composable(NotificationRoutes.RESERVATIONS) {
            LaunchedEffect(Unit) {
                nav.navigate(Screen.Reservations) {
                    popUpTo(NotificationRoutes.RESERVATIONS) { inclusive = true }
                }
            }
        }
        composable("${NotificationRoutes.BOOKING}/{lotteryId}") {
            LaunchedEffect(Unit) {
                nav.navigate(Screen.Reservations) {
                    popUpTo("${NotificationRoutes.BOOKING}/{lotteryId}") { inclusive = true }
                }
            }
        }
        // Tapping a MESSAGE notification opens that reservation's chat.
        composable("${NotificationRoutes.CHAT}/{reservationId}") { entry ->
            CorrespondenceDestination(
                initialTab = 1,
                openReservationId = entry.arguments?.getString("reservationId"),
                onNavBack = { nav.navigateUp() },
                navToNotification = { nav.navigate(NotificationRoutes.routeFor(it)) },
                deps
            )
        }

    }

    LaunchedEffect(deps.pendingRoute) {
        if (!deps.pendingRoute.isNullOrBlank()) {
            nav.navigate(deps.pendingRoute!!)
            deps.onPendingRouteHandled()
        }
    }
}

/**
 * Goes to the home page on the given tab, clearing any screens opened on top of it.
 *
 * @param role The signed-in role, which decides the bottom bar.
 * @param tab Index of the bottom bar tab to open.
 */
private fun NavController.goHome(role: UserRole, tab: Int = 0) {
    navigate(Screen.Home(role = role, tab = tab)) {
        popUpTo<Screen.Home> { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * The Notifications / Messages screen, with its tabs.
 *
 * @param initialTab 0 opens Notifications, 1 opens Messages.
 * @param openReservationId If set, the Messages tab opens straight into this reservation's chat.
 */
@Composable
private fun CorrespondenceDestination(
    initialTab: Int,
    openReservationId: String? = null,
    onNavBack: () -> Unit,
    navToNotification: (Notification) -> Unit,
    deps: Dependencies
) {
    CorrespondenceScreen(
        initialTab = initialTab,
        notificationsContent = { NotificationsTab(deps, navToNotification) },
        messagesContent = { MessagesTab(openReservationId, deps) },
        onNavBack
    )
}

/** The notification inbox. viewModel() keeps the same inbox (and its Firestore listener) across recompositions. */
@Composable
private fun NotificationsTab(
    deps: Dependencies,
    navToNotification: (Notification) -> Unit
) {
    val inboxViewModel: NotificationViewModel = viewModel(
        key = "notification-inbox-${deps.deviceID}",
        factory = simpleViewModelFactory {
            NotificationViewModel(deps.notificationController, deps.deviceID)
        }
    )
    NotificationInboxScreen(viewModel = inboxViewModel, navToNotification)
}

/** The chat list and chat threads. [openReservationId] jumps straight into one chat. */
@Composable
private fun MessagesTab(openReservationId: String? = null, deps: Dependencies) {
    val userId = deps.deviceID
    val messageViewModel: MessageViewModel = viewModel(
        key = "message-inbox-$userId",
        factory = simpleViewModelFactory {
            MessageViewModel(deps.messageController, userId)
        }
    )
    LaunchedEffect(openReservationId) {
        if (!openReservationId.isNullOrBlank()) {
            messageViewModel.open(openReservationId)
        }
    }
    MessagesScreen(viewModel = messageViewModel, currentUserId = userId)
}

