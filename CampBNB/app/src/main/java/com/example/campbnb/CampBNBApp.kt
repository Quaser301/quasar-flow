package com.example.campbnb

import  androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.campbnb.ui.Screens.LoginScreen
import com.example.campbnb.ui.Screens.RoleSelectScreen
import com.example.campbnb.ui.Screens.SignUpScreen
import com.example.campbnb.ui.Screens.StartScreen
import com.example.campbnb.ui.Screens.HomeScreen
import androidx.compose.runtime.remember
import androidx.navigation.toRoute
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.ui.lottery.LotteryEditActivity
import com.example.campbnb.ui.lottery.LotteryMonitorActivity
import com.example.campbnb.viewModels.AppViewSettingController

@Composable
fun CampBnbApp(appViewSettings: AppViewSettingController) {

    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Screen.Start) {
        composable<Screen.Start> {
            StartScreen(onGetStarted = { nav.navigate(Screen.Login) })
        }
        composable<Screen.Login> {
            LoginScreen(
                onLogin = { username, role, rememberMe -> /* TODO: sign in, then go to the role's home */ },
                onSignUpClick = { nav.navigate(Screen.SignUp) }
            )
        }
        composable<Screen.SignUp> {
            SignUpScreen(
                onSignUp = { email, username -> /* TODO: create the account */ nav.navigate("role") },
                onLoginClick = { nav.navigateUp() }
            )
        }
        composable<Screen.Role> {
            RoleSelectScreen(onRoleSelected = { role -> /* TODO: save the role, go to its home */ })
        }
        composable<Screen.Login> {
            LoginScreen(
                onLogin = { _, role, _ ->   // TODO: authenticate first
                    nav.navigate(Screen.Home(roleName = role)) {
                        popUpTo(Screen.Start) {
                            inclusive = true
                        }
                    }
                },
                onSignUpClick = { nav.navigate(Screen.SignUp) }
            )
        }
        composable<Screen.Role> {
            RoleSelectScreen(onRoleSelected = { role ->
                nav.navigate(Screen.Home(roleName = role)) {
                    popUpTo(Screen.Start) {
                        inclusive = true
                    }
                }
            })
        }
        composable<Screen.Home> { entry ->
            val role = entry.toRoute<Screen.Home>().roleName
            HomeScreen(role)
        }
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
            val controller = remember { LotteryController(
                model,
                repo,
                TODO(),
                TODO()
            ) }
            activity.LotteryEdit(model = model, controller = controller)
        }
        composable<Screen.LotteryMonitorActivity> {
            val activity = remember {
                LotteryMonitorActivity(
                    { nav.navigate(Screen.Start /* TODO: CHANGE TO HOME */) },
                    { nav.navigateUp() },
                    appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember { LotteryController(model, repo, TODO(), TODO()) }
            activity.LotteryMonitor(model = model, controller = controller)
        }
    }
}
