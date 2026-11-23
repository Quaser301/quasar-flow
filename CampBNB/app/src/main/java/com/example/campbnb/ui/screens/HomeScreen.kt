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

/** Every screen reachable from a bottom bar. */
enum class HomeDestination {
    EXPLORE,
    MESSAGES,
    BOOKINGS,
    RESERVATIONS,
    INBOX,
    PROFILE,
    MY_CAMPSITES,
    REPORTS,
    FLAGGED,
    ADMIN_PROFILE_VIEW,
    ADMIN_NOTIFICATION_VIEW
}

/** One circle in the bottom bar. */
data class HomeTab(val label: String, val destination: Screen)

/** The bottom bar tabs for each role, in display order. Explore is always first. */
fun tabsFor(role: UserRole): List<HomeTab> = when (role) {
    UserRole.CAMPER -> listOf(
<<<<<<< HEAD
        HomeTab("Explore", HomeDestination.EXPLORE),
        HomeTab("Messages", HomeDestination.MESSAGES),
        HomeTab("Bookings", HomeDestination.BOOKINGS),
        HomeTab("Reservations", HomeDestination.RESERVATIONS),
        HomeTab("Inbox", HomeDestination.INBOX),
        HomeTab("Profile", HomeDestination.PROFILE)
=======
        HomeTab("Explore", Screen.Explore),
        HomeTab("Messages", Screen.Messages),
        HomeTab("Bookings", Screen.Bookings),
        HomeTab("Inbox", Screen.Inbox),
        HomeTab("Profile", Screen.Profile)
>>>>>>> 898bfa2 (Admitting defeat, and letting Qwen finish. Need sleep.)
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
 * every role. Pressing Back on any other tab returns to Explore.
 *
 * @param role Decides which tabs appear in the bottom bar.
 * @param initialTab Index of the tab to open first. Clamped to the role's tabs.
 * @param exploreContent The Explore page. Pass your own to supply real campsite data.
 * @param destinationContent Screen to show for every tab except Explore.
 * @param appViewSettings The app's view setting. Not used for choosing tabs yet.
 */
@Composable
fun HomeScreen(
    role: UserRole,
    appViewSettings: AppViewSetting,
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

<<<<<<< HEAD
    // The tab to open first is the route's: goHome re-mounts Home naming the tab it
    // wants, so the seed honours initialTab (clamped to the role's tabs). A plain
    // remember resets on exactly such a remount, while the in-place tab switches below
    // keep the value between re-renders.
    var selected by remember {
        mutableIntStateOf(initialTab.coerceIn(0, (tabsFor(role).size - 1).coerceAtLeast(0)))
    }

    BackHandler(enabled = selected != 0) { selected = 0 }

    Scaffold(
        containerColor = Color.White,
        bottomBar = { RoleBottomBar(tabs, selected, onSelect = { selected = it }) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            // The live tab list follows the current view and can be shorter than the
            // one this screen was seeded with, so the index is clamped before use.
            val destination = tabs[selected.coerceIn(0, tabs.size - 1)].destination
            if (destination == HomeDestination.EXPLORE) exploreContent()
            else destinationContent(destination)
=======
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
>>>>>>> 898bfa2 (Admitting defeat, and letting Qwen finish. Need sleep.)
        }
    ) { padding ->
        NavHost(
            navController = homeNavController,
            startDestination = tabs.first().destination as Any,
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
            val routePrefix = tab.destination::class.qualifiedName
            val isSelected = routePrefix != null && currentDestination?.hierarchy?.any { dest ->
                dest.route == routePrefix || dest.route?.startsWith("$routePrefix/") == true
            } == true

            NavCircle(tab.label, selected = isSelected) { onSelect(tab) }
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

/** Temporary content for tabs that don't have a screen yet. */
@Composable
fun PlaceholderTab(destination: HomeDestination) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            destination.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
            fontSize = 22.sp
        )
    }
}