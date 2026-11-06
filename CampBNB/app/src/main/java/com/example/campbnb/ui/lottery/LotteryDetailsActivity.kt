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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.ui.components.GradientButton
import com.example.campbnb.ui.shared.AppViewModificationIsValidForCurrentWindowChecker
import com.example.campbnb.ui.theme.ScreenGradient
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 05-11-26: Prompted gemini-3.5 to create the skeleton ui. Requested A display of the selected
 * lottery details with two buttons at the bottom that's cohesive with the UI theme of the
 * home screen.
 */

class LotteryDetailsActivity(
    val returnHome: () -> Unit,
    val navigateBackwards: () -> Unit,
    val onEditLottery: (Lottery?) -> Unit = {},
    val onViewWaitlist: (Lottery?) -> Unit = {},
    val appViewSettings: AppViewSetting
) {
    @Composable
    fun LotteryDetails(
        model: LotteryModel,
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

        fun formatDateRange(start: Long, end: Long): String {
            if (start == 0L || end == 0L) return "Not set"
            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return "${formatter.format(Date(start))} - ${formatter.format(Date(end))}"
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
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
                        text = "Lottery Details",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // White Card Container following app theme
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (selectedLottery == null) {
                            Text(
                                text = "No lottery selected.",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        } else {
                            Text(
                                text = "Lottery Information",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            InfoRow("Status", "${selectedLottery.statusAt(System.currentTimeMillis())}")
                            InfoRow("Campsite(s)", selectedLottery.campsiteIds.joinToString(", ").ifEmpty { "None" })
                            InfoRow("Number of Winners", "${selectedLottery.numberOfWinners}")
                            InfoRow("Waitlist Capacity", selectedLottery.waitlistCapacity?.toString() ?: "Unlimited")
                            InfoRow("Registration Period", formatDateRange(selectedLottery.registrationStart, selectedLottery.registrationEnd))
                            InfoRow("Booking Period", formatDateRange(selectedLottery.bookingStart, selectedLottery.bookingEnd))

                            Spacer(modifier = Modifier.height(8.dp))

                            // View Waitlist Button
                            GradientButton(
                                text = "View Waitlist",
                                onClick = { onViewWaitlist(selectedLottery) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                GradientButton(
                    text = "Edit Lottery",
                    onClick = { onEditLottery(selectedLottery) }
                )
            }
        }
    }

    @Composable
    private fun InfoRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
