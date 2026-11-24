package com.example.campbnb.destinations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.navigation.NavController
import com.example.campbnb.Dependencies
import com.example.campbnb.Screen
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel


/**
 * The invitation panel opened by a lottery INVITE notification: the invited owner reads
 * what they were invited to and accepts with one tap, which stores the lottery as joined
 * and then opens the owner lottery list, where it now shows. The panel only appears while
 * the invitation is pending for the current viewer, so taps from WIN notifications keep
 * navigating to the reservations screen as before.
 *
 * @param nav The app's NavController.
 * @param lotteryId The route argument naming the invited lottery.
 * @param deps The app-wide dependencies, which name the viewer by device id.
 */
@Composable
fun InvitationDestination(
    viewerId: String,

    notificationController: NotificationController,

    navToLotteryMon: () -> Unit,
    navToReservations: () -> Unit,
    goToReservations: () -> Unit, // Go to reservation screen and discard the previous stack entries

    lotteryId: String? = null,
) {
    var invitedLottery by remember { mutableStateOf<Lottery?>(null) }
    var invitationResolved by remember { mutableStateOf(false) }

    LaunchedEffect(lotteryId) {
        if (!lotteryId.isNullOrEmpty()) {
            Database.Lotteries.readEngine().getById(
                id = lotteryId,
                onSuccess = { found ->
                    invitedLottery = found
                    invitationResolved = true
                },
                onFailure = {
                    invitationResolved = true
                }
            )
        } else {
            invitationResolved = true
        }
    }

    val invited = invitedLottery
    if (invited != null && viewerId in invited.invitedOwnerIds && viewerId !in invited.joinedOwnerIds) {
        val invitationModel = remember { LotteryModel() }
        val invitationRepository = remember { LotteryRepository() }
        val invitationController = remember {
            LotteryController(
                invitationModel,
                invitationRepository,
                Database.Lotteries.readEngine(),
                notificationController
            ) { System.currentTimeMillis() }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Lottery Invitation",
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "You were invited to join this lottery. Accept to see it in your list and add your own campsites."
            )
            Button(
                onClick = {
                    invitationController.acceptInvitation(
                        lottery = invited,
                        userId = viewerId,
                        onSuccess = navToLotteryMon
                    )
                }
            ) {
                Text("Accept invitation")
            }
            TextButton(onClick = navToReservations) {
                Text("Not now")
            }
        }
    } else if (invitationResolved) {
        LaunchedEffect(true) {
            goToReservations()
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
