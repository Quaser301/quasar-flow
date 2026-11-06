package com.example.campbnb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.ui.Screens.ExploreScreen
import com.example.campbnb.ui.Screens.HomeDestination
import com.example.campbnb.ui.Screens.HomeScreen
import com.example.campbnb.ui.Screens.LoginScreen
import com.example.campbnb.ui.Screens.PlaceholderTab
import com.example.campbnb.ui.Screens.RoleSelectScreen
import com.example.campbnb.ui.Screens.SignUpScreen
import com.example.campbnb.ui.Screens.StartScreen
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.lottery.LotteryDetailsActivity
import com.example.campbnb.ui.lottery.LotteryEditActivity
import com.example.campbnb.ui.lottery.LotteryMonitorActivity
import com.example.campbnb.ui.settings.SettingsScreen
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSettingController

@Composable
fun CampBnbApp(appViewSettings: AppViewSettingController) {

    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Screen.Start) {
        // Start
        composable<Screen.Start> {
            StartScreen(onGetStarted = { nav.navigate(Screen.Login) })
        }
        // Login
        composable<Screen.Login> {
            LoginScreen(
                onLogin = { _, role, _ ->   // TODO: authenticate first
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
                    // TODO: create the account
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
            val role = entry.toRoute<Screen.Home>().role
            HomeScreen(role = role,
                destinationContent = { destination ->
                    when (destination) {
                        HomeDestination.MY_CAMPSITES -> {
                            Button(
                                onClick = {
                                    nav.navigate(Screen.CampsiteEditActivity)
                                }
                            ) {
                                Text("Register Campsite")
                            }
                        }
                    )
                },
                destinationContent = { destination ->
                    if (destination == HomeDestination.MY_CAMPSITES) {
                        MyCampsitesTab(
                            onOpenLotteries = { nav.navigate(Screen.LotteryMonitorActivity) }
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
        // Search
        composable<Screen.Search> { entry ->
            val route = entry.toRoute<Screen.Search>()
            SearchScreen(
                initialQuery = route.query,
                onCampsiteClick = {
                    nav.navigate(Screen.CampsiteDetail(route.roleName, it.id))
                },
                onQrClick = { /* TODO: open QR scanner */ }
            )
        }
        // Campsite details
        composable<Screen.CampsiteDetail> { entry ->
            val route = entry.toRoute<Screen.CampsiteDetail>()
            CampsiteDetailsScreen(
                role = route.roleName,
                campsite = placeholderDetails(route.campsiteId),
                // navigateUp so a fast double tap can't go too far back
                onBack = { nav.navigateUp() },
                onTabSelected = { index -> nav.goHome(route.roleName, index) },
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
            activity.LotteryEdit(model = model, controller = controller)
        }
        // Lottery monitor (owner's list of lotteries)
        composable<Screen.LotteryMonitorActivity> {
            val activity = remember {
                LotteryMonitorActivity(
                    { nav.navigate(Screen.Start /* TODO: CHANGE TO HOME */) },
                    { nav.navigateUp() },
                    { nav.navigate(Screen.LotteryEditActivity) },
                    { lottery -> nav.navigate(Screen.LotteryDetailsActivity) },
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
                    onEditLottery = { lottery -> nav.navigate(Screen.LotteryEditActivity) },
                    onViewWaitlist = { lottery -> /* TODO */ },
                    appViewSettings = appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            activity.LotteryDetails(model = model)
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
    navigate(Screen.Home(roleName = role, tab = tab)) {
        popUpTo<Screen.Home> { inclusive = true }
        launchSingleTop = true
    }
}

/** Owner's My Campsites tab. For now it links to the lottery list. */
@Composable
private fun MyCampsitesTab(onOpenLotteries: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("My Campsites", fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))
        GradientButton("My Lotteries", onOpenLotteries)
    }
}