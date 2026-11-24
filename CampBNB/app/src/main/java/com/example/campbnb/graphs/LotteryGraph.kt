package com.example.campbnb.graphs

import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.campbnb.Screen
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.ui.lottery.LotteryDetailsActivity
import com.example.campbnb.ui.lottery.LotteryEditActivity
import com.example.campbnb.ui.lottery.LotteryMonitorActivity
import com.example.campbnb.ui.lottery.LotteryWaitlistActivity
import com.example.campbnb.viewModels.AppViewSetting
import com.example.campbnb.viewModels.LotterySelectedViewModel
import kotlinx.serialization.Serializable



fun NavGraphBuilder.LotteryGraph(
    deviceID: String,
    appViewSettings: AppViewSetting,
    lottery: LotterySelectedViewModel,

    notification: NotificationController,
    userRepository: UserRepository,

    onNavBack: () -> Unit,
    onNavToEditLottery: () -> Unit,
    onNavToLotteryDetails: () -> Unit,
    onNavToWaitlist: () -> Unit,
    onGoHome: () -> Unit,
) {
    navigation<NavGraphNodes.LotteryGraph>(startDestination = Screen.LotteryMonitor) {
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
            // The user repository gives the edit screen the owner candidates that
            // invitations may be checked for (US 02.04.08).
            val userRepo = userRepository
            val controller = remember {
                LotteryController(
                    model,
                    repo,
                    Database.Lotteries.readEngine(),
                    notification
                ) { System.currentTimeMillis() }
            }
            activity.LotteryEdit(
                model = model,
                controller = controller,
                userRepository = userRepo,
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
                    Database.Lotteries.readEngine(),
                    notification
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

        composable<Screen.LotteryDetails> {
            val selectedLottery = lottery.selectedLottery.collectAsStateWithLifecycle()
            val activity = remember {
                LotteryDetailsActivity(
                    returnHome = onGoHome,
                    navigateBackwards = onNavBack,
                    onEditLottery = { _ -> onNavToEditLottery() },
                    onViewWaitlist = { _ -> onNavToWaitlist() },
                    appViewSettings = appViewSettings
                )
            }
            val model = remember { LotteryModel() }
            activity.LotteryDetails(
                model = model,
                selectedLottery = selectedLottery.value
            )
        }

        composable<Screen.LotteryWaitlist> {
            val selectedLottery = lottery.selectedLottery.collectAsStateWithLifecycle()
            val activity = remember {
                LotteryWaitlistActivity(
                    onGoHome,
                    onNavBack,
                    appViewSettings = appViewSettings
                )
            }
            val model = remember { LotteryModel() }
            val repo = remember { LotteryRepository() }
            val controller = remember {
                LotteryController(
                    model,
                    repo,
                    Database.Lotteries.readEngine(),
                    notification
                ) { System.currentTimeMillis() }
            }
            activity.LotteryWaitlist(
                model = model,
                controller = controller,
                selectedLottery = selectedLottery.value
            )
        }
    }
}
