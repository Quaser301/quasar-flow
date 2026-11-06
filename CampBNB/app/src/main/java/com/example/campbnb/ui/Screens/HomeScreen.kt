package com.example.campbnb.ui.Screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.theme.ButtonGradient
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.viewModels.AppViewSetting

/** Every screen reachable from a bottom bar. */
enum class HomeDestination {
    EXPLORE, MESSAGES, BOOKINGS, INBOX, PROFILE, MY_CAMPSITES, REPORTS, FLAGGED
}

/** One circle in the bottom bar. */
data class HomeTab(val label: String, val destination: HomeDestination)

/** The bottom bar tabs for each role, in display order. Explore is always first. */
fun tabsFor(role: UserRole): List<HomeTab> = when (role) {
    UserRole.CAMPER -> listOf(
        HomeTab("Explore", HomeDestination.EXPLORE),
        HomeTab("Messages", HomeDestination.MESSAGES),
        HomeTab("Bookings", HomeDestination.BOOKINGS),
        HomeTab("Inbox", HomeDestination.INBOX),
        HomeTab("Profile", HomeDestination.PROFILE)
    )
    UserRole.OWNER -> listOf(
        HomeTab("Explore", HomeDestination.EXPLORE),
        HomeTab("Messages", HomeDestination.MESSAGES),
        HomeTab("My Campsites", HomeDestination.MY_CAMPSITES),
        HomeTab("Inbox", HomeDestination.INBOX),
        HomeTab("Profile", HomeDestination.PROFILE)
    )
    UserRole.ADMIN -> listOf(
        HomeTab("Explore", HomeDestination.EXPLORE),
        HomeTab("Messages", HomeDestination.MESSAGES),
        HomeTab("Reports", HomeDestination.REPORTS),
        HomeTab("Flagged", HomeDestination.FLAGGED)
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
    initialTab: Int = 0,
    exploreContent: @Composable () -> Unit = { ExploreScreen() },
    destinationContent: @Composable (HomeDestination) -> Unit = { PlaceholderTab(it) },
    appViewSettings: AppViewSetting
) {
    val tabs = remember(role) { tabsFor(role) }
    var selected by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(tabs.indices)) }

    BackHandler(enabled = selected != 0) { selected = 0 }

    Scaffold(
        containerColor = Color.White,
        bottomBar = { RoleBottomBar(tabs, selected, onSelect = { selected = it }) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val destination = tabs[selected].destination
            if (destination == HomeDestination.EXPLORE) exploreContent()
            else destinationContent(destination)
        }
    }
}

/** Green gradient bar with rounded top corners and one circle per tab. */
@Composable
fun RoleBottomBar(tabs: List<HomeTab>, selectedIndex: Int, onSelect: (Int) -> Unit) {
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