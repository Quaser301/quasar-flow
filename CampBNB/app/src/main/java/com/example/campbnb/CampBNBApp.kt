package com.example.campbnb

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.campbnb.ui.lottery.LotteryEditActivity
import com.example.campbnb.ui.lottery.LotteryMonitorActivity

@Composable
fun CampBNBApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        //composable("home") { HomeScreen(navController) }
        composable("LotteryEditActivity") { LotteryEditActivity(navController) }
        composable("LotteryMonitorActivity") { LotteryMonitorActivity(navController) }

    }
}