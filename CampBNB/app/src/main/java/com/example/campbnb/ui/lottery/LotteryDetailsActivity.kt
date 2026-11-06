package com.example.campbnb.ui.lottery

import androidx.compose.runtime.Composable
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.ui.shared.AppViewModificationIsValidForCurrentWindowChecker
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting

class LotteryDetailsActivity(
    val returnHome: () -> Unit,
    val navigateBackwards: () -> Unit,
    val appViewSettings: AppViewSetting
) {
    @Composable
    fun LotteryDetails(model: LotteryModel){

        AppViewModificationIsValidForCurrentWindowChecker(
            returnHome,
            appViewSettings,
            listOf(
                AppView.EntrantView,
                AppView.AdminView
            )
        )

    }
}