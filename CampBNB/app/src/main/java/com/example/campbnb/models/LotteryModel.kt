package com.example.campbnb.models

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Observable state for the lottery screens: the lotteries drawn, the one the entrant is
 * in, and their waitlist place. Unlike the other models this one keeps its fields in
 * mutable state boxes and its setters do not notify views; see the marker comment
 * below. */
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

    /** Replaces the drawn lotteries; does not notify views. */
    fun setLotteries(list: List<Lottery>) {
        lotteries.value = list
    }

    /** Records a status message for the lottery screens. */
    fun setStatusMessage(msg: String) {
        message.value = msg
    }

    /** Sets what the entrant sees right now: the lottery they are in, and their
     * place on its waitlist. */
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