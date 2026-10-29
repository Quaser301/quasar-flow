package com.example.campbnb

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController


interface ViewObserver<M> {
    fun update (model: M)
}


open class LotteryModel {
    private val views = mutableListOf<ViewObserver<LotteryModel>>()
    val lotteryList = mutableListOf<Lottery>()
    var lotteryStatus = " "

    fun addView(view : ViewObserver<LotteryModel>) {
        if (view !in views) {
            views.add(view)
        }
    }

    fun removeView(view: ViewObserver<LotteryModel>) {
        views.remove(view)
    }


    protected fun notifyViews(model: LotteryModel) {
        views.forEach { view ->
            view.update(model)
        }
    }
}

