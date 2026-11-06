package com.example.campbnb

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.campbnb.ui.Screens.ReservationScreen
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.UserController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.ReservationFilter
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.FirestoreNotificationRepository
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.Screens.DeviceSignInScreen
import com.example.campbnb.notifications.NotificationRoutes
import com.example.campbnb.ui.Screens.ExploreScreen
import com.example.campbnb.ui.Screens.HomeDestination
import com.example.campbnb.ui.Screens.HomeScreen
import com.example.campbnb.ui.Screens.PlaceholderTab
import com.example.campbnb.ui.Screens.RoleSelectScreen
import com.example.campbnb.ui.Screens.SignUpScreen
import com.example.campbnb.ui.Screens.StartScreen
import com.example.campbnb.ui.campsite.CampsiteRegistrationForm
import com.example.campbnb.ui.components.GradientButton
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.details.CampsiteDetailsScreen
import com.example.campbnb.ui.details.placeholderDetails
import com.example.campbnb.ui.inbox.CorrespondenceScreen
import com.example.campbnb.ui.inbox.NotificationInboxScreen
import com.example.campbnb.ui.inbox.NotificationViewModel
import com.example.campbnb.ui.lottery.LotteryDetailsActivity
import com.example.campbnb.ui.lottery.LotteryEditActivity
import com.example.campbnb.ui.lottery.LotteryMonitorActivity
import com.example.campbnb.ui.lottery.LotteryWaitlistActivity
import com.example.campbnb.ui.search.SearchScreen
import com.example.campbnb.ui.settings.SettingsScreen
import com.example.campbnb.ui.shared.DeviceId
import com.example.campbnb.ui.shared.toUserRole
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSettingController
import com.example.campbnb.viewModels.simpleViewModelFactory
import com.google.firebase.auth.FirebaseAuth

