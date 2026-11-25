package com.example.campbnb.lotteryTests

import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.QueryEngine
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.LotteryStatus
import com.example.campbnb.models.WaitingListEntry

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

import org.junit.Assert.assertEquals
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
}
