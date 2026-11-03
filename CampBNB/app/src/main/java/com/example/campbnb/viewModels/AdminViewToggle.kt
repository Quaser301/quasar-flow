package com.example.campbnb.viewModels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class AppViewSettingController: ViewModel() {
    // default to EntrantView because it has the least potential for Bad Stuff
    private val _viewSetting = MutableStateFlow(AppView.EntrantView)
    val viewSetting = _viewSetting.asStateFlow()

    fun changeViewTo(newView: AppView) {
        _viewSetting.value = newView
    }
}

enum class AppView {
    AdminView,
    EntrantView,
    OwnerView
}