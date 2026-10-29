package com.example.campbnb.models

interface ViewObserver<M> {
    fun update(model: M)
}