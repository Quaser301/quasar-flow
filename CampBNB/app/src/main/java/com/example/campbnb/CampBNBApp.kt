package com.example.campbnb

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.campbnb.Screen
import com.example.campbnb.graphs.StartupGraph
import com.example.campbnb.graphs.startupGraph
import com.example.campbnb.graphs.exploreGraph
import com.example.campbnb.graphs.campsiteGraph
import com.example.campbnb.graphs.LotteryGraph
import com.example.campbnb.graphs.CorrespondenceGraph
import com.example.campbnb.graphs.ReservationGraph
import com.example.campbnb.graphs.MiscGraph
import com.example.campbnb.graphs.DebugGraph
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.Role
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.screens.HomeScreen
import com.example.campbnb.ui.shared.roleFor
import com.example.campbnb.viewModels.LotterySelectedViewModel
import com.example.campbnb.ui.admin.ProfileRemovedDialog
import com.example.campbnb.ui.campsite.CampsiteReviewsScreen

@Composable
fun CampBnbApp(
    deps: Dependencies,
    lottery: LotterySelectedViewModel = viewModel()
) {

    // needed for nav obviously
    val nav = rememberNavController()
    // needed for Toasts
    val context = LocalContext.current

    // The role the app view says the user is in right now. Graph callbacks that run
    // outside one screen's route need it, the same derivation HomeScreen uses.
    fun currentRole(): UserRole = roleFor(deps.appViewSettings.viewSetting.value)

    // The role the signed-in profile actually holds, which is not the same thing: a
    // user may be viewing as another role while still holding this one. The Settings
    // screen takes this one, so an admin who switched their app view can still reach
    // the view switch and switch back; a view-derived role would hide the switch and
    // leave them locked out of their admin abilities for good.
    fun signedInRole(): UserRole {
        val privileges = deps.userModel.currentUser?.roles ?: emptyList()
        return when {
            Role.ADMIN in privileges -> UserRole.ADMIN
            Role.OWNER in privileges -> UserRole.OWNER
            else -> UserRole.CAMPER
        }
    }

    // True once an admin has deleted this user's profile and the pop-up has not been
    // answered (US 03.02.01).
    var showRemovedNotice by remember { mutableStateOf(false) }

    // A running user whose profile an admin deletes is told about it and kicked back to
    // the start screen; watching costs nothing when nothing removes the profile.
    DisposableEffect(deps.deviceID) {
        val stopWatching = deps.userController.watchForRemoval(
            deps.deviceID,
            onDeletedBySelf = {
                deps.userModel.setCurrentUser(null)
                nav.navigate(Screen.Start) {
                    popUpTo(Screen.Start) {
                        inclusive = true
                    }
                }
            },
            onRemoved = { showRemovedNotice = true }
        )
        onDispose { stopWatching() }
    }

    // One toast channel shared by the graph builders, the same the app-level screens
    // use. The graphs own no side effect of their own.
    val toast = { message: String, duration: Int ->
        Toast.makeText(
            context,
            message,
            duration
        ).show()
    }

    /**
     * Goes to the home page on the given tab, clearing any screens opened on top of it.
     *
     * The tab cannot ride the route: a route argument holds a primitive or an enum, and
     * a tab is a Screen. It is handed over as session state instead, written in the same
     * step as the navigation, so the value an entry asks for is the value Home reads.
     *
     * @param role The signed-in role, which decides the bottom bar.
     * @param tab The screen the named tab shows, or null for the bar's first tab.
     */
    fun NavController.goHome(role: UserRole, tab: Screen? = null) {
        deps.homeTab = tab
        navigate(Screen.Home(role = role)) {
            popUpTo<Screen.Home> { inclusive = true }
            launchSingleTop = true
        }
    }

    /**
     * Opens Home at the end of sign-in, evicting the sign-in screens from the back
     * stack: the back gesture (the side scroll on the phone) must not walk a signed-in
     * user back through the role picker, the sign-up form and the login screen.
     *
     * Popping to the sign-in graph node is the eviction because that node is the only
     * entry ever sitting below the first Home, and it is absent from the stack at every
     * other time Home opens — so this pop answers as nothing at all for ordinary tab
     * navigation. Popping to Home would not help here: a pop whose target is not on the
     * stack leaves the stack untouched, and Home is not on it while sign-in runs.
     */
    fun NavController.goHomeAfterSignIn(role: UserRole) {
        deps.homeTab = null
        navigate(Screen.Home(role = role)) {
            popUpTo<StartupGraph> { inclusive = true }
            launchSingleTop = true
        }
    }

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

    // The app graph opens ON the sign-in graph, not on a screen inside it: a
    // NavHost's start destination must be a direct child of its own graph, and
    // entering a graph node loads that graph's start destination (Screen.Start)
    // by itself. Naming the screen instead would crash at instantiation.
    NavHost(navController = nav, startDestination = StartupGraph) {

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
                nav.goHomeAfterSignIn(role)
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

        // Explore and Search. The same graph is mounted again inside Home's tab
        // graph below (further down); this hosts it for the app, for entries that
        // do not come through the bottom bar.
        exploreGraph(
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

            toastGenerator = toast,
            cameraAllowed = deps.cameraAllowed
        )

        // The campsite half: the owner's My Campsites links, campsite registration
        // and editing, and one campsite's details page.
        campsiteGraph(
            deviceID = deps.deviceID,
            appViewSettings = deps.appViewSettings.toAppViewSetting(),
            notification = deps.notificationController,
            admin = deps.adminController,
            currentUserId = deps.currentUserId,
            currentUserName = deps.userModel.currentUser?.name ?: "",
            fileDownloadWriter = deps.fileDownloadWriter,

            onNavBack = {
                nav.navigateUp()
            },
            // Back from the campsite monitor goes home NAMING the My Campsites tab:
            // a bare Home pop re-enters Home through its start, which is the first
            // tab, and landed the owner on Explore (reported regression 9). Naming
            // the tab sends Home's nested graph the right one.
            onNavHome = {
                nav.goHome(currentRole(), Screen.MyCampsites)
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
            onOpenCampsiteReg = {
                nav.navigate(Screen.CampsiteRegistration)
            },
            onOpenCampsiteDetails = { role, id ->
                nav.navigate(Screen.CampsiteDetailsScreen(role, id))
            },
            onOpenCampsiteEdit = { id ->
                nav.navigate(Screen.EditCampsite(id))
            },
            // A details screen's tab picker goes home naming the tab.
            onCampsiteDetailsClicked = { role, tab ->
                nav.goHome(role, tab)
            },

            campsiteRepo = deps.campsiteRepository,
            campsiteModel = campsiteGraphModel,

            toastGenerator = toast,
        )

        // The lottery half: the owner's lottery list, editing, one lottery's
        // details, and an entrant's waitlist.
        LotteryGraph(
            deviceID = deps.deviceID,
            appViewSettings = deps.appViewSettings.toAppViewSetting(),
            lottery = lottery,

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

        // Notification and message destinations, including the bridges a tapped
        // notice names: tapping a notice hands over the typed Screen route that
        // notice resolves to, and these invisible frames open the view the notice
        // points at and pop themselves out of the stack.
        CorrespondenceGraph(
            // read on each draw, not once when the graph is built (see the graph's own
            // parameter): the sign-in runs after this graph exists
            currentUserIsOwner = { deps.currentUserIsOwner },
            userId = deps.deviceID,

            userRepo = deps.userRepository,
            notifRepo = deps.notificationRepository,
            reservationRepo = deps.reservationRepository,
            campsiteRepo = deps.campsiteRepository,

            userControl = deps.userController,
            notifControl = deps.notificationController,
            msgControl = deps.messageController,

            userModel = deps.userModel,

            onNavBack = {
                nav.navigateUp()
            },
            navToNotif = { screen ->
                nav.navigate(screen)
            },
            navToReservations = {
                nav.navigate(Screen.Reservations)
            },
            navToLotteryMon = {
                nav.navigate(Screen.LotteryMonitor)
            },
            goToReservations = {
                nav.navigate(Screen.Reservations)
            },

            toastGen = toast,
        )

        // The reservations view: opened by a notification tap, the owner's My
        // Campsites link and the camper's bottom tab.
        ReservationGraph(
            userID = deps.deviceID,
            currentUserId = deps.currentUserId,

            campsiteRepo = deps.campsiteRepository,
            reservationRepo = deps.reservationRepository,
            notificationPreferences = deps.notificationPreferencesService,
            fileDownloadWriter = deps.fileDownloadWriter,

            onNavBack = {
                nav.navigateUp()
            },
        )

        // Settings, the placeholder tabs, and the two QR bridges: the link of a
        // code opened by the phone's own camera arrives on the same pending-screen
        // bridge a notification tap uses.
        MiscGraph(
            onNavBack = {
                nav.navigateUp()
            },
            navToCampsiteDetails = { role, id ->
                nav.navigate(Screen.CampsiteDetailsScreen(role, id))
            },
            navToMyCampsites = {
                nav.navigate(Screen.MyCampsites)
            },
            navToStart = {
                nav.navigate(Screen.Start)
            },

            userModel = deps.userModel,

            appViewSettings = deps.appViewSettings,
            entrantSettings = deps.entrantSettings,
            ownerSettings = deps.ownerSettings,
            notificationPreferences = deps.notificationPreferencesService,
            appSettings = deps.appSettingsService,
            userIdForSettings = deps.currentUserId,
        )

        // The admin's debugging views.
        DebugGraph(
            onNavBack = {
                nav.goHome(currentRole())
            },

            notificationRepository = deps.notificationRepository,

            userController = deps.userController,

            userModel = deps.userModel,

            adminController = deps.adminController,
        )

        // Home (Explore plus the role's bottom bar)
        composable<Screen.Home> { entry ->
            val route = entry.toRoute<Screen.Home>()
            val role = route.role

            // The bottom bar moves between the tabs on this nested graph: the
            // app's own nav never enters or leaves a tab, so a tab change is
            // never stacked on top of the last one and the bar keeps its state.
            val homeNav = rememberNavController()

            HomeScreen(
                role = role,
                appViewSettings = deps.appViewSettings.toAppViewSetting(),
                initialTab = deps.homeTab,
                homeNavController = homeNav
            ) {
                // One node per bottom bar tab: every tab the bar can name is a
                // graph this same file registers for the app, so its live
                // content and the shared components behind it are the ones any
                // other entry to that screen would get. Back controls move on
                // the bar's own graph (homeNav), so pressing one lands the bar
                // back where it was; outward jumps — a campsite's details page,
                // search, the notice bridges — still use the app's nav, which
                // keeps this live Home frame mounted underneath them.
                exploreGraph(
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
                        homeNav.navigateUp()
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

                    toastGenerator = toast,
                    cameraAllowed = deps.cameraAllowed
                )

                // The owner's My Campsites tab: the tab's own screen names the
                // links it offers, which open on the app's graph above.
                campsiteGraph(
                    deviceID = deps.deviceID,
                    appViewSettings = deps.appViewSettings.toAppViewSetting(),
                    notification = deps.notificationController,
                    admin = deps.adminController,
                    currentUserId = deps.currentUserId,
                    currentUserName = deps.userModel.currentUser?.name ?: "",
                    fileDownloadWriter = deps.fileDownloadWriter,

                    onNavBack = {
                        homeNav.navigateUp()
                    },
                    onNavHome = {
                        nav.goHome(currentRole(), Screen.MyCampsites)
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
                    onOpenCampsiteReg = {
                        nav.navigate(Screen.CampsiteRegistration)
                    },
                    onOpenCampsiteDetails = { role, id ->
                        nav.navigate(Screen.CampsiteDetailsScreen(role, id))
                    },
                    onOpenCampsiteEdit = { id ->
                        nav.navigate(Screen.EditCampsite(id))
                    },
                    // A details screen's tab picker goes home naming the tab.
                    onCampsiteDetailsClicked = { role, tab ->
                        nav.goHome(role, tab)
                    },

                    campsiteRepo = deps.campsiteRepository,
                    campsiteModel = campsiteGraphModel,

                    toastGenerator = toast,
                )

                // Messages, Inbox and Bookings tabs: the same destinations, with
                // a tap of a notice still running the app's own bridges above.
                CorrespondenceGraph(
                    // read on each draw, not once when the graph is built (see the graph's own
                    // parameter): the sign-in runs after this graph exists
                    currentUserIsOwner = { deps.currentUserIsOwner },
                    userId = deps.deviceID,

                    userRepo = deps.userRepository,
                    notifRepo = deps.notificationRepository,
                    reservationRepo = deps.reservationRepository,
                    campsiteRepo = deps.campsiteRepository,

                    userControl = deps.userController,
                    notifControl = deps.notificationController,
                    msgControl = deps.messageController,

                    userModel = deps.userModel,

                    onNavBack = {
                        homeNav.navigateUp()
                    },
                    // The notice bridges run on the app's navigation, so their pop does too:
                    // the pending frame a tap pushes sits on the app's stack even though the
                    // bar's back controls move on homeNav.
                    popNoticeFrame = {
                        nav.navigateUp()
                    },
                    navToNotif = { screen ->
                        nav.navigate(screen)
                    },
                    navToReservations = {
                        nav.navigate(Screen.Reservations)
                    },
                    navToLotteryMon = {
                        nav.navigate(Screen.LotteryMonitor)
                    },
                    goToReservations = {
                        nav.navigate(Screen.Reservations)
                    },

                    toastGen = toast,
                )

                // The camper's Reservations tab hosts the view itself.
                ReservationGraph(
                    userID = deps.deviceID,
                    currentUserId = deps.currentUserId,

                    campsiteRepo = deps.campsiteRepository,
                    reservationRepo = deps.reservationRepository,
                    notificationPreferences = deps.notificationPreferencesService,
                    fileDownloadWriter = deps.fileDownloadWriter,

                    onNavBack = {
                        homeNav.navigateUp()
                    },
                )

                // The Profile, Reports and Flagged tabs, plus Settings behind
                // Explore's settings button — as shared windows of the graph.
                MiscGraph(
                    onNavBack = {
                        homeNav.navigateUp()
                    },
                    navToCampsiteDetails = { role, id ->
                        nav.navigate(Screen.CampsiteDetailsScreen(role, id))
                    },
                    navToMyCampsites = {
                        nav.navigate(Screen.MyCampsites)
                    },
                    navToStart = {
                        nav.navigate(Screen.Start)
                    },

                    userModel = deps.userModel,

                    appViewSettings = deps.appViewSettings,
                    entrantSettings = deps.entrantSettings,
                    ownerSettings = deps.ownerSettings,
                    notificationPreferences = deps.notificationPreferencesService,
                    appSettings = deps.appSettingsService,
                    userIdForSettings = deps.currentUserId,
                )

                // The admin's Profiles and Notifications debugging tabs.
                DebugGraph(
                    onNavBack = {
                        nav.goHome(currentRole())
                    },

                    notificationRepository = deps.notificationRepository,

                    userController = deps.userController,

                    userModel = deps.userModel,

                    adminController = deps.adminController,
                )
            }
        }


    }

    // The user stays where they are until they tap OK; only then do they go to the
    // start screen (US 03.02.01: an admin deleted their profile while it was running).
    if (showRemovedNotice) {
        ProfileRemovedDialog(
            onOk = {
                showRemovedNotice = false
                deps.userModel.setCurrentUser(null)
                nav.navigate(Screen.Start) {
                    popUpTo(Screen.Start) {
                        inclusive = true
                    }
                }
            }
        )
    }

    LaunchedEffect(deps.pendingScreen) {
        val pending = deps.pendingScreen
        if (pending != null) {
            nav.navigate(pending)
            deps.onPendingRouteHandled()
        }
    }
}


