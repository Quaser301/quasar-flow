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
import com.example.campbnb.viewModels.AppViewSettingController

@Composable
fun CampBNBApp(
    // insert the Controller hook so whatever control view we end up creating
    // can actually control the app settings.
    appViewSettings: AppViewSettingController
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "LotteryEditActivity") {
        /**
         * Change default back to home when done testing
         */
        composable<Screen.Home> {}
        composable<Screen.LotteryEditActivity> {
            val activity = remember {
                LotteryEditActivity(
                    { navController.navigate(Screen.Home) },
                    // using NavigateUp prevents the user from clicking on the back screen
                    // quickly and navigating too far up
                    { navController.navigateUp() },
                    appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember { LotteryController(model, repo) }
            activity.LotteryEdit(model = model, controller = controller)
        }
        composable<Screen.LotteryMonitorActivity> {
            val activity = remember {
                LotteryMonitorActivity(
                    { navController.navigate(Screen.Home) },
                    appViewSettings.toAppViewSetting()
                )
            }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember { LotteryController(model, repo) }
            activity.LotteryMonitor(model = model, controller = controller)
        }
    }
}
