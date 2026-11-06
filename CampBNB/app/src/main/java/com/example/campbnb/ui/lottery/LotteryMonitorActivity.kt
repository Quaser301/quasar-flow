package com.example.campbnb.ui.lottery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.ViewObserver
import com.example.campbnb.ui.shared.AppViewModificationIsValidForCurrentWindowChecker
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting

/**
 * 05-11-26: Prompted gemini 3.6 to integrate the card layout I created for assignment 1 to
 * display the owners active lotteries.
 * 05-11-26: Prompted gemini 3.6 to create a wide but short rectangular button for the create lottery
 * button. Modifiers are tedious :).
 */

class LotteryMonitorActivity(
    val goHome: () -> Unit,
    val goBack: () -> Unit,
    val goToCreateLottery: () -> Unit,
    val goToLotteryDetails: (Lottery) -> Unit,
    val appViewSettings: AppViewSetting
) : ViewObserver<LotteryModel> {
    override fun update(model: LotteryModel) { // why is this here???
    }

    // List of all lotteries
    @Composable
    fun LotteryMonitor(
        model: LotteryModel,
        controller: LotteryController,
        ownerId: String = ""
    ) {
        AppViewModificationIsValidForCurrentWindowChecker(
            goHome,
            appViewSettings,
            listOf(
                AppView.EntrantView,
                AppView.AdminView
            )
        )
        LaunchedEffect(ownerId) {
            if (ownerId.isNotBlank()) {
                controller.loadOwnerLotteries(ownerId)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {


            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { goBack() },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    text = "My Lotteries",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(model.lotteryList) { lottery ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Campsite(s): ${lottery.campsiteIds.joinToString(", ").ifEmpty { "None" }}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Status: ${lottery.statusAt(System.currentTimeMillis())}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Button(
                                onClick = { goToLotteryDetails(lottery) },
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("Details")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Wide, short rectangular button at the bottom
            Button(
                onClick = { goToCreateLottery() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Create Lottery",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
