package com.example.campbnb.ui.lottery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.WaitingListEntry
import com.example.campbnb.ui.shared.AppViewModificationIsValidForCurrentWindowChecker
import com.example.campbnb.ui.theme.ScreenGradient
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copied template from LotteryDetailsActivity.kt.
 * 06-11-26: Prompted gemini 3.6 to modify the template code to modify the ui to
 * display the waitlist entrants using the query and controller functions I set up.
 * 06-11-26: Prompted gemini 3.6 to fill out kdocs
 *
 * @property returnHome Callback function to navigate back to the home screen.
 * @property navigateBackwards Callback function to navigate backwards in the navigation stack.
 * @property onViewDetails Optional callback function to view details of [Lottery].
 * @property appViewSettings Current view settings used for window verification and access checks.
 */
class LotteryWaitlistActivity(
    val returnHome: () -> Unit,
    val navigateBackwards: () -> Unit,
    val onViewDetails: (Lottery?) -> Unit = {},
    val appViewSettings: AppViewSetting
) {
    /**
     * Renders the entrant waitlist for a selected lottery.
     *
     * Loads waitlist entries via [controller] on launch and displays them in a scrollable
     * list showing each entrant's user ID and timestamp when they joined.
     *
     * @param model The lottery state model shown by views.
     * @param controller The controller managing lottery database actions.
     * @param selectedLottery The [Lottery] instance whose waitlist is displayed; null if none selected.
     */
    @Composable
    fun LotteryWaitlist(
        model: LotteryModel,
        controller: LotteryController,
        selectedLottery: Lottery? = null
    ) {
        AppViewModificationIsValidForCurrentWindowChecker(
            returnHome,
            appViewSettings,
            listOf(
                AppView.EntrantView,
                AppView.AdminView
            )
        )

        var entriesList by remember { mutableStateOf<List<WaitingListEntry>>(emptyList()) }

        LaunchedEffect(selectedLottery) {
            if (selectedLottery != null && selectedLottery.id.isNotBlank()) {
                controller.loadWaitlistEntries(selectedLottery.id) { entries ->
                    entriesList = entries
                }
            }
        }

        /** Join time for a waitlist row; unset reads as unavailable. */
        fun formatDate(timestamp: Long): String {
            if (timestamp == 0L) return "N/A"
            val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return formatter.format(Date(timestamp))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row with Back Button & Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = navigateBackwards) {
                        Text("Back")
                    }
                    Text(
                        text = "Lottery Waitlist",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Surface Container
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Entrant Waitlist (${entriesList.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        if (selectedLottery == null) {
                            Text(
                                text = "No lottery selected.",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        } else if (entriesList.isEmpty()) {
                            Text(
                                text = "No entrants on this waitlist yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(entriesList) { entry ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "User ID: ${entry.userId}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Joined: ${formatDate(entry.joinedAt)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
