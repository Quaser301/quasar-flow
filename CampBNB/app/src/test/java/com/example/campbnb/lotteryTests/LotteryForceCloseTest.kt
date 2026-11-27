package com.example.campbnb.lotteryTests

import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.QueryEngine
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.LotteryStatus
import com.example.campbnb.models.LotteryEntryStatus
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
 * Force-closing a lottery draws the one the caller named. Two things the device
 * reported had been broken: the draw left a second copy of the lottery on file, and
 * it moved the booking window onto today instead of keeping the period the owner
 * chose. These cases run the draw over a stubbed repository and read the record it
 * saves.
 */
class LotteryForceCloseTest {
    private val dayMillis = 24L * 60L * 60L * 1000L

    /** A controller over a stubbed repository; nothing touches Firestore. */
    private fun controllerOn(
        repository: LotteryRepository,
        notifications: NotificationController,
        clock: () -> Long
    ): LotteryController = LotteryController(
        LotteryModel(),
        repository,
        mockk<QueryEngine<Lottery>>(),
        notifications,
        clock
    )

    /** A lottery whose registration has already closed at [now], bookable as described. */
    private fun closedAt(now: Long, bookingStart: Long, bookingEnd: Long) = Lottery(
        ownerId = "owner-1",
        campsiteIds = listOf("camp-one"),
        registrationStart = now - 600000L,
        registrationEnd = now - 100000L,
        bookingStart = bookingStart,
        bookingEnd = bookingEnd,
        numberOfWinners = 1
    )

    /** Runs the draw over a stubbed repository and hands back the record it saved. */
    private fun savedByForceClose(lottery: Lottery, clock: () -> Long): Lottery {
        val repository = mockk<LotteryRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val controller = controllerOn(repository, notifications, clock)

        val entries = listOf(
            WaitingListEntry(lotteryId = lottery.id, userId = "entrant-1"),
            WaitingListEntry(lotteryId = lottery.id, userId = "entrant-2")
        )
        val onEntries = slot<Function1<List<WaitingListEntry>, Unit>>()
        val onEntriesFailed = slot<Function1<Exception, Unit>>()
        every {
            repository.getEntries(any(), capture(onEntries), capture(onEntriesFailed))
        } answers {
            onEntries.captured.invoke(entries)
        }

        val saved = slot<Lottery>()
        val onSaved = slot<Function0<Unit>>()
        val onSaveFailed = slot<Function1<Exception, Unit>>()
        every {
            repository.save(capture(saved), capture(onSaved), capture(onSaveFailed))
        } answers {
            onSaved.captured.invoke()
        }
        every {
            repository.updateEntry(any(), any())
        } answers {
            secondArg<(Result<Exception?>) -> Unit>().invoke(Result.success(null))
        }

        controller.forceCloseLottery(lottery, nameOfCampsite = { id -> id })

        verify(exactly = 1) { notifications.notify(any()) }
        return saved.captured
    }

    /** The draw edits the lottery the caller named; it never leaves a second copy. */
    @Test
    fun force_closing_edits_the_lottery_the_caller_named() {
        val now = 1700000000000L
        val lottery = closedAt(now, bookingStart = now + 1000L, bookingEnd = now + 2000L)
        val saved = savedByForceClose(lottery) { now }

        assertEquals(lottery.id, saved.id)
    }

    @Test
    fun force_closing_keeps_the_booking_period_the_owner_chose() {
        val now = 1700000000000L
        val chosenEnd = now + 3L * dayMillis
        val saved = savedByForceClose(
            closedAt(now, bookingStart = now + 1000L, bookingEnd = chosenEnd)
        ) { now }

        assertEquals(chosenEnd, saved.bookingEnd)
    }

    /** A booking that has not opened yet is pulled to now, so the draw is bookable. */
    @Test
    fun force_closing_opens_a_booking_that_has_not_opened_yet() {
        val now = 1700000000000L
        val saved = savedByForceClose(
            closedAt(now, bookingStart = now + 1000L, bookingEnd = now + 3L * dayMillis)
        ) { now }

        assertEquals(now, saved.bookingStart)
        assertTrue(saved.statusAt(now) == LotteryStatus.BOOKING)
    }

    /** A period that already passed is held open for the rest of today, the shortest
     * window a draw can still be booked in. */
    @Test
    fun force_closing_a_lottery_whose_period_passed_books_the_rest_of_today() {
        val realNow = System.currentTimeMillis()
        val saved = savedByForceClose(
            Lottery(
                ownerId = "owner-1",
                campsiteIds = listOf("camp-one"),
                // the whole lifecycle is already past
                registrationStart = realNow - 3L * dayMillis,
                registrationEnd = realNow - 2L * dayMillis - 1000L,
                bookingStart = realNow - 2L * dayMillis,
                bookingEnd = realNow - dayMillis,
                numberOfWinners = 1
            )
        ) { realNow }

        assertTrue(saved.bookingEnd >= realNow)
        assertTrue(saved.statusAt(realNow) == LotteryStatus.BOOKING)
        // the window ends at the last millisecond of a UTC day, not at an arbitrary point
        assertEquals(dayMillis - 1L, saved.bookingEnd % dayMillis)
    }

    /**
     * Before anything is reported, every entrant's waiting-list entry is rewritten one
     * at a time: a winner's becomes WON_AWAITING_RESPONSE, every other NOT_SELECTED,
     * each keeping its own key (controller.md, main's US 02.08.02). The draw order is
     * arbitrary, so the case counts the sides rather than naming which entrant lands
     * on which.
     */
    @Test
    fun force_closing_rewrites_every_entrant_to_its_side_of_the_draw() {
        val now = 1700000000000L
        val lottery = closedAt(now, bookingStart = now + 1000L, bookingEnd = now + 2000L)

        val repository = mockk<LotteryRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val controller = controllerOn(repository, notifications) { now }

        val entries = listOf(
            WaitingListEntry(lotteryId = lottery.id, userId = "entrant-1", joinedAt = 111L),
            WaitingListEntry(lotteryId = lottery.id, userId = "entrant-2", joinedAt = 222L)
        )
        val onEntries = slot<Function1<List<WaitingListEntry>, Unit>>()
        every {
            repository.getEntries(any(), capture(onEntries), any())
        } answers {
            onEntries.captured.invoke(entries)
        }

        val onSaved = slot<Function0<Unit>>()
        every {
            repository.save(any(), capture(onSaved), any())
        } answers {
            onSaved.captured.invoke()
        }

        val rewritten = mutableListOf<WaitingListEntry>()
        val rewrittenEntry = slot<WaitingListEntry>()
        val onUpdated = slot<Function1<Result<Exception?>, Unit>>()
        every {
            repository.updateEntry(capture(rewrittenEntry), capture(onUpdated))
        } answers {
            rewritten.add(rewrittenEntry.captured)
            onUpdated.captured.invoke(Result.success(null))
        }

        controller.forceCloseLottery(lottery, nameOfCampsite = { id -> id })

        assertEquals(1, rewritten.count { it.status == LotteryEntryStatus.WON_AWAITING_RESPONSE })
        assertEquals(1, rewritten.count { it.status == LotteryEntryStatus.NOT_SELECTED })
        // every rewrite keeps its own key: same lottery, same user, same join moment
        assertEquals(
            listOf(lottery.id, lottery.id),
            rewritten.map { it.lotteryId }
        )
        assertEquals(
            listOf(111L, 222L),
            rewritten.map { it.joinedAt }.sorted()
        )
        assertEquals(
            listOf("entrant-1", "entrant-2"),
            rewritten.map { it.userId }.sorted()
        )
    }

    /** When an entry rewrite fails, the lottery IS drawn and saved — only the entrants'
     * half did not land — and the status message names the cause and asks nothing further. */
    @Test
    fun force_closing_a_draw_whose_entries_do_not_all_land_reports_the_partial() {
        val now = 1700000000000L
        val lottery = closedAt(now, bookingStart = now + 1000L, bookingEnd = now + 2000L)

        val model = LotteryModel()
        val repository = mockk<LotteryRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val controller = LotteryController(
            model,
            repository,
            mockk<QueryEngine<Lottery>>(),
            notifications
        ) { now }

        val entries = listOf(
            WaitingListEntry(lotteryId = lottery.id, userId = "entrant-1", joinedAt = 111L),
            WaitingListEntry(lotteryId = lottery.id, userId = "entrant-2", joinedAt = 222L)
        )
        val onEntries = slot<Function1<List<WaitingListEntry>, Unit>>()
        every {
            repository.getEntries(any(), capture(onEntries), any())
        } answers {
            onEntries.captured.invoke(entries)
        }

        val saved = slot<Lottery>()
        val onSaved = slot<Function0<Unit>>()
        every {
            repository.save(capture(saved), capture(onSaved), any())
        } answers {
            onSaved.captured.invoke()
        }

        val rewritten = slot<WaitingListEntry>()
        val onUpdated = slot<Function1<Result<Exception?>, Unit>>()
        every {
            repository.updateEntry(capture(rewritten), capture(onUpdated))
        } answers {
            if (rewritten.captured.userId == "entrant-2") {
                onUpdated.captured.invoke(Result.failure(Exception("the store said no")))
            } else {
                onUpdated.captured.invoke(Result.success(null))
            }
        }

        var drew = true
        controller.forceCloseLottery(
            lottery,
            nameOfCampsite = { id -> id },
            onDone = { drew = it }
        )

        assertFalse(drew)
        assertEquals(
            "Lottery was drawn, but some entrant statuses could not be updated: the store said no",
            model.statusMessage
        )
        // the draw itself landed: the record was saved and no LotteryDrawn was reported
        assertEquals(lottery.id, saved.captured.id)
        verify(exactly = 0) { notifications.notify(any()) }
    }
}
