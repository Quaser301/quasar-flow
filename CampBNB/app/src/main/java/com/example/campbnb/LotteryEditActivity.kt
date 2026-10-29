package com.example.campbnb

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

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

