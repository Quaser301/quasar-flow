package com.example.campbnb.graphs

import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.campbnb.Screen
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.ui.lottery.LotteryEditActivity
import com.example.campbnb.ui.lottery.LotteryMonitorActivity
import com.example.campbnb.viewModels.AppViewSetting
import com.example.campbnb.viewModels.LotterySelectedViewModel
import kotlinx.serialization.Serializable


@Serializable
data object LotteryGraph


fun NavGraphBuilder.LotteryGraph(
    deviceID: String,
    appViewSettings: AppViewSetting,
    lottery: LotterySelectedViewModel,

    onNavBack: () -> Unit,
    onNavToEditLottery: () -> Unit,
    onNavToLotteryDetails: () -> Unit,
    onGoHome: () -> Unit,

) {
    navigation<LotteryGraph>(startDestination = Screen.Start) {
        composable<Screen.LotteryEdit> {
            val selectedLottery = lottery.selectedLottery.collectAsStateWithLifecycle()
            val activity = remember {
                LotteryEditActivity(
                    onNavBack,
                    // using NavigateUp prevents the user from clicking on the back screen
                    // quickly and navigating too far up
                    onNavBack,
                    appViewSettings
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
            activity.LotteryEdit(
                model = model,
                controller = controller,
                initialLottery = selectedLottery.value,
                deviceID = deviceID
            )
        }

        composable<Screen.LotteryMonitor> {
            val activity = remember {
                LotteryMonitorActivity(
                    onGoHome,
                    onNavBack,
                    {
                        lottery.updateLottery(null)
                        onNavToEditLottery()
                    },
                    { newLottery ->
                        lottery.updateLottery(newLottery)
                        onNavToLotteryDetails()
                    },
                    appViewSettings
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
            activity.LotteryMonitor(
                model = model,
                controller = controller,
                deviceID = deviceID
            )
        }

    }
}