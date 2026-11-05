package com.example.campbnb

import  androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.campbnb.ui.Screens.LoginScreen
import com.example.campbnb.ui.Screens.RoleSelectScreen
import com.example.campbnb.ui.Screens.SignUpScreen
import com.example.campbnb.ui.Screens.StartScreen
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.Screens.HomeScreen
import com.example.campbnb.ui.lottery.LotteryEditActivity
import com.example.campbnb.ui.lottery.LotteryMonitorActivity

@Composable
fun CampBnbApp() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "start") {
        composable("start") {
            StartScreen(onGetStarted = { nav.navigate("login") })
        }
        composable("login") {
            LoginScreen(
                onLogin = { username, role, rememberMe -> /* TODO: sign in, then go to the role's home */ },
                onSignUpClick = { nav.navigate("signup") }
            )
        }
        composable("signup") {
            SignUpScreen(
                onSignUp = { email, username -> /* TODO: create the account */ nav.navigate("role") },
                onLoginClick = { nav.popBackStack() }
            )
        }
        composable("role") {
            RoleSelectScreen(onRoleSelected = { role -> /* TODO: save the role, go to its home */ })
        }

        composable("login") {
            LoginScreen(
                onLogin = { _, role, _ ->   // TODO: authenticate first
                    nav.navigate("home/${role.name}") { popUpTo("start") { inclusive = true } }
                },
                onSignUpClick = { nav.navigate("signup") }
            )
        }
        composable("role") {
            RoleSelectScreen(onRoleSelected = { role ->
                nav.navigate("home/${role.name}") { popUpTo("start") { inclusive = true } }
            })
        }
        composable("home/{role}") { entry ->
            val role = UserRole.valueOf(entry.arguments?.getString("role") ?: UserRole.CAMPER.name)
            HomeScreen(role)
        }
    }
}