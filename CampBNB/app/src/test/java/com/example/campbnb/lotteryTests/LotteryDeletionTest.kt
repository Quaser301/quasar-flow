package com.example.campbnb.lotteryTests

// AI-assisted (US 02.04.08 night batch, an owner deleting a finished lottery): written by Qwen 3.8 Flash
// Next Coder IQ1_M.
//
// What these cases cover: a lottery whose booking window is still ahead is never taken out of
// the records and answers that nothing happened, so an owner cannot drop work they still have
// to do; a lottery past its booking window is deleted once and the model's list no longer holds
// it, which is what answers the card the owner pressed on with the card simply gone. The screen
// is not driven here; the same [LotteryController] the LotteryMonitor mount builds is, over a
// stubbed repository.

import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.QueryEngine
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.LotteryStatus

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryDeletionTest {
    private val dayMillis = 24L * 60L * 60L * 1000L

    /** A controller over a stubbed repository; nothing touches Firestore. */
    private fun controllerOn(
        model: LotteryModel,
        repository: LotteryRepository,
        clock: () -> Long
    ): LotteryController = LotteryController(
        model,
        repository,
        mockk<QueryEngine<Lottery>>(),
        mockk<NotificationController>(relaxed = true),
        clock
    )

    /** A lottery whose booking window has already passed at [now]: finished. */
    private fun finishedAt(now: Long): Lottery = Lottery(
        ownerId = "owner-1",
        campsiteIds = listOf("camp-one"),
        registrationStart = now - 6 * dayMillis,
        registrationEnd = now - 5 * dayMillis,
        bookingStart = now - 2 * dayMillis,
        bookingEnd = now - dayMillis,
        numberOfWinners = 1
    )

    /** A lottery whose booking window is still ahead at [now]: not finished. */
    private fun stillBookableAt(now: Long): Lottery = Lottery(
        ownerId = "owner-1",
        campsiteIds = listOf("camp-one"),
        registrationStart = now - 6 * dayMillis,
        registrationEnd = now - 5 * dayMillis,
        bookingStart = now + dayMillis,
        bookingEnd = now + 2 * dayMillis,
        numberOfWinners = 1
    )

    /** Stubs the repository so a delete answers success, and records whether it was asked at all. */
    private fun deleteAnswers(
        repository: LotteryRepository,
        asked: (Boolean) -> Unit
    ) {
        val onDelete = slot<Function1<Result<Exception?>, Unit>>()
        every {
            repository.deleteLottery(any(), capture(onDelete))
        } answers {
            asked(true)
            onDelete.captured.invoke(Result.success(null))
        }
    }

    @Test
    fun test_A_Finished_Lottery_LeavesTheRecordsAndTheOwnersList() {
        val now = 10000000000000L
        val model = LotteryModel()
        val repository = mockk<LotteryRepository>()
        val controller = controllerOn(model, repository) { now }
        val lottery = finishedAt(now)
        model.setLotteries(listOf(lottery))

        var askedToDelete = false
        deleteAnswers(repository) { wasAsked -> askedToDelete = wasAsked }

        var answeredThatItWasDeleted = false
        controller.deleteLottery(lottery) { done -> answeredThatItWasDeleted = done }

        assertTrue("the finished lottery was taken out of the records", askedToDelete)
        assertTrue("the controller answered that the delete happened", answeredThatItWasDeleted)
        assertTrue("the owner's list no longer holds the deleted lottery", model.lotteryList.isEmpty())
    }

    @Test
    fun test_AnUnfinishedLottery_IsNeverDeleted() {
        val now = 10000000000000L
        val model = LotteryModel()
        val repository = mockk<LotteryRepository>()
        val controller = controllerOn(model, repository) { now }
        val lottery = stillBookableAt(now)
        model.setLotteries(listOf(lottery))

        var askedToDelete = false
        deleteAnswers(repository) { wasAsked -> askedToDelete = wasAsked }

        var answeredThatItWasDeleted = false
        controller.deleteLottery(lottery) { done -> answeredThatItWasDeleted = done }

        assertFalse("the records were never asked to delete it", askedToDelete)
        assertFalse("the controller answered that nothing was deleted", answeredThatItWasDeleted)
        assertEquals("the lottery is still listed", 1, model.lotteryList.size)
    }

    @Test
    fun test_OnlyTheFinishedStatus_IsDeletable() {
        val now = 10000000000000L
        assertTrue(
            "a lottery past its booking window is the finished one",
            finishedAt(now).statusAt(now) == LotteryStatus.CLOSED
        )
        assertTrue(
            "a lottery whose booking window is still ahead is not",
            stillBookableAt(now).statusAt(now) != LotteryStatus.CLOSED
        )
    }
}
