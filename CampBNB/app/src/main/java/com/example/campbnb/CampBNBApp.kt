package com.example.campbnb

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.campbnb.Screen
import com.example.campbnb.graphs.exploreGraph
import com.example.campbnb.graphs.campsiteGraph
import com.example.campbnb.graphs.lotteryGraph
import com.example.campbnb.graphs.startupGraph
import com.example.campbnb.tabs.MyCampsitesTab
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.ReservationFilter
import com.example.campbnb.models.ReservationStatus
import com.example.campbnb.models.BookingCardData
import com.example.campbnb.ui.screens.BookingsScreen
import com.example.campbnb.ui.screens.BookingTab
import com.example.campbnb.ui.screens.ReservationScreen
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.SentNotificationsRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.Role
import com.example.campbnb.models.qrLinkRoute
import com.example.campbnb.models.qrOldCodeRoute
import com.example.campbnb.ui.screens.QrScannerScreen
import com.example.campbnb.ui.screens.QrOldCodeNoticeScreen
import com.example.campbnb.notifications.NotificationRoutes
import com.example.campbnb.ui.screens.CampsiteCardData
import com.example.campbnb.ui.screens.ExploreScreen
import com.example.campbnb.ui.screens.HomeScreen
import com.example.campbnb.ui.screens.PlaceholderTab
import com.example.campbnb.ui.screens.tabsFor
import com.example.campbnb.ui.admin.AdminProfilesScreen
import com.example.campbnb.ui.campsite.CampsiteMonitorActivity
import com.example.campbnb.ui.components.profileLabelOf
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.notification.NotificationLogScreen
import com.example.campbnb.ui.inbox.CorrespondenceScreen
import com.example.campbnb.ui.inbox.CorrespondenceTabs
import com.example.campbnb.ui.inbox.MessagesScreen
import com.example.campbnb.ui.inbox.NotificationInboxScreen
import com.example.campbnb.ui.inbox.NotificationViewModel
import com.example.campbnb.ui.settings.SettingsScreen
import com.example.campbnb.ui.shared.roleFor
import com.example.campbnb.ui.shared.toUserRole
import com.example.campbnb.viewModels.MessageViewModel
import com.example.campbnb.viewModels.simpleViewModelFactory
import com.example.campbnb.viewModels.LotterySelectedViewModel

@Composable
fun CampBnbApp(
    deps: Dependencies
) {

    // needed for nav obviously
    val nav = rememberNavController()
    // needed for Toasts
    val context = LocalContext.current

    // The role the app view says the user is in right now. Graph callbacks that run
    // outside one screen's route need it, the same derivation HomeScreen uses.
    fun currentRole(): UserRole = roleFor(deps.appViewSettings.viewSetting.value)

    // One toast channel shared by the graph builders, the same the app-level screens
    // use. The graphs own no side effect of their own.
    val toast = { message: String ->
        Toast.makeText(
            context,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    // The lottery the owner picked on the lottery list, shared by the lottery edit,
    // details and waitlist screens inside the lottery graph.
    val lotterySelected = remember { LotterySelectedViewModel() }

    // The explore graph owns its own campsite model and controller: the search
    // screen draws what its controller loads, and the shared storage boundary
    // behind them is the app's.
    val exploreCampsiteModel = remember { CampsiteModel() }
    val exploreCampsiteController = remember {
        CampsiteController(exploreCampsiteModel, deps.campsiteRepository)
    }

    // The campsite graph carries its own model for the registration and edit
    // screens; the app root keeps its own for the campsite monitor below.
    val campsiteGraphModel = remember { CampsiteModel() }

    NavHost(navController = nav, startDestination = Screen.Start) {

        // Sign-in flow: Start, Login, SignUp, Role. The graph applies the view the
        // signed-in user's privileges give and then asks Home to open.
        startupGraph(
            deviceID = deps.deviceID,
            userController = deps.userController,
            userModel = deps.userModel,

            navToSignUp = {
                nav.navigate(Screen.SignUp)
            },
            navToHomePage = { role ->
                nav.goHome(role)
            },
            navToRoleSelector = {
                nav.navigate(Screen.Role)
            },

            // A freshly signed-up user has no chosen view, so the derived one
            // applies; a returning user's saved view stays theirs.
            changeAppViewTo = { view ->
                deps.appViewSettings.applyViewDefaultIfNoneChosen(view)
            },
            onGetStartedPressed = {
                nav.navigate(Screen.Login)
            },
        )

        // Explore and Search. Search is reached from the live Home frame's own
        // explore content (below); this graph hosts it for the app.
        exploreGraph(
            navToSettings = {
                nav.navigate(Screen.Settings(currentRole()))
            },
            navToCampsite = { card ->
                nav.navigate(
                    Screen.CampsiteDetailsScreen(currentRole(), card.id)
                )
            },
            navToSearch = { text ->
                nav.navigate(Screen.Search(currentRole(), text))
            },
            navToCampDetails = { role, campsiteId ->
                nav.navigate(Screen.CampsiteDetailsScreen(role, campsiteId))
            },

            onNavBack = {
                nav.navigateUp()
            },
            onSearchFilterClicked = {
                nav.navigate(Screen.Search(currentRole(), "", true))
            },
            onOpenHistory = {
                nav.navigate(Screen.Search(currentRole(), "", false, true))
            },
            onOpenQr = {
                nav.navigate(Screen.QrScanner(currentRole()))
            },

            campsiteRepo = deps.campsiteRepository,
            campsiteModel = exploreCampsiteModel,
            campsiteControl = exploreCampsiteController,

            toastGenerator = toast
        )

        // The campsite half: the owner's My Campsites links, campsite registration
        // and editing, and one campsite's details page.
        campsiteGraph(
            deviceID = deps.deviceID,
            appViewSettings = deps.appViewSettings.toAppViewSetting(),
            notification = deps.notificationController,
            admin = deps.adminController,
            currentUserId = deps.currentUserId,

            onNavBack = {
                nav.navigateUp()
            },
            onOpenMyCampsites = {
                nav.navigate(Screen.CampsiteMonitor(currentRole()))
            },
            onOpenLotteries = {
                nav.navigate(Screen.LotteryMonitor)
            },
            onOpenReservations = {
                nav.navigate(Screen.Reservations)
            },
            // A details screen's tab picker goes home naming the tab.
            onCampsiteDetailsClicked = { role, tab ->
                nav.goHome(role, tab)
            },

            campsiteRepo = deps.campsiteRepository,
            campsiteModel = campsiteGraphModel,

            toastGenerator = toast
        )

        // The lottery half: the owner's lottery list, editing, one lottery's
        // details, and an entrant's waitlist.
        lotteryGraph(
            deviceID = deps.deviceID,
            appViewSettings = deps.appViewSettings.toAppViewSetting(),
            lottery = lotterySelected,

            notification = deps.notificationController,
            userRepository = deps.userRepository,

            onNavBack = {
                nav.navigateUp()
            },
            onNavToEditLottery = {
                nav.navigate(Screen.LotteryEdit)
            },
            onNavToLotteryDetails = {
                nav.navigate(Screen.LotteryDetails)
            },
            onNavToWaitlist = {
                nav.navigate(Screen.LotteryWaitlist)
            },
            onGoHome = {
                nav.goHome(currentRole())
            },
        )

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

            // The bottom bar moves between the tabs on this nested graph: the
            // app's own nav never enters or leaves a tab, so a tab change is
            // never stacked on top of the last one and the bar keeps its state.
            val homeNav = rememberNavController()

            HomeScreen(
                role = role,
                appViewSettings = deps.appViewSettings.toAppViewSetting(),
                initialTab = route.tab,
                homeNavController = homeNav
            ) {
                // One node per bottom bar tab. Which tabs appear is the screen's
                // own decision (its tabsFor); every tab the bar can name is
                // registered here, so pressing any circle lands on real content.
                composable<Screen.Explore> {
                    ExploreScreen(
                        recommended = campsiteCards,
                        newSites = campsiteCards,
                        onSettingsClick = {
                            // A plain push: this tab's content only ever shows while
                            // Home is the live app-level frame, so pushing without a
                            // pop leaves Home mounted under Settings, and Settings'
                            // navigateUp back pops Settings and returns to the SAME
                            // live Home, Explore still selected with its cards intact.
                            // The earlier typed popUpTo<Screen.Home> could not match a
                            // live Home frame (Home is a parameterized route; a typed
                            // pop matches a class as a pattern no instance route equals)
                            // and popped the whole stack, leaving Settings the only
                            // entry (reported regression 7).
                            nav.navigate(Screen.Settings(role))
                        },
                        onCampsiteClick = { campsite ->
                            // Plain push like the settings entry: Home stays mounted
                            // under the details screen, so backing from details lands
                            // on the same live Home with Explore and its cards intact.
                            nav.navigate(
                                Screen.CampsiteDetailsScreen(role, campsite.id)
                            )
                        },
                        onSearch = { text ->
                            nav.navigate(Screen.Search(role, text))
                        },
                        onFilterClick = {
                            nav.navigate(Screen.Search(role, "", true))
                        },
                        onOpenHistory = {
                            nav.navigate(Screen.Search(role, "", false, true))
                        },
                        onOpenQr = {
                            nav.navigate(Screen.QrScanner(role))
                        }
                    )
                }

                composable<Screen.Bookings> {
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
                        nav = nav,
                        openTab = CorrespondenceTabs.MESSAGES_TAB,
                        onNavBack = { homeNav.navigateUp() },
                        deps = deps
                    )
                }

                composable<Screen.Inbox> {
                    CorrespondenceDestination(
                        nav = nav,
                        openTab = CorrespondenceTabs.NOTIFICATIONS_TAB,
                        onNavBack = { homeNav.navigateUp() },
                        deps = deps
                    )
                }

                // The camper's Reservations tab hosts the panel directly: pressing the
                // tab lands in the view itself, no second button asking again. Back pops
                // the tab's own frame on the nested graph, which lands the bar back on
                // the first tab — Explore stays under every other tab.
                composable<Screen.Reservations> {
                    ReservationsPanel(deps) { homeNav.navigateUp() }
                }

                composable<Screen.MyCampsites> {
                    MyCampsitesTab(
                        onOpenMyCampsites = {
                            nav.navigate(Screen.CampsiteMonitor(role))
                        },
                        onOpenLotteries = {
                            nav.navigate(Screen.LotteryMonitor)
                        },
                        onOpenReservations = {
                            nav.navigate(Screen.Reservations)
                        }
                    )
                }

                composable<Screen.AdminProfileView> {
                    LaunchedEffect(Unit) {
                        deps.userController.loadAllUsers()
                    }
                    AdminProfilesScreen(
                        { nav.goHome(role) },
                        deps.userModel
                    )
                }

                composable<Screen.AdminNotificationView> {
                    LaunchedEffect(Unit) {
                        deps.userController.loadAllUsers()
                    }
                    NotificationLogScreen(
                        title = "Notification Debugging",
                        notificationSource = deps.notificationRepository,
                        returnFunc = { nav.goHome(role) },
                        // The debug log holds profile references, so it names the
                        // users they stand for: the saved users are looked up once
                        // above and every reference resolves against them. The log
                        // watches the model, so the names show even though the view
                        // opens before the users arrive.
                        profileText = { userId ->
                            profileLabelOf(userId, deps.userModel.allUsers)
                        },
                        savedUsers = deps.userModel
                    )
                }

                // Tabs whose real screens do not exist yet.
                composable<Screen.Profile> {
                    PlaceholderTab("Profile")
                }

                composable<Screen.Reports> {
                    PlaceholderTab("Reports")
                }

                composable<Screen.Flagged> {
                    PlaceholderTab("Flagged")
                }
            }
        }

        // Campsite monitor (owner's list of campsites)
        composable<Screen.CampsiteMonitor> { entry ->
            val route = entry.toRoute<Screen.CampsiteMonitor>()
            val activity = remember {
                CampsiteMonitorActivity(
                    // Back goes to Home naming the My Campsites tab: a bare Home
                    // pop re-enters Home through its start, which is the first
                    // tab, and landed the owner on Explore (reported regression
                    // 9). Naming the tab sends Home's nested graph the right one.
                    goHome = { nav.goHome(route.role, tabIndexOf(route.role, Screen.MyCampsites)) },
                    goBack = { nav.goHome(route.role, tabIndexOf(route.role, Screen.MyCampsites)) },
                    goToRegisterCampsite = {
                        nav.navigate(Screen.CampsiteRegistration)
                    },
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
                    // The csv export button writes through this boundary; without it
                    // the screen offers no download at all (the reported regression).
                    fileDownloadWriter = deps.fileDownloadWriter,
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

        // Reservations: the view opened by a notification tap, the owner's My Campsites
        // link and the camper's bottom tab. The private panel below owns the loading and
        // drawing, so all entries share one wiring.
        composable<Screen.Reservations> {
            ReservationsPanel(deps) { nav.navigateUp() }
        }

        composable<Screen.Settings> { entry ->
            // need to know our role so we can show
            // the relevant settings
            val role = entry.toRoute<Screen.Settings>().role
            SettingsScreen(
                back = { nav.navigateUp() },
                role = role,
                viewSettings = deps.appViewSettings,
                entrantSettings = deps.entrantSettings,
                ownerSettings = deps.ownerSettings,
                notificationPreferences = deps.notificationPreferencesService,
                appSettings = deps.appSettingsService,
                userIdForSettings = deps.currentUserId
            )
        }

        // Camera scanner: opened by the QR circle on Search. A working code opens the
        // campsite it names; going back from that campsite returns here because the
        // navigation stack keeps this screen under the details page (US 01.12.01).
        composable<Screen.QrScanner> { entry ->
            val route = entry.toRoute<Screen.QrScanner>()
            val scannerModel = remember { CampsiteModel() }
            val scannerController = remember {
                CampsiteController(
                    scannerModel,
                    deps.campsiteRepository
                )
            }
            QrScannerScreen(
                controller = scannerController,
                onOpenCampsite = { campsite ->
                    nav.navigate(Screen.CampsiteDetailsScreen(route.role, campsite.id))
                }
            )
        }

        // Notification and message destinations. Tapping a notification opens the notices a
        // viewer received: an owner's own list is the sent one, so their received notices
        // live under the Received tab, while every other viewer has a single notices tab.
        composable(NotificationRoutes.NOTIFICATIONS) {
            CorrespondenceDestination(
                nav = nav,
                openTab = if (deps.currentUserIsOwner) CorrespondenceTabs.RECEIVED_TAB else CorrespondenceTabs.NOTIFICATIONS_TAB,
                onNavBack = { nav.navigateUp() },
                deps = deps
            )
        }
        composable(NotificationRoutes.MESSAGES) {
            CorrespondenceDestination(
                nav = nav,
                openTab = CorrespondenceTabs.MESSAGES_TAB,
                onNavBack = { nav.navigateUp() },
                deps = deps
            )
        }
        // Tapping a RESERVATION-type notification opens the reservations view. The
        // bridge pops its own frame before the push: a string frame left on the stack
        // would otherwise keep this invisible frame under the reservations screen, and
        // the first back press would land on it instead of the view the notification
        // was tapped from. Naming the live Home frame with a typed popUpTo could not
        // work (Home is a parameterized route; the pop matched nothing, emptied the
        // stack and left the reservations view as the only entry — reported regression
        // 7's reservations half).
        composable(NotificationRoutes.RESERVATIONS) {
            LaunchedEffect(Unit) {
                nav.navigateUp()
                nav.navigate(Screen.Reservations)
            }
        }
        // Taps from WIN and INVITE notifications share the booking route. The
        // destination below opens the invitation panel only while the invitation is
        // still pending for the current viewer, and otherwise keeps navigating to the
        // reservations screen (there is still no separate booking screen).
        composable("${NotificationRoutes.BOOKING}/{lotteryId}") { entry ->
            InvitationDestination(
                nav = nav,
                lotteryId = entry.arguments?.getString("lotteryId"),
                deps = deps
            )
        }
        // Tapping a MESSAGE notification opens that reservation's chat.
        composable("${NotificationRoutes.CHAT}/{reservationId}") { entry ->
            CorrespondenceDestination(
                nav = nav,
                openTab = CorrespondenceTabs.MESSAGES_TAB,
                openReservationId = entry.arguments?.getString("reservationId"),
                onNavBack = { nav.navigateUp() },
                deps = deps
            )
        }
        // The phone's own camera opens a CampBNB code's link here: MainActivity has
        // already resolved the code's text into one of these routes and queued it on
        // pendingRoute, the same bridge a notification tap uses, and the sign-in ran
        // before this first render, so the user's data populates. A live code names
        // its campsite: this invisible string frame opens the details page and pops
        // itself out of the stack like the reservations bridge does, so the first
        // back press goes to Home, not to this blank frame (the reported white
        // screen). The role carried into the key is the signed-in user's own
        // privileges, the same derivation the login flow uses.
        composable("$qrLinkRoute/{campsiteId}") { entry ->
            val campsiteId = entry.arguments?.getString("campsiteId") ?: ""
            LaunchedEffect(campsiteId) {
                if (campsiteId.isNotBlank()) {
                    val privileges = deps.userModel.currentUser?.roles ?: emptyList()
                    val role = when {
                        Role.ADMIN in privileges -> Role.ADMIN
                        Role.OWNER in privileges -> Role.OWNER
                        else -> Role.ENTRANT
                    }
                    nav.navigateUp()
                    nav.navigate(Screen.CampsiteDetailsScreen(toUserRole(role), campsiteId))
                }
            }
        }
        // A dead code's link — its stop already past, or no expiry at all — answers
        // the old-code notice in place of any destination; back goes where the link
        // would otherwise have been.
        composable(qrOldCodeRoute) {
            QrOldCodeNoticeScreen(back = { nav.navigateUp() })
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
 * Index of a tab in a role's bottom bar; -1 when that role has no such tab.
 *
 * @param role The role whose tab bar is meant.
 * @param destination The tab's screen to find.
 */
private fun tabIndexOf(role: UserRole, destination: Screen): Int =
    tabsFor(role).indexOfFirst { it.destination == destination }

/**
 * The Correspondence screen, with the tabs this viewer has.
 *
 * The tabs and their contents are chosen here, where the viewer's role is known: an owner
 * is shown the notices they caused under "Notifications" and the notices they received
 * under "Received", while every other viewer has the one notices tab they can see. The
 * screen itself only draws what it is handed.
 *
 * @param nav The app's NavController, used when a notification is tapped.
 * @param openTab Which tab opens, named in CorrespondenceTabs.
 * @param openReservationId If set, the Messages tab opens straight into this reservation's chat.
 * @param onNavBack Runs when the screen's Back control is pressed: the caller that opened
 *     this destination owns where back goes.
 */
@Composable
private fun CorrespondenceDestination(
    nav: NavController,
    openTab: String,
    openReservationId: String? = null,
    onNavBack: () -> Unit = {},
    deps: Dependencies
) {
    if (deps.currentUserIsOwner) {
        CorrespondenceScreen(
            tabLabels = listOf(
                CorrespondenceTabs.NOTIFICATIONS_TAB,
                CorrespondenceTabs.RECEIVED_TAB,
                CorrespondenceTabs.MESSAGES_TAB
            ),
            openTab = openTab,
            notificationsTabContent = { sentNoticesTab(deps) },
            receivedTabContent = { receivedNoticesTab(nav, deps) },
            messagesTabContent = { MessagesTab(openReservationId, deps) },
            onNavBack = onNavBack
        )
    } else {
        CorrespondenceScreen(
            tabLabels = listOf(
                CorrespondenceTabs.NOTIFICATIONS_TAB,
                CorrespondenceTabs.MESSAGES_TAB
            ),
            openTab = openTab,
            notificationsTabContent = { receivedNoticesTab(nav, deps) },
            messagesTabContent = { MessagesTab(openReservationId, deps) },
            onNavBack = onNavBack
        )
    }
}

/**
 * The "Sent Notifications" of the current viewer: the notices they caused, read through a
 * repository scoped to their own id, drawn by the notification log.
 */
@Composable
private fun sentNoticesTab(deps: Dependencies) {
    NotificationLogScreen(
        title = "Sent Notifications",
        notificationSource = SentNotificationsRepository(deps.notificationRepository, deps.deviceID)
    )
}

/**
 * The notices the current viewer received. viewModel() keeps the same inbox (and its
 * Firestore listener) across recompositions. Tapping a notice runs the app's own bridge:
 * the route that notice names, so an owner's tap opens the same destination any other
 * entry to that notice would.
 */
@Composable
private fun receivedNoticesTab(nav: NavController, deps: Dependencies) {
    val userId = deps.deviceID
    val inboxViewModel: NotificationViewModel = viewModel(
        key = "notification-inbox-$userId",
        factory = simpleViewModelFactory {
            NotificationViewModel(deps.notificationController, userId)
        }
    )
    NotificationInboxScreen(viewModel = inboxViewModel, navToNotification = { notification ->
        nav.navigate(NotificationRoutes.routeFor(notification))
    })
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

/**
 * The invitation panel opened by a lottery INVITE notification: the invited owner reads
 * what they were invited to and accepts with one tap, which stores the lottery as joined
 * and then opens the owner lottery list, where it now shows. The panel only appears while
 * the invitation is pending for the current viewer, so taps from WIN notifications keep
 * navigating to the reservations screen as before.
 *
 * @param nav The app's NavController.
 * @param lotteryId The route argument naming the invited lottery.
 * @param deps The app-wide dependencies, which name the viewer by device id.
 */
@Composable
private fun InvitationDestination(
    nav: NavController,
    lotteryId: String? = null,
    deps: Dependencies
) {
    val viewerId = deps.deviceID
    var invitedLottery by remember { mutableStateOf<Lottery?>(null) }
    var invitationResolved by remember { mutableStateOf(false) }

    LaunchedEffect(lotteryId) {
        if (!lotteryId.isNullOrEmpty()) {
            Database.Lotteries.readEngine().getById(
                id = lotteryId,
                onSuccess = { found ->
                    invitedLottery = found
                    invitationResolved = true
                },
                onFailure = {
                    invitationResolved = true
                }
            )
        } else {
            invitationResolved = true
        }
    }

    val invited = invitedLottery
    if (invited != null && viewerId in invited.invitedOwnerIds && viewerId !in invited.joinedOwnerIds) {
        val invitationModel = remember { LotteryModel() }
        val invitationRepository = remember { LotteryRepository() }
        val invitationController = remember {
            LotteryController(
                invitationModel,
                invitationRepository,
                Database.Lotteries.readEngine(),
                deps.notificationController
            ) { System.currentTimeMillis() }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Lottery Invitation",
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "You were invited to join this lottery. Accept to see it in your list and add your own campsites."
            )
            Button(
                onClick = {
                    invitationController.acceptInvitation(
                        lottery = invited,
                        userId = viewerId,
                        onSuccess = { nav.navigate(Screen.LotteryMonitor) }
                    )
                }
            ) {
                Text("Accept invitation")
            }
            TextButton(onClick = { nav.navigate(Screen.Reservations) }) {
                Text("Not now")
            }
        }
    } else if (invitationResolved) {
        LaunchedEffect(invitationResolved) {
            nav.navigate(Screen.Reservations) {
                popUpTo(Screen.Reservations) { inclusive = true }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

/**
 * The reservations view, opened by a notification tap, the owner's My Campsites link and
 * the camper's bottom tab. Which reservations a viewer may list is decided by the read
 * handed here: an owner browses their own campsites and sees every reservation at each,
 * while every other viewer reads their own reservations and is offered only the sites
 * that carry them - an entrant reaching this view gets their own bookings, not an empty
 * campsite selector. The screen only draws what it is handed.
 *
 * @param deps The app-wide dependencies, naming the viewer and the storage boundaries.
 * @param goBack The return function handed to the screen, owned by the caller that opened
 *     this view.
 */
@Composable
private fun ReservationsPanel(deps: Dependencies, goBack: () -> Unit) {
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
    LaunchedEffect(deps.deviceID) {
        deps.campsiteRepository.getAll(
            onSuccess = { allCampsites ->
                // A real owner is recognised by owning sites: their saved profile does not
                // have to carry the OWNER role flag, and gating on the flag left the
                // top screen of a plain owner empty (the reported regression).
                if (allCampsites.any { it.ownerId == deps.deviceID }) {
                    val browsedCampsites = allCampsites.filter {
                        it.ownerId == deps.deviceID
                    }
                    campsites = browsedCampsites
                    if (selectedCampsiteId == null ||
                        browsedCampsites.none { it.id == selectedCampsiteId }
                    ) {
                        selectedCampsiteId = browsedCampsites.firstOrNull()?.id
                    }
                } else {
                    deps.reservationRepository.getForEntrant(
                        entrantId = deps.deviceID,
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
        val viewerIsOwner = campsites.any { it.ownerId == deps.deviceID }

        if (campsiteId == null) {
            reservations = emptyList()
        } else {
            deps.reservationRepository.run {
                when (selectedFilter) {
                    ReservationFilter.ACTIVE -> getActive(
                        campsiteId,
                        onSuccess = { loaded ->
                            reservations =
                                if (viewerIsOwner) loaded
                                else loaded.filter { it.entrantId == deps.deviceID }
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
                                else loaded.filter { it.entrantId == deps.deviceID }
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
                                else loaded.filter { it.entrantId == deps.deviceID }
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
        csvPreferences = deps.notificationPreferencesService,
        currentUserId = deps.currentUserId,
        fileDownloadWriter = deps.fileDownloadWriter
    )
}
