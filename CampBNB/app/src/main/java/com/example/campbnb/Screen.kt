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

    /** The entry screen the app opens on.
     * @property role decides which bottom-bar tabs are reachable.
     * @property tab which bottom-bar tab is open; the destination it shows is decided
     *     by the role as well. */
    @Serializable
    data class Home(val role: UserRole, val tab: Int = 0): Screen

    // the screen where a Campsite Owner registers a new campsite.
    @Serializable
    data object CampsiteEditActivity : Screen

    /** The owner's editing surface for one campsite.
     * @property campsiteId the campsite being edited. */
    @Serializable
    data class EditCampsite(val campsiteId: String): Screen

    // the screen where a Campsite Owner edits their lottery settings.
    // the screen where a Campsite Owner edits/creates their lotteries.
    @Serializable
    data object LotteryEditActivity: Screen

    // the screen where a Campsite Owner views their lotteries.
    @Serializable
    data object LotteryMonitorActivity: Screen

    @Serializable
    data object LotteryDetailsActivity: Screen
    @Serializable
    data object LotteryWaitlistActivity: Screen
    @Serializable
    data object Start: Screen

    @Serializable
    data object Login: Screen

    @Serializable
    data object SignUp: Screen

    @Serializable
    data object Role: Screen

    /** Settings surface; which settings are visible depends on the role.
     * @property role the role whose settings groups are offered. */
    @Serializable
    data class Settings(val role: UserRole): Screen


    /** Browse for campsites, optionally narrowed by a search.
     * @property role the role browsing; an admin sees everything.
     * @property query the current search text; empty means plain browse. */
    @Serializable
    data class Search(val role: UserRole, val query: String = "", val openFilter: Boolean = false) : Screen
    // the screen where a Campsite Owner views reservations.
    @Serializable
    data object Reservations : Screen
    /** Vocabulary entry for one campsite's detail page. No window constructs this
     *     screen today; the reachable detail screen is [CampsiteDetailsScreen]. */
    @Serializable
    data class CampsiteDetail(val role: UserRole, val campsiteId: String) : Screen

    // the screen where a Campsite Owner views their campsites.
    @Serializable
    data class CampsiteMonitorActivity(val role: UserRole) : Screen



    // one campsite's details page.
    /** One campsite's detail page, reached from Browse or from Home.
     * @property role who is viewing it.
     * @property campsiteId which campsite. */
    @Serializable
    data class CampsiteDetailsScreen(val role: UserRole, val campsiteId: String): Screen

    @Serializable
    data object AdminProfileView: Screen

}