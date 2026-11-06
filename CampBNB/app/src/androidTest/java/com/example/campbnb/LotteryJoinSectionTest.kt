package com.example.campbnb

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.Database
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.WaitingListEntry
import com.example.campbnb.ui.campsite.LotteryJoinSection
import org.junit.Rule
import org.junit.Test

/** In-memory repository for the UI test; never touches the server. */
class FakeLotteryForUi(private val lotteries: List<Lottery>) : LotteryRepository() {
    private val entries = mutableListOf<WaitingListEntry>()

    override fun getAllLotteries(onSuccess: (List<Lottery>) -> Unit, onFailure: (Exception) -> Unit) {
        onSuccess(lotteries)
    }

    override fun getEntries(
        lotteryId: String,
        onSuccess: (List<WaitingListEntry>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(entries.filter { entry -> entry.lotteryId == lotteryId })
    }

    override fun addEntry(entry: WaitingListEntry, onComplete: (Result<Exception?>) -> Unit) {
        entries.add(entry)
        onComplete(Result.success(null))
    }

    override fun removeEntry(lotteryId: String, userId: String, onComplete: (Result<Exception?>) -> Unit) {
        entries.removeAll { entry -> entry.lotteryId == lotteryId && entry.userId == userId }
        onComplete(Result.success(null))
    }
}

class LotteryJoinSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun openLottery(): Lottery {
        val now = System.currentTimeMillis()
        return Lottery(
            ownerId = "l1",
            campsiteIds = listOf("c1"),
            registrationStart = now - 60_000,
            registrationEnd = now + 3_600_000,
            bookingStart = now + 7_200_000,
            bookingEnd = now + 10_800_000,
            numberOfWinners = 1,
            waitlistCapacity = 10
        )
    }

    private fun makeController(model: LotteryModel, lotteries: List<Lottery>): LotteryController {
        // The engine is never used by join/leave; it only fills the constructor.
        return LotteryController(model, FakeLotteryForUi(lotteries), Database.Lotteries.readEngine())
    }

    @Test
    fun joiningAndLeaving_changesTheButtonAndTheStatus() {
        val model = LotteryModel()
        val controller = makeController(model, listOf(openLottery()))
        composeRule.setContent {
            LotteryJoinSection(model, controller, "c1", "u1")
        }
        composeRule.onNodeWithText("Lottery open for entries (0 waiting)").assertIsDisplayed()

        composeRule.onNodeWithTag("join_lottery").performClick()
        composeRule.onNodeWithText("You are on the waiting list (1 waiting)").assertIsDisplayed()
        composeRule.onNodeWithTag("leave_lottery").assertIsDisplayed()

        composeRule.onNodeWithTag("leave_lottery").performClick()
        composeRule.onNodeWithText("Lottery open for entries (0 waiting)").assertIsDisplayed()
        composeRule.onNodeWithTag("join_lottery").assertIsDisplayed()
    }

    @Test
    fun aCampsiteWithNoOpenLottery_hasNoJoinButton() {
        val model = LotteryModel()
        val controller = makeController(model, emptyList())
        composeRule.setContent {
            LotteryJoinSection(model, controller, "c1", "u1")
        }
        composeRule.onNodeWithText("No active lottery").assertIsDisplayed()
        composeRule.onNodeWithTag("join_lottery").assertDoesNotExist()
    }
}