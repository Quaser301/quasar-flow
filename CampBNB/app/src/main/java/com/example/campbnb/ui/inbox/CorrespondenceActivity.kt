package com.example.campbnb.ui.inbox
/**
 * the screen that holds the "Notifications" and "Messages" tabs (US 02.12.01 calls it "Correspondence").
 * The tab contents are passed in, so this file only does layout.
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
 * Shared screen shell for the Notifications and Messages tabs.
 */
@Composable
fun CorrespondenceScreen(
    navController: NavController,
    initialTab: Int,
    notificationsContent: @Composable () -> Unit,
    messagesContent: @Composable () -> Unit
) {
    var selectedTab by rememberSaveable {
        mutableIntStateOf(initialTab.coerceIn(0, 1))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            TextButton(onClick = { navController.popBackStack() }) {
                Text("Back")
            }
            Text(
                text = "Correspondence",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Notifications", "Messages").forEachIndexed { index, label ->
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

        if (selectedTab == 0) {
            notificationsContent()
        } else {
            messagesContent()
        }
    }
}
