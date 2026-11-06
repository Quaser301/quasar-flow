package com.example.campbnb.ui.campsite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.ui.shared.observeModel

// Todo: no countdown to the end of registration yet.

/**
 * View: the lottery block for the Details screen (US 01.01.06, 01.02.01, 01.02.02). Shows
 * whether the campsite has an open lottery and offers Join Lottery or Leave Lottery.
 * It loads the lottery status and redraws itself, so the Details screen only has to place it.
 */
@Composable
fun LotteryJoinSection(
    model: LotteryModel,
    controller: LotteryController,
    campsiteId: String,
    userId: String,
    modifier: Modifier = Modifier
) {
    observeModel(model)
    LaunchedEffect(campsiteId, userId) {
        controller.loadEntrantStatus(campsiteId, userId)
    }

    val lottery = model.entrantLottery
    val isOnWaitlist = model.entrantIsOnWaitlist
    val position = model.entrantPosition

    Column(modifier = modifier) {
        Text(
            text = lotteryStatusText(lottery, isOnWaitlist, position),
            modifier = Modifier.testTag("lottery_status")
        )
        if (lottery != null) {
            if (isOnWaitlist) {
                Button(
                    onClick = { controller.leave(lottery, userId) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("leave_lottery")
                ) {
                    Text("Leave Lottery")
                }
            } else {
                Button(
                    onClick = { controller.join(lottery, userId) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("join_lottery")
                ) {
                    Text("Join Lottery")
                }
            }
        }
        if (model.statusMessage.isNotBlank()) {
            Text(
                text = model.statusMessage,
                modifier = Modifier.padding(top = 4.dp).testTag("lottery_message")
            )
        }
    }
}

/** The line shown above the Join/Leave button. */
fun lotteryStatusText(lottery: Lottery?, joined: Boolean, position: Int): String {
    if (lottery == null) {
        return "No active lottery"
    }
    if (joined) {
        return "You are on the waiting list (Position: $position)"
    }
    return "Lottery open for entries"
}