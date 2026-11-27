package com.example.campbnb.lotteryTests

// AI-assisted (US 01.05.02): written by Qwen 3.8 Flash IQ1_M. The verbatim prompts for
// this work are recorded in AI_Disclosure/Wyatt_AI_Use.md.

import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.QueryEngine
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryEntryStatus
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.WaitingListEntry

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOTICE: This file was AI-assisted; see the disclosure record above.
 *
 * The entrant's OWN side of a draw (main's US 02.08.02, controller.md): accepting or
 * declining a win rewrites the one waiting-list entry the pair names, keeping its
 * deterministic id; a win can be answered exactly once; declining needs no open
 * booking window while accepting does. The cases run the flow over a stubbed
 * repository and a stubbed one-record read and read what each side writes.
 */
class LotteryWinResponseTest {

    private fun controllerOn(
        repository: LotteryRepository,
        lotteries: QueryEngine<Lottery>
    ): LotteryController = LotteryController(
        LotteryModel(),
        repository,
        lotteries,
        mockk<NotificationController>(relaxed = true)
    ) { 1700000000000L }

    /** A drawn lottery whose booking window covers the injected clock. */
    private fun openLottery() = Lottery(
        ownerId = "owner-1",
        campsiteIds = listOf("camp-one"),
        registrationStart = 1600000000000L,
        registrationEnd = 1650000000000L,
        bookingStart = 1699999000000L,
        bookingEnd = 1700001000000L,
        numberOfWinners = 1,
        drawn = true,
        winnerIds = listOf("entrant-1")
    )

    private fun wonEntry() = WaitingListEntry(
        lotteryId = "lottery-1",
        userId = "entrant-1",
        joinedAt = 111L,
        status = LotteryEntryStatus.WON_AWAITING_RESPONSE
    )

    /** Answers the one record the window check reads with the given lottery. */
    private fun windowAnswering(
        lotteries: QueryEngine<Lottery>,
        found: Lottery?
    ) {
        val onFound = slot<Function1<Lottery?, Unit>>()
        every {
            lotteries.getById(any(), capture(onFound), any())
        } answers {
            onFound.captured.invoke(found)
        }
    }

    /** Answers the entrant read with the given entries. */
    private fun entriesAnswering(
        repository: LotteryRepository,
        entries: List<WaitingListEntry>
    ) {
        val onEntries = slot<Function1<List<WaitingListEntry>, Unit>>()
        every {
            repository.getEntriesByUser(any(), capture(onEntries), any())
        } answers {
            onEntries.captured.invoke(entries)
        }
    }

    @Test
    fun accepting_a_win_needs_both_ids() {
        val repository = mockk<LotteryRepository>()
        val lotteries = mockk<QueryEngine<Lottery>>()
        val controller = controllerOn(repository, lotteries)

        var error: Exception? = null
        controller.acceptLotteryWin(
            lotteryId = "",
            userId = "entrant-1",
            onSuccess = {},
            onFailure = { error = it }
        )
        assertTrue(error is IllegalArgumentException)
        assertEquals("Invalid lottery or user ID", error?.message)

        var otherError: Exception? = null
        controller.acceptLotteryWin(
            lotteryId = "lottery-1",
            userId = "",
            onSuccess = {},
            onFailure = { otherError = it }
        )
        assertTrue(otherError is IllegalArgumentException)
        assertEquals("Invalid lottery or user ID", otherError?.message)
    }

    @Test
    fun accepting_outside_the_booking_window_refuses() {
        val repository = mockk<LotteryRepository>()
        val lotteries = mockk<QueryEngine<Lottery>>()
        val controller = controllerOn(repository, lotteries)
        // no record behind the id: the window is not open
        windowAnswering(lotteries, null)
        entriesAnswering(repository, listOf(wonEntry()))

        var error: Exception? = null
        controller.acceptLotteryWin(
            lotteryId = "lottery-1",
            userId = "entrant-1",
            onSuccess = {},
            onFailure = { error = it }
        )
        assertTrue(error is IllegalStateException)
        assertEquals("The booking window is not open", error?.message)
        // nothing was rewritten
        verify(exactly = 0) { repository.updateEntry(any(), any()) }
    }

    @Test
    fun accepting_names_an_entry_that_is_not_there() {
        val repository = mockk<LotteryRepository>()
        val lotteries = mockk<QueryEngine<Lottery>>()
        val controller = controllerOn(repository, lotteries)
        windowAnswering(lotteries, openLottery())
        // an entry for a DIFFERENT lottery is not this pair's entry: the id is checked
        entriesAnswering(
            repository,
            listOf(
                WaitingListEntry(
                    lotteryId = "other-lottery",
                    userId = "entrant-1",
                    joinedAt = 111L,
                    status = LotteryEntryStatus.WON_AWAITING_RESPONSE
                )
            )
        )

        var error: Exception? = null
        controller.acceptLotteryWin(
            lotteryId = "lottery-1",
            userId = "entrant-1",
            onSuccess = {},
            onFailure = { error = it }
        )
        assertTrue(error is IllegalStateException)
        assertEquals("Your lottery entry could not be found", error?.message)
        verify(exactly = 0) { repository.updateEntry(any(), any()) }
    }

    @Test
    fun an_entry_that_never_won_cannot_be_answered() {
        val repository = mockk<LotteryRepository>()
        val lotteries = mockk<QueryEngine<Lottery>>()
        val controller = controllerOn(repository, lotteries)
        windowAnswering(lotteries, openLottery())
        entriesAnswering(
            repository,
            listOf(
                WaitingListEntry(
                    lotteryId = "lottery-1",
                    userId = "entrant-1",
                    joinedAt = 111L,
                    status = LotteryEntryStatus.WAITING_FOR_DRAW
                )
            )
        )

        var error: Exception? = null
        controller.acceptLotteryWin(
            lotteryId = "lottery-1",
            userId = "entrant-1",
            onSuccess = {},
            onFailure = { error = it }
        )
        assertTrue(error is IllegalStateException)
        assertEquals(
            "This lottery entry is no longer awaiting a response",
            error?.message
        )
        verify(exactly = 0) { repository.updateEntry(any(), any()) }
    }

    @Test
    fun answering_a_win_twice_refuses_the_second_time() {
        val repository = mockk<LotteryRepository>()
        val lotteries = mockk<QueryEngine<Lottery>>()
        val controller = controllerOn(repository, lotteries)
        windowAnswering(lotteries, openLottery())
        // the entry already carries an answer: a win is answered exactly once
        entriesAnswering(
            repository,
            listOf(
                WaitingListEntry(
                    lotteryId = "lottery-1",
                    userId = "entrant-1",
                    joinedAt = 111L,
                    status = LotteryEntryStatus.ACCEPTED
                )
            )
        )

        var error: Exception? = null
        controller.acceptLotteryWin(
            lotteryId = "lottery-1",
            userId = "entrant-1",
            onSuccess = {},
            onFailure = { error = it }
        )
        assertTrue(error is IllegalStateException)
        assertEquals(
            "This lottery entry is no longer awaiting a response",
            error?.message
        )
        verify(exactly = 0) { repository.updateEntry(any(), any()) }
    }

    @Test
    fun accepting_rewrites_the_entry_to_accepted_keeping_its_key() {
        val repository = mockk<LotteryRepository>()
        val lotteries = mockk<QueryEngine<Lottery>>()
        val controller = controllerOn(repository, lotteries)
        windowAnswering(lotteries, openLottery())
        entriesAnswering(repository, listOf(wonEntry()))

        val rewritten = slot<WaitingListEntry>()
        val onUpdated = slot<Function1<Result<Exception?>, Unit>>()
        every {
            repository.updateEntry(capture(rewritten), capture(onUpdated))
        } answers {
            onUpdated.captured.invoke(Result.success(null))
        }

        controller.acceptLotteryWin(
            lotteryId = "lottery-1",
            userId = "entrant-1",
            onSuccess = {},
            onFailure = {}
        )

        assertEquals(LotteryEntryStatus.ACCEPTED, rewritten.captured.status)
        // the rewrite keeps lotteryId/userId/joinedAt, and so its deterministic id
        assertEquals("lottery-1", rewritten.captured.lotteryId)
        assertEquals("entrant-1", rewritten.captured.userId)
        assertEquals(111L, rewritten.captured.joinedAt)
        assertEquals(
            WaitingListEntry.makeId("lottery-1", "entrant-1"),
            rewritten.captured.id
        )
    }

    @Test
    fun declining_needs_no_open_window() {
        val repository = mockk<LotteryRepository>()
        val lotteries = mockk<QueryEngine<Lottery>>()
        val controller = controllerOn(repository, lotteries)
        // the window is closed — accepting would refuse, declining must not
        windowAnswering(lotteries, null)
        entriesAnswering(repository, listOf(wonEntry()))

        val rewritten = slot<WaitingListEntry>()
        val onUpdated = slot<Function1<Result<Exception?>, Unit>>()
        every {
            repository.updateEntry(capture(rewritten), capture(onUpdated))
        } answers {
            onUpdated.captured.invoke(Result.success(null))
        }

        controller.declineLotteryWin(
            lotteryId = "lottery-1",
            userId = "entrant-1",
            onSuccess = {},
            onFailure = {}
        )

        assertEquals(LotteryEntryStatus.DECLINED, rewritten.captured.status)
        assertEquals("lottery-1", rewritten.captured.lotteryId)
        assertEquals("entrant-1", rewritten.captured.userId)
        assertEquals(111L, rewritten.captured.joinedAt)
    }

    @Test
    fun a_failed_entry_write_names_the_store_failure() {
        val repository = mockk<LotteryRepository>()
        val lotteries = mockk<QueryEngine<Lottery>>()
        val controller = controllerOn(repository, lotteries)
        windowAnswering(lotteries, openLottery())
        entriesAnswering(repository, listOf(wonEntry()))

        val onUpdated = slot<Function1<Result<Exception?>, Unit>>()
        every {
            repository.updateEntry(any(), capture(onUpdated))
        } answers {
            onUpdated.captured.invoke(Result.failure(Exception("the store said no")))
        }

        var error: Exception? = null
        controller.acceptLotteryWin(
            lotteryId = "lottery-1",
            userId = "entrant-1",
            onSuccess = {},
            onFailure = { error = it }
        )
        assertEquals("the store said no", error?.message)
    }

    @Test
    fun checkEntrantAccepted_names_only_its_own_accepted_entry() {
        val repository = mockk<LotteryRepository>()
        val lotteries = mockk<QueryEngine<Lottery>>()
        val controller = controllerOn(repository, lotteries)

        val accepted = WaitingListEntry(
            lotteryId = "lottery-1",
            userId = "entrant-1",
            joinedAt = 111L,
            status = LotteryEntryStatus.ACCEPTED
        )
        val results = mutableListOf<Boolean>()

        // the entry whose id IS makeId(lotteryId, userId) and whose status is ACCEPTED
        entriesAnswering(repository, listOf(accepted))
        controller.checkEntrantAccepted(
            lotteryId = "lottery-1",
            userId = "entrant-1",
            onSuccess = { results.add(it) },
            onFailure = {}
        )
        assertTrue(results.last())

        // an ACCEPTED entry for another lottery does not count: the entry id is checked
        entriesAnswering(
            repository,
            listOf(
                WaitingListEntry(
                    lotteryId = "other-lottery",
                    userId = "entrant-1",
                    joinedAt = 111L,
                    status = LotteryEntryStatus.ACCEPTED
                )
            )
        )
        controller.checkEntrantAccepted(
            lotteryId = "lottery-1",
            userId = "entrant-1",
            onSuccess = { results.add(it) },
            onFailure = {}
        )
        assertFalse(results.last())

        // the right entry but not yet answered: status is checked too
        entriesAnswering(repository, listOf(wonEntry()))
        controller.checkEntrantAccepted(
            lotteryId = "lottery-1",
            userId = "entrant-1",
            onSuccess = { results.add(it) },
            onFailure = {}
        )
        assertFalse(results.last())
    }

    @Test
    fun loadEntrantEntries_refuses_a_blank_user() {
        val repository = mockk<LotteryRepository>()
        val lotteries = mockk<QueryEngine<Lottery>>()
        val controller = controllerOn(repository, lotteries)

        var error: Exception? = null
        controller.loadEntrantEntries(
            userId = "",
            onSuccess = {},
            onFailure = { error = it }
        )
        assertTrue(error is IllegalArgumentException)
        assertEquals("A valid user ID is required", error?.message)
        verify(exactly = 0) { repository.getEntriesByUser(any(), any(), any()) }
    }
}
