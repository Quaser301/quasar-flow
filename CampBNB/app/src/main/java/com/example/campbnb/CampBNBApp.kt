package com.example.campbnb

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.FirestoreNotificationRepository
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.notifications.NotificationRoutes
import com.example.campbnb.ui.Screens.ExploreScreen
import com.example.campbnb.ui.Screens.HomeDestination
import com.example.campbnb.ui.Screens.HomeScreen
import com.example.campbnb.ui.Screens.LoginScreen
import com.example.campbnb.ui.Screens.PlaceholderTab
import com.example.campbnb.ui.Screens.RoleSelectScreen
import com.example.campbnb.ui.Screens.SignUpScreen
import com.example.campbnb.ui.Screens.StartScreen
import com.example.campbnb.ui.campsite.CampsiteOwnerActivity
import com.example.campbnb.ui.campsite.CampsiteRatingSection
import com.example.campbnb.ui.campsite.CampsiteRegistrationForm
import com.example.campbnb.ui.campsite.LotteryJoinSection
import com.example.campbnb.ui.campsite.lotteryButtonText
import com.example.campbnb.ui.campsite.toCardList
import com.example.campbnb.ui.campsite.toDetails
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
import com.example.campbnb.ui.shared.observeModel
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

    NavHost(navController = nav, startDestination = Screen.Start) {
        // Start
        composable<Screen.Start> {
            StartScreen(onGetStarted = { nav.navigate(Screen.Login) })
        }

        composable<Screen.EditCampsite> { backStackEntry ->
            val campsiteId = backStackEntry.toRoute<Screen.EditCampsite>().campsiteId
            val context = LocalContext.current


            var campsite by remember(campsiteId) {
                mutableStateOf<Campsite?>(null)
            }

            var isLoading by remember(campsiteId) {
                mutableStateOf(true)
            }

            val repository = remember { CampsiteRepository() }
            val controller = remember {
                CampsiteController(CampsiteModel(), repository)
            }

            LaunchedEffect(campsiteId) {
                repository.getAll(
                    onSuccess = { campsites ->
                        campsite = campsites.find { it.id == campsiteId &&
                        it.ownerId == DeviceId.get(context) }
                        isLoading = false
                    },
                    onFailure = {
                        isLoading = false
                    }
                )
            }

            if (isLoading) {
                Text("Loading campsite...")
            } else if (campsite == null) {
                Text("Campsite not found")
            } else {
                CampsiteRegistrationForm(
                    existingCampsite = campsite,
                    onBack = { nav.navigateUp() },
                    onUpload = { updatedCampsite, _ ->
                        controller.updateCampsite(
                            updatedCampsite,
                            DeviceId.get(context)
                        ) { success ->
                            if (success) {
                                Toast.makeText(
                                    context,
                                    "Campsite updated successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                                nav.navigateUp()
                            }else
                                Toast.makeText(
                                    context,
                                    "Failed to update campsite",
                                    Toast.LENGTH_SHORT
                                ).show()
                        }
                    }
                )
            }
        }

        composable<Screen.CampsiteEditActivity> {
            val context = LocalContext.current
            val controller = remember { CampsiteController(CampsiteModel(), CampsiteRepository()) }
            CampsiteRegistrationForm(
                onBack = { nav.navigateUp() },
                onUpload = { campsite, _ ->
                    controller.registerCampsite(campsite, DeviceId.get(context)) { success ->
                        if (success) {
                            nav.navigateUp()
                        }
                    }
                }
            )
        }

        // Login
        composable<Screen.Login> {
            LoginScreen(
                onLogin = { _, role, _ ->
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
                },
                onSignUpClick = { nav.navigate(Screen.SignUp) }
            )
        }

        // SignUp
        composable<Screen.SignUp> {
            SignUpScreen(
                onSignUp = { _, _ ->
                    nav.navigate(Screen.Role)
                },
                onLoginClick = { nav.navigateUp() }
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

            val exploreModel = remember { CampsiteModel() }
            val exploreController = remember { CampsiteController(exploreModel, CampsiteRepository()) }
            observeModel(exploreModel)
            LaunchedEffect(Unit) {
                exploreController.loadAll()
            }

            HomeScreen(
                role = role,
                initialTab = route.tab,

                destinationContent = { destination ->
                    if (destination == HomeDestination.MY_CAMPSITES && role == UserRole.OWNER) {
                        CampsiteOwnerActivity(
                            onEdit = { campsiteId ->
                                nav.navigate(Screen.EditCampsite(campsiteId))
                            },
                            onRegister = {
                                nav.navigate(Screen.CampsiteEditActivity)
                            },
                            onBack = {
                                nav.goHome(role)
                            },
                            onOpenLotteries = {
                                nav.navigate(Screen.LotteryMonitorActivity)
                            }
                        )
                    } else {
                        PlaceholderTab(destination)
                    }
                },

                exploreContent = {
                    ExploreScreen(
                        recommended = toCardList(exploreModel.allCampsites, exploreModel),
                        onSettingsClick = { nav.navigate(Screen.Settings(role)) },
                        onSearch = { query -> nav.navigate(Screen.Search(role, query)) },
                        onCampsiteClick = { card -> nav.navigate(Screen.CampsiteDetailsScreen(role, card.id)) }
                    )
                },
                appViewSettings = appViewSettings.toAppViewSetting()
            )
        }

        // Search
        composable<Screen.Search> { entry ->
            val route = entry.toRoute<Screen.Search>()
            val model = remember { CampsiteModel() }
            val controller = remember { CampsiteController(model, CampsiteRepository()) }
            observeModel(model)
            LaunchedEffect(route.query) {
                controller.loadAndSearch(route.query)
            }
            SearchScreen(
                initialQuery = route.query,
                results = toCardList(model.visibleCampsites, model),
                onSearch = { query, _ -> controller.search(query, null) },
                onCampsiteClick = { card -> nav.navigate(Screen.CampsiteDetailsScreen(route.role, card.id)) },
                onQrClick = { /* TODO: open QR scanner */ }
            )
        }

        // Campsite details
        composable<Screen.CampsiteDetailsScreen> { entry ->
            val route = entry.toRoute<Screen.CampsiteDetailsScreen>()
            val userId = DeviceId.get(LocalContext.current)
            val campsiteModel = remember { CampsiteModel() }
            val campsiteController = remember { CampsiteController(campsiteModel, CampsiteRepository()) }
            val lotteryModel = remember { LotteryModel() }
            val lotteryController = remember {
                LotteryController(lotteryModel, LotteryRepository(), Database.Lotteries.readEngine()) {
                    System.currentTimeMillis()
                }
            }
            observeModel(campsiteModel)
            LaunchedEffect(route.campsiteId) {
                campsiteController.loadAndSelect(route.campsiteId)
            }

            val campsite = campsiteModel.selectedCampsite
            if (campsite == null) {
                var message = "Loading campsite..."
                if (campsiteModel.allCampsites.isNotEmpty()) {
                    message = "Campsite not found"
                }
                if (campsiteModel.statusMessage.isNotBlank()) {
                    message = campsiteModel.statusMessage
                }
                Text(message, modifier = Modifier.padding(24.dp))
            } else {
                CampsiteDetailsScreen(
                    role = route.role,
                    campsite = toDetails(campsite, campsiteModel),
                    onBack = { nav.navigateUp() },
                    onTabSelected = { index -> nav.goHome(route.role, index) },
                    onEnterLottery = { lotteryController.toggleEntrantMembership(userId) },
                    lotteryButtonText = lotteryButtonText(lotteryModel),
                    lotteryButtonEnabled = lotteryModel.entrantLottery != null,
                    lotteryButtonIsLeave = lotteryModel.entrantIsOnWaitlist,
                    onDeleteSite = { /* TODO: delete through the campsite controller */ },
                    entrantContent = {
                        CampsiteRatingSection(campsiteModel, campsiteController, campsite.id, userId)
                        LotteryJoinSection(lotteryModel, lotteryController, campsite.id, userId)
                    }
                )
            }
        }

        // Lottery edit
        composable<Screen.LotteryEditActivity> {
            val activity = remember {
                LotteryEditActivity(
                    { nav.navigateUp() },
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
            activity.LotteryEdit(model = model, controller = controller)
        }

        // Lottery monitor (owner's list of lotteries)
        composable<Screen.LotteryMonitorActivity> {
            val activity = remember {
                LotteryMonitorActivity(
                    { nav.navigate(Screen.Start) },
                    { nav.navigateUp() },
                    { nav.navigate(Screen.LotteryEditActivity) },
                    { _ -> nav.navigate(Screen.LotteryDetailsActivity) },
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

        composable<Screen.LotteryDetailsActivity> {
            val activity = remember {
                LotteryDetailsActivity(
                    { nav.navigate(Screen.Start) },
                    { nav.navigateUp() },
                    { _ -> nav.navigate(Screen.LotteryEditActivity) },
                    { _ -> nav.navigate(Screen.LotteryWaitlistActivity) },
                    appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            activity.LotteryDetails(model = model)
        }

        composable<Screen.LotteryWaitlistActivity> {
            val activity = remember {
                LotteryWaitlistActivity(
                    { nav.navigateUp() },
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
            activity.LotteryWaitlist(model = model, controller = controller)
        }

        composable<Screen.Settings> { entry ->
            val role = entry.toRoute<Screen.Settings>().role
            SettingsScreen(
                { nav.navigateUp() },
                role,
                appViewSettings
            )
        }

        // Notification and message destinations are additive: existing typed
        // destinations above remain unchanged.
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
private fun MyCampsitesTab(onRegisterCampsite: () -> Unit, onOpenLotteries: () -> Unit) {
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
    }
}