@Composable
fun CampBnbApp(
    appViewSettings: AppViewSettingController,
    pendingRoute: String? = null,
    onPendingRouteHandled: () -> Unit = {}
) {

    val nav = rememberNavController()
    val context = LocalContext.current
    val userModel = remember { UserModel() }
    val userRepo = remember { UserRepository() }
    val userController = remember { UserController(userModel, userRepo) }

    var selectedLottery by remember { mutableStateOf<Lottery?>(null) }

    NavHost(navController = nav, startDestination = Screen.Start) {
        // Start
        composable<Screen.Start> {
            StartScreen(onGetStarted = { nav.navigate(Screen.Login) })
        }

        // Login
        composable<Screen.Login> {
            DeviceSignInScreen(
                readDevice = { DeviceId.get(context, {""}) },
                userController = userController,
                onEntered = {
                    val role = userModel.currentUser?.roles?.firstOrNull() ?: Role.ENTRANT
                    nav.navigate(Screen.Home(role = toUserRole(role))) {
                        popUpTo(Screen.Start) { inclusive = true }
                    }
                },
                onNotFound = {
                    nav.navigate(Screen.SignUp)
                }
            )
        }

        // SignUp
        composable<Screen.SignUp> {
            SignUpScreen(
                onSignUp = { email, username, phoneNumber ->
                    val newUser = User(
                        deviceId = DeviceId.get(context),
                        name = username,
                        email = email,
                        phone = phoneNumber,
                        roles = listOf(Role.ADMIN)
                    )
                    Database.Users.writeEngine().save(newUser, { /* TODO: handle the complete */})
                    nav.navigate(Screen.Role) }
            )
        }

        // Role Selection
        composable<Screen.Role> {
            RoleSelectScreen(onRoleSelected = { role ->
                val targetView = when (role) {
                    UserRole.ADMIN -> AppView.AdminView
                    UserRole.OWNER -> AppView.OwnerView
                    UserRole.CAMPER -> AppView.EntrantView
                }
                appViewSettings.changeViewTo(targetView)
                nav.navigate(Screen.Home(role = role)) {
                    popUpTo(Screen.Start) {
                        inclusive = true
                    }
                }
            })
        }
        // Home (Explore plus the role's bottom bar)
        composable<Screen.Home> { entry ->
            val route = entry.toRoute<Screen.Home>()
            val role = route.role
            HomeScreen(
                role = role,
                initialTab = route.tab,
                destinationContent = { destination ->
                    if (destination == HomeDestination.MY_CAMPSITES) {
                        MyCampsitesTab(
                            onRegisterCampsite = { nav.navigate(Screen.CampsiteEditActivity) },
                            onOpenLotteries = { nav.navigate(Screen.LotteryMonitorActivity) },
                            onOpenReservations = { nav.navigate(Screen.Reservations) }
                        )
                    } else {
                        PlaceholderTab(destination)
                    }
                },
                exploreContent = {
                    ExploreScreen(
                        onSettingsClick = { nav.navigate(Screen.Settings(role)) }
                    )
                },
                appViewSettings = appViewSettings.toAppViewSetting()
            )
        }
        // Campsite registration (owner)
        composable<Screen.CampsiteEditActivity> {
            val context = LocalContext.current
            val controller = remember { CampsiteController(CampsiteModel(), CampsiteRepository()) }
            CampsiteRegistrationForm(
                onBack = { nav.navigateUp() },
                onUpload = { campsite, _ ->
                    // TODO: upload the chosen image too
                    controller.registerCampsite(campsite, DeviceId.get(context)) { success ->
                        if (success) {
                            nav.navigateUp()
                        }
                    }
                }
            )
        }
        // Search
        composable<Screen.Search> { entry ->
            val route = entry.toRoute<Screen.Search>()
            SearchScreen(
                initialQuery = route.query,
                onClose = {
                    nav.navigateUp()
                },
                onCampsiteClick = {
                    nav.navigate(Screen.CampsiteDetailsScreen(route.role, it.id))
                },
            )
        }
        // Campsite details
        composable<Screen.CampsiteDetailsScreen> { entry ->
            val route = entry.toRoute<Screen.CampsiteDetailsScreen>()
            CampsiteDetailsScreen(
                role = route.role,
                campsite = placeholderDetails(route.campsiteId),
                // navigateUp so a fast double tap can't go too far back
                onBack = { nav.navigateUp() },
                onTabSelected = { index -> nav.goHome(route.role, index) },
                onEnterLottery = { /* TODO: enter the lottery */ },
                onDeleteSite = { /* TODO: delete through the campsite controller */ }
            )
        }
        // Lottery edit
        composable<Screen.LotteryEditActivity> {
            val activity = remember {
                LotteryEditActivity(
                    { nav.navigateUp() },
                    // using NavigateUp prevents the user from clicking on the back screen
                    // quickly and navigating too far up
                    { nav.navigateUp() },
                    appViewSettings.toAppViewSetting()
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
            activity.LotteryEdit(model = model, controller = controller, initialLottery = selectedLottery)
        }
        // Lottery monitor (owner's list of lotteries)
        composable<Screen.LotteryMonitorActivity> {
            val activity = remember {
                LotteryMonitorActivity(
                    { nav.navigate(Screen.Start /* TODO: CHANGE TO HOME */) },
                    { nav.navigateUp() },
                    {
                        selectedLottery = null
                        nav.navigate(Screen.LotteryEditActivity)
                    },
                    { lottery ->
                        selectedLottery = lottery
                        nav.navigate(Screen.LotteryDetailsActivity)
                    },
                    appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember {
                LotteryController(
                    model,
                    repo,
                    Database.Lotteries.readEngine()
                ) {
                    System.currentTimeMillis()
                }
            }
            activity.LotteryMonitor(model = model, controller = controller)
        }
        composable<Screen.LotteryWaitlistActivity> {
            val activity = remember {
                LotteryWaitlistActivity(
                    { nav.navigate(Screen.Start) },
                    { nav.navigateUp() },
                    appViewSettings = appViewSettings.toAppViewSetting()
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


        composable<Screen.Reservations> {

            ReservationScreen(
                campsites = emptyList(),
                selectedCampsiteId = null,
                selectedFilter = ReservationFilter.ACTIVE,
                reservations = emptyList(),
                onCampsiteSelected = {},
                onFilterSelected = {}
            )
        }
        composable<Screen.Settings> { entry ->
            // need to know our role so we can show
            // the relevant settings
            val role = entry.toRoute<Screen.Settings>().role
            SettingsScreen(
                { nav.navigateUp() },
                role,
                appViewSettings
            )
        }
        composable<Screen.LotteryDetailsActivity> {
            val activity = remember {
                LotteryDetailsActivity(
                    returnHome = { nav.navigate(Screen.Start) },
                    navigateBackwards = { nav.navigateUp() },
                    onEditLottery = { _ -> nav.navigate(Screen.LotteryEditActivity) },
                    onViewWaitlist = { _ -> /* TODO */ },
                    appViewSettings = appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            activity.LotteryDetails(model = model, selectedLottery = selectedLottery)
        }

        // Notification and message destinations
        composable(NotificationRoutes.NOTIFICATIONS) {
            CorrespondenceDestination(nav = nav, initialTab = 0)
        }
        composable(NotificationRoutes.MESSAGES) {
            CorrespondenceDestination(nav = nav, initialTab = 1)
        }
    }

    LaunchedEffect(pendingRoute) {
        if (!pendingRoute.isNullOrBlank()) {
            nav.navigate(pendingRoute)
            onPendingRouteHandled()
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
 * @param nav The app's NavController, used when a notification is tapped.
 * @param initialTab 0 opens Notifications, 1 opens Messages.
 */
@Composable
private fun CorrespondenceDestination(nav: NavController, initialTab: Int) {
    val context = LocalContext.current
    // viewModel() keeps the same inbox (and its Firestore listener) across recompositions
    val inboxViewModel: NotificationViewModel = viewModel(
        factory = simpleViewModelFactory {
            NotificationViewModel(
                NotificationController(FirestoreNotificationRepository()),
                DeviceId.get(context)
            )
        }
    )
    CorrespondenceScreen(
        navController = nav,
        initialTab = initialTab,
        notificationsContent = {
            NotificationInboxScreen(viewModel = inboxViewModel, navController = nav)
        },
        messagesContent = {
            PlaceholderTab(HomeDestination.MESSAGES)
        }
    )
}

/** Owner's My Campsites tab. For now it links to campsite registration and the lottery list. */
@Composable
private fun MyCampsitesTab(onRegisterCampsite: () -> Unit, onOpenLotteries: () -> Unit, onOpenReservations: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("My Campsites", fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))
        GradientButton("Register Campsite", onRegisterCampsite)
        Spacer(Modifier.height(12.dp))
        GradientButton("My Lotteries", onOpenLotteries)
        Spacer(Modifier.height(12.dp))
        GradientButton("Reservations", onOpenReservations)
    }
}
