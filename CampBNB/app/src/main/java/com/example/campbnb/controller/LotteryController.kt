package com.example.campbnb.controller

import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.models.LotteryModel

class LotteryController (
    private val model: LotteryModel,
    private val repo: LotteryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
){
    // E, D, A, F
    fun onBackMonitorClicked() {
        TODO()
    }
}