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

}