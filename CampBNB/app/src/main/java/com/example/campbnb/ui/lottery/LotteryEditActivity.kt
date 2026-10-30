package com.example.campbnb.ui.lottery

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.ViewObserver

class LotteryEditActivity : ViewObserver<LotteryModel> {
    override fun update(model: LotteryModel) {
    }

    @Composable
    fun LotteryEdit(
        model: LotteryModel,
        controller: LotteryController,
        navController: NavController
    ) {


    }

}