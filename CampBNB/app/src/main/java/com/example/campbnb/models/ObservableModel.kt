package com.example.campbnb.models


/**
 * Observable state shared with views: holds the registered [ViewObserver]s and hands state
 * changes to all of them. Views register themselves via addView so the model never has to
 * know which screens exist.
 */
open class ObservableModel<M> {
    private val views = mutableListOf<ViewObserver<M>>()
    /** Registers a view; registering the same view twice has no effect. */
    fun addView(v: ViewObserver<M>) {
        if (v !in views) {
            views.add(v)
        }
    }
    /** Unregisters a view; an unregistered view is ignored. */
    fun removeView(v: ViewObserver<M>) {
        if (v in views) {
            views.remove(v)
        }
    }
    /** Pushes the current state to every registered view; called by the setters of
     * concrete models after they have assigned their fields. */
    protected fun notifyViews() {
        views.forEach { view ->
            view.update(this as M)
        }
    }
}