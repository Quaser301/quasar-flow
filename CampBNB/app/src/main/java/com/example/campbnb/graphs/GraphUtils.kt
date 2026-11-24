package com.example.campbnb.graphs

import kotlinx.serialization.Serializable


@Serializable
sealed interface NavGraphNodes {
    @Serializable
    data object CorrespondenceGraph : NavGraphNodes

    @Serializable
    data object ExploreGraph : NavGraphNodes

    @Serializable
    data object ReservationGraph : NavGraphNodes

    @Serializable
    data object CampsiteGraph : NavGraphNodes

    @Serializable
    data object LotteryGraph : NavGraphNodes


    @Serializable
    data object DebugGraph : NavGraphNodes
}