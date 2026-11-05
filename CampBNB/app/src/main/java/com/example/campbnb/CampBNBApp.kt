package com.example.campbnb

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.ui.lottery.LotteryEditActivity
import com.example.campbnb.ui.lottery.LotteryMonitorActivity

@Composable
fun CampBNBApp() {
    val navController = rememberNavController()

    val lotteryModel = remember { LotteryModel() }
    val lotteryRepo = remember { LotteryRepository() }
    val lotteryController = remember { LotteryController(lotteryModel, lotteryRepo) }
    val lotteryEditActivity = remember { LotteryEditActivity() }
    val lotteryMonitorActivity = remember { LotteryMonitorActivity() }

    NavHost(navController = navController, startDestination = "LotteryMonitorActivity") {
        //composable("home") { HomeScreen(navController) }
        composable("LotteryEditActivity") {
            lotteryEditActivity.LotteryEdit(
                model = lotteryModel,
                controller = lotteryController,
                navController = navController
            )
        }
        composable("LotteryMonitorActivity") {
            lotteryMonitorActivity.LotteryMonitor(
                model = lotteryModel,
                controller = lotteryController,
                navController = navController
            )
        }
    }
}