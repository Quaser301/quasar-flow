package com.example.campbnb.graphs

import kotlinx.serialization.Serializable
import com.example.campbnb.Screen


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

/**
 * The graph node a screen's content is registered under, so a caller may start on that
 * node instead of the screen. A NavHost may only start on a direct child of its own
 * graph, and a caller that registers whole feature graphs as nodes names those nodes,
 * not the screens inside them; entering such a node loads that graph's own start
 * destination. Screens a graph registers bare — MiscGraph's — have no node, and the
 * screen itself is the start.
 *
 * @param screen The screen to open on.
 */
fun graphNodeFor(screen: Screen): Any = when (screen) {
    Screen.Explore -> NavGraphNodes.ExploreGraph
    Screen.MyCampsites -> NavGraphNodes.CampsiteGraph
    Screen.Messages, Screen.Bookings, Screen.Inbox -> NavGraphNodes.CorrespondenceGraph
    Screen.Reservations -> NavGraphNodes.ReservationGraph
    Screen.AdminProfileView, Screen.AdminNotificationView, Screen.AdminImages -> NavGraphNodes.DebugGraph
    else -> screen
}