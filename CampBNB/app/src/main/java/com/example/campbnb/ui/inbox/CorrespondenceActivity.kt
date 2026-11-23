package com.example.campbnb.ui.inbox
/**
 * the screen that holds the correspondence tabs (US 02.12.01 and US 02.10.02 call it
 * "Correspondence"): the notices a viewer is shown, the notices they received, and their
 * messages. Which tabs a viewer has and what each one lists are decided by the caller,
 * where the viewer's permissions are known; this file only does layout.
 */
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * The names the correspondence tabs are drawn under. A caller picks tabs and names an
 * opening one from here, so it cannot ask for a tab this screen has no content for.
 */
object CorrespondenceTabs {
    const val NOTIFICATIONS_TAB = "Notifications"
    const val RECEIVED_TAB = "Received"
    const val MESSAGES_TAB = "Messages"
}

/**
 * Shared screen shell for the correspondence tabs: a Back control, the Correspondence
 * header, the tab bars, and one tab's content below them.
 *
 * The tab names and the content of each are handed in, so this screen holds no idea
 * about roles or permissions: a caller that may show a viewer only their own notices
 * asks for the tabs they have and hands the content those tabs list.
 *
 * @param navController The app's navigation, used by the Back control.
 * @param tabLabels Which tabs this viewer has, in the order they are drawn, taken from
 *     [CorrespondenceTabs].
 * @param openTab Which tab opens, named rather than counted, so a caller that asks for a
 *     tab this viewer does not have still opens a tab instead of the wrong one.
 * @param notificationsTabContent What the "Notifications" tab lists for this viewer.
 * @param receivedTabContent What the "Received" tab lists; only drawn when that tab was
 *     asked for.
 * @param messagesTabContent What the "Messages" tab lists.
 */
@Composable
fun CorrespondenceScreen(
<<<<<<< HEAD
    navController: NavController,
    tabLabels: List<String>,
    openTab: String,
    notificationsTabContent: @Composable () -> Unit,
    receivedTabContent: @Composable () -> Unit = {},
    messagesTabContent: @Composable () -> Unit,
=======
    initialTab: Int,
    notificationsContent: @Composable () -> Unit,
    messagesContent: @Composable () -> Unit,
    onNavBack: () -> Unit,
>>>>>>> 898bfa2 (Admitting defeat, and letting Qwen finish. Need sleep.)
) {
    var selectedTab by rememberSaveable {
        mutableIntStateOf(tabLabels.indexOf(openTab).coerceAtLeast(0))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            TextButton(onClick = onNavBack) {
                Text("Back")
            }
            Text(
                text = "Correspondence",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            tabLabels.forEachIndexed { index, label ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            role = Role.Tab
                        )
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        fontWeight = if (selectedTab == index) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (selectedTab == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    HorizontalDivider(
                        thickness = if (selectedTab == index) 3.dp else 1.dp,
                        color = if (selectedTab == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                }
            }
        }

        if (selectedTab >= 0 && selectedTab < tabLabels.count()) {
            when (tabLabels[selectedTab]) {
                CorrespondenceTabs.MESSAGES_TAB -> messagesTabContent()
                CorrespondenceTabs.RECEIVED_TAB -> receivedTabContent()
                else -> notificationsTabContent()
            }
        }
    }
}
