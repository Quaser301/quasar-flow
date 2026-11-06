package com.example.campbnb.ui.campsite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.ui.shared.observeModel

// Todo: no countdown to the end of registration yet.

/**
 * View: the lottery status block for the Details screen (US 01.01.06, 01.02.01, 01.02.02).
 * Loads whether the campsite has an open lottery and whether the user is on its waiting
 * list, and shows that plus any join/leave message. The Join/Leave action itself is the
 * Details screen's floating button, labelled by [lotteryButtonText].
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

    Column(modifier = modifier) {
        Text(
            text = lotteryStatusText(model.entrantLottery, model.entrantIsOnWaitlist, model.entrantPosition),
            modifier = Modifier.testTag("lottery_status")
        )
        if (model.statusMessage.isNotBlank()) {
            Text(
                text = model.statusMessage,
                modifier = Modifier.padding(top = 4.dp).testTag("lottery_message")
            )
        }
    }
}

/** The status line shown on the Details screen. */
fun lotteryStatusText(lottery: Lottery?, joined: Boolean, position: Int): String {
    if (lottery == null) {
        return "No active lottery"
    }
    if (joined) {
        return "You are on the waiting list (Position: $position)"
    }
    return "Lottery open for entries"
}

/** The floating button's label for the current lottery state. */
fun lotteryButtonText(model: LotteryModel): String {
    if (model.entrantLottery == null) {
        return "No Active Lottery"
    }
    if (model.entrantIsOnWaitlist) {
        return "Leave Lottery"
    }
    return "Enter Lottery"
}