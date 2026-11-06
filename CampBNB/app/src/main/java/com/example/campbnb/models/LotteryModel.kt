package com.example.campbnb.models

class LotteryModel : ObservableModel<LotteryModel>(){
    var lotteryList: List<Lottery> = emptyList()
        private set
    var statusMessage: String = ""
        private set

    fun setLotteries(list: List<Lottery>) {
        lotteryList = list;
        notifyViews()
    }
    fun setStatusMessage(msg: String) {
        statusMessage = msg;
        notifyViews()
    }

    /** The open lottery for the campsite on screen, or null if there is none. */
    var activeLottery: Lottery? = null
        private set

    /** True if the current user is on the active lottery's waiting list. */
    var joinedActive: Boolean = false
        private set

    /** How many entrants are on the active lottery's waiting list. */
    var waitlistSize: Int = 0
        private set

    /** Sets the open lottery (or null), whether the user is on its list, and the list size. */
    fun setEntrantStatus(lottery: Lottery?, joined: Boolean, size: Int) {
        activeLottery = lottery
        joinedActive = joined
        waitlistSize = size
        notifyViews()
    }

}