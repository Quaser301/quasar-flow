package com.example.campbnb

import com.example.campbnb.ui.components.UserRole
import kotlinx.serialization.Serializable
import com.example.campbnb.ui.shared.AppViewModificationIsValidForCurrentWindowChecker
import com.example.campbnb.viewModels.AppView

// Developer Note: the interface is sealed and its members are named data objects so the
// compiler, not the runtime, knows the complete set of screens: every `is`-check on a
// Screen can be verified exhaustive, no screen can be constructed outside this list, and
// a renamed or removed screen breaks every window that still refers to it at compile
// time. That is what keeps a navigate() call between screens safe.

/**
 * Enumerates every screen the app can display, as one named type per screen.
 *
 * Windows and shared window components refer to screens by type instead of by string,
 * so any navigation between screens is checked against this sealed set at compile time.
 *
 * @see AppViewModificationIsValidForCurrentWindowChecker The shared component that
 * watches the current [AppView] within a screen and returns to a safe screen.
 */
@Serializable
sealed interface Screen {
    // the app's entry point.

    @Serializable
    data class Home(val roleName: UserRole): Screen

    // the screen where a Campsite Owner registers a new campsite.
    @Serializable
    data object CampsiteEditActivity : Screen

    @Serializable
    data class EditCampsite(val campsiteId: String): Screen

    // the screen where a Campsite Owner edits their lottery settings.
    @Serializable
    data object LotteryEditActivity: Screen

    // the screen where a Campsite Owner watches their lottery run.
    @Serializable
    data object LotteryMonitorActivity: Screen

    @Serializable
    data object Start: Screen

    @Serializable
    data object Login: Screen

    @Serializable
    data object SignUp: Screen

    @Serializable
    data object Role: Screen
}