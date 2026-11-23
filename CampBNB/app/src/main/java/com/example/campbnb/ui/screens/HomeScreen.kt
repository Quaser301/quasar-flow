package com.example.campbnb.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.campbnb.Screen
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.theme.ButtonGradient
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting

/** One circle in the bottom bar. */
data class HomeTab(val label: String, val destination: Screen)

/** The bottom bar tabs for each role, in display order. Explore is always first. */
fun tabsFor(role: UserRole): List<HomeTab> = when (role) {
    UserRole.CAMPER -> listOf(
        HomeTab("Explore", Screen.Explore),
        HomeTab("Messages", Screen.Messages),
        HomeTab("Bookings", Screen.Bookings),
        HomeTab("Reservations", Screen.Reservations),
        HomeTab("Inbox", Screen.Inbox),
        HomeTab("Profile", Screen.Profile)
    )
    UserRole.OWNER -> listOf(
        HomeTab("Explore", Screen.Explore),
        HomeTab("Messages", Screen.Messages),
        HomeTab("My Campsites", Screen.MyCampsites),
        HomeTab("Inbox", Screen.Inbox),
        HomeTab("Profile", Screen.Profile)
    )
    UserRole.ADMIN -> listOf(
        HomeTab("Explore", Screen.Explore),
        HomeTab("Messages", Screen.Messages),
        HomeTab("Reports", Screen.Reports),
        HomeTab("Flagged", Screen.Flagged),
        HomeTab("Profiles", Screen.AdminProfileView),
        HomeTab("Notifications", Screen.AdminNotificationView)
    )
}

/**
 * The app's home: the shared Explore page with the signed-in role's bottom bar.
 *
 * The role only decides which tabs appear. Explore is the first tab and looks the same for
 * every role; the live view setting decides the tabs too, so switching views while signed
 * in moves the bar without re-asking for a role.
 *
 * The bar moves between tabs on its own graph rather than through the app's navigation:
 * Explore stays under every other tab, and the graph's saved state returns to it.
 *
 * @param role Decides which tabs appear in the bottom bar.
 * @param appViewSettings The app's view setting: the live view decides the tabs.
 * @param initialTab Which tab the nested graph opens on. A caller that sends the
 *     app back into Home names the tab to land on (the my-campsites back
 *     affordances and the details-page picker name it); the default opens the
 *     first tab, and an index outside the live tab list clamps into it.
 * @param homeNavController The controller for the tab graph below Home. The caller
 *     supplies one so its tab content can hand back controls that move on this graph.
 * @param builder Registers the content of the tabs: the caller owns one node per tab
 *     its bottom bar can name.
 */
@Composable
fun HomeScreen(
    role: UserRole,
    appViewSettings: AppViewSetting,
    initialTab: Int = 0,
    homeNavController: NavHostController = rememberNavController(),
    builder: NavGraphBuilder.() -> Unit
) {
    val viewState by appViewSettings.setting.collectAsStateWithLifecycle()

    val tabs = remember(viewState, role) {
        when (viewState) {
            AppView.AdminView -> tabsFor(UserRole.ADMIN)
            AppView.OwnerView -> tabsFor(UserRole.OWNER)
            AppView.EntrantView -> tabsFor(UserRole.CAMPER)
        }
    }

    val navBackStackEntry by homeNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            RoleBottomBar(
                tabs = tabs,
                currentDestination = currentDestination,
                onSelect = { tab ->
                    homeNavController.navigate(tab.destination) {
                        popUpTo(homeNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { padding ->
        NavHost(
            navController = homeNavController,
            startDestination = tabs[initialTab.coerceIn(0, tabs.size - 1)].destination as Any,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            builder = builder // Zero screen composables referenced inside HomeScreen
        )
    }
}


/** Green gradient bar with rounded top corners and one circle per tab. */
@Composable
fun RoleBottomBar(
    tabs: List<HomeTab>,
    currentDestination: NavDestination?,
    onSelect: (HomeTab) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ButtonGradient, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            // Which tab is lit is read from the live nav position itself: nav-compose
            // names a screen with its class name plus a slash-separated tail, and
            // routePrefixOf below derives that name from the tab's own screen object,
            // so nothing about a screen is spelled out here.
            val routePrefix = routePrefixOf(tab.destination)
            val isSelected = currentDestination?.hierarchy?.any { dest ->
                dest.route == routePrefix || dest.route?.startsWith("$routePrefix/") == true
            } == true

            NavCircle(tab.label, selected = isSelected) { onSelect(tab) }
        }
    }
}


/**
 * The bar as the details page sees it: a tab picker with no live graph under it, so
 * the caller names the lit position by index instead of a nav position.
 *
 * @param tabs The role's tabs, in bar order.
 * @param selectedIndex Which circle is lit right now.
 * @param onSelect Runs with the index of the tab the circle was tapped.
 */
@Composable
fun RoleBottomBar(
    tabs: List<HomeTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ButtonGradient, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            NavCircle(tab.label, selected = index == selectedIndex) { onSelect(index) }
        }
    }
}


@Composable
private fun NavCircle(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White else FieldGrey)
            .testTag("home_tab")
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            color = Color.Black
        )
    }
}

// The route prefix nav-compose gives one of the bottom bar's screens: the JVM class
// name of the screen object, read off the object itself rather than spelled out here.
private fun routePrefixOf(screen: Screen): String = screen.toString().takeWhile { it != '@' }


/** Temporary content for tabs that don't have a screen yet. */
@Composable
fun PlaceholderTab(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            title,
            fontSize = 22.sp
        )
    }
}