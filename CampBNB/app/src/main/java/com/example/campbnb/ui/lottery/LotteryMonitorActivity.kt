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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.ViewObserver
import com.example.campbnb.ui.shared.AppViewModificationIsValidForCurrentWindowChecker
import com.example.campbnb.ui.shared.observeModel
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting
import com.google.firebase.auth.FirebaseAuth

/**
 * 05-11-26: Prompted Gemini 3.6 to integrate the card layout I created for assignment 1 to
 * display the owners active lotteries.
 * 05-11-26: Prompted Gemini 3.6 to create a wide but short rectangular button for the create
 * lottery button. Modifiers are tedious :).
 * 06-11-26: Prompted Gemini 3.6 to help me fix bug where the incorrect owner ID is passed in
 * making the lotteries not display. Provided a simple few line edit.
 * 06-11-26: Prompted Gemini 3.6 to fill out KDocs
 * 06-11-26: Prompted Gemini 3.6 to help fix bug where campsite names were being displayed
 * instead of their IDs
 *
 * @property goHome Callback function to navigate to the home screen.
 * @property goBack Callback function to navigate backwards.
 * @property goToCreateLottery Callback function to navigate to the create lottery screen.
 * @property goToLotteryDetails Callback function to navigate to details for a selected [Lottery].
 * @property appViewSettings Current view settings used for window verification and access checks.
 */
class LotteryMonitorActivity(
    val goHome: () -> Unit,
    val goBack: () -> Unit,
    val goToCreateLottery: () -> Unit,
    val goToLotteryDetails: (Lottery) -> Unit,
    val appViewSettings: AppViewSetting
) : ViewObserver<LotteryModel> {

    /**
     * Required by ViewObserver. The list recomposes through the Compose state in
     * [LotteryModel], so no manual update is necessary.
     *
     * @param model The updated [LotteryModel] instance.
     */
    override fun update(model: LotteryModel) {}

    /**
     * Renders the list of lotteries belonging to the specified owner.
     *
     * Fetches lotteries via [controller] on launch and displays them in cards showing
     * campsite names, status, and navigation to lottery details or creation.
     *
     * @param model The lottery state model shown by views.
     * @param controller The controller managing lottery database actions.
     * @param ownerId Unique identifier of the campsite owner whose lotteries are displayed.
     */
    @Composable
    fun LotteryMonitor(
        model: LotteryModel,
        controller: LotteryController,
        ownerId: String = "",
        deviceID: String
    ) {
        observeModel(model)
        AppViewModificationIsValidForCurrentWindowChecker(
            goHome,
            appViewSettings,
            listOf(
                AppView.EntrantView,
                AppView.AdminView
            )
        )

        val resolvedOwnerId = ownerId.ifBlank {
            FirebaseAuth.getInstance().currentUser?.uid ?: deviceID }

        var campsitesMap by remember { mutableStateOf<Map<String, Campsite>>(emptyMap()) }

        LaunchedEffect(resolvedOwnerId) {
            if (resolvedOwnerId.isNotBlank()) {
                controller.loadOwnerLotteries(resolvedOwnerId)
                controller.loadOwnerCampsites(resolvedOwnerId) { fetchedCampsites ->
                    campsitesMap = fetchedCampsites.associateBy { it.id }
                }
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
                    val campsiteNames = lottery.campsiteIds.map { id -> campsitesMap[id]?.campName ?: id }
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
                                    text = "Campsite(s): ${campsiteNames.joinToString(", ").ifEmpty { "None" }}",
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
