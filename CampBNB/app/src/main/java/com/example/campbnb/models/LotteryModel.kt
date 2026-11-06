package com.example.campbnb.models

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

class LotteryModel : ObservableModel<LotteryModel>() {

    private val lotteries = mutableStateOf<List<Lottery>>(emptyList())
    private val message = mutableStateOf("")

    private val currentLottery = mutableStateOf<Lottery?>(null)
    private val isOnWaitlist = mutableStateOf(false)
    private val waitlistPosition = mutableIntStateOf(0)

    val lotteryList: List<Lottery>
        get() = lotteries.value

    val statusMessage: String
        get() = message.value

    val entrantLottery: Lottery?
        get() = currentLottery.value

    val entrantIsOnWaitlist: Boolean
        get() = isOnWaitlist.value

    val entrantPosition: Int
        get() = waitlistPosition.intValue

    fun setLotteries(list: List<Lottery>) {
        lotteries.value = list
    }

    fun setStatusMessage(msg: String) {
        message.value = msg
    }

    fun setEntrantStatus(
        lottery: Lottery?,
        onWaitlist: Boolean,
        position: Int
    ) {
        currentLottery.value = lottery
        isOnWaitlist.value = onWaitlist
        waitlistPosition.intValue = position

        // notifyViews() if you still use observer updates
    }
}