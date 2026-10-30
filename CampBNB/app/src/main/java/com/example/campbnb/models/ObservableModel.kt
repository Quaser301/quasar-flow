package com.example.campbnb.models

open class ObservableModel<M> {
    private val views = mutableListOf<ViewObserver<M>>()
    fun addView(v: ViewObserver<M>) {
        if (v !in views) {
            views.add(v)
        }
    }
    fun removeView(v: ViewObserver<M>) {
        if (v in views) {
            views.remove(v)
        }
    }
    protected fun notifyViews(model: LotteryModel) {
        views.forEach { view ->
            view.update(model as M)
        }
    }
}