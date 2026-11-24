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
import androidx.lifecycle.ViewModel
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
import com.example.campbnb.controller.MessageController
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

    // One toast channel shared by the graph builders, the same the app-level screens
    // use. The graphs own no side effect of their own.
    val toast = { message: String, duration:Int ->
        Toast.makeText(
            context,
            message,
            duration
        ).show()
    }

    // The lottery the owner picked on the lottery list, shared by the lottery edit,
    // details and waitlist screens inside the lottery graph.

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
            onNavHome = {
                nav.goHome(roleFor(deps.appViewSettings.viewSetting.value))
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

            toastGenerator = toast,
        )

        // The lottery half: the owner's lottery list, editing, one lottery's
        // details, and an entrant's waitlist.
        lotteryGraph(
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

        // Home (Explore plus the role's bottom bar)
        composable<Screen.Home> { entry ->
            val route = entry.toRoute<Screen.Home>()
            val role = route.role

            val context = LocalContext.current



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

            }
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

