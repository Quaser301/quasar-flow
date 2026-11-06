package com.example.campbnb.models

/**
 * A view's end of the observer channel: concrete views implement update to re-render
 * themselves when a model pushes new state.
 */
interface ViewObserver<M> {
    /** Called with the model's current state each time it changes. */
    fun update(model: M)
}