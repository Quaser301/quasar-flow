package com.example.campbnb

import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen {
    @Serializable
    data object Home: Screen

    @Serializable
    data object LotteryEditActivity: Screen

    @Serializable
    data object LotteryMonitorActivity: Screen
}