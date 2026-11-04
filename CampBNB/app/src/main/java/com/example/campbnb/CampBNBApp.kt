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

    NavHost(navController = navController, startDestination = "LotteryEditActivity") {
        /**
         * Change default back to home when done testing
         */
        //composable("home") { HomeScreen(navController) }
        composable("LotteryEditActivity") {
            val activity = remember { LotteryEditActivity(navController) }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember { LotteryController(model, repo) }
            activity.LotteryEdit(model = model, controller = controller)
        }
        composable("LotteryMonitorActivity") {
            val activity = remember { LotteryMonitorActivity(navController) }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember { LotteryController(model, repo) }
            activity.LotteryMonitor(model = model, controller = controller, navController = navController)
        }
    }
}
