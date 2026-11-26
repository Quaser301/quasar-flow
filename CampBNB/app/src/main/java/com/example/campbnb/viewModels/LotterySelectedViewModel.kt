package com.example.campbnb.viewModels

import androidx.lifecycle.ViewModel
import com.example.campbnb.models.Lottery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LotterySelectedViewModel: ViewModel() {

    private val _selectedLottery = MutableStateFlow<Lottery?>(null)
    val selectedLottery = _selectedLottery.asStateFlow()

    fun updateLottery(new: Lottery?) {
        _selectedLottery.value = new
    }
}