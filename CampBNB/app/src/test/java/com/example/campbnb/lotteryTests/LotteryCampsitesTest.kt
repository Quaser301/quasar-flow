package com.example.campbnb.lotteryTests

import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryStatus
import org.junit.Assert
import org.junit.Test

class LotteryCampsitesTest {
    @Test
    fun multiple_campsites_create_one_lottery() {
        val now = System.currentTimeMillis()
        val registrationStart = now + 1000L
        val registrationEnd = registrationStart + 1000L
        val bookingStart = registrationEnd + 1000L
        val bookingEnd = bookingStart + 1000L
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one", "camp-two"),
            registrationStart = registrationStart,
            registrationEnd = registrationEnd,
            bookingStart = bookingStart,
            bookingEnd = bookingEnd,
            numberOfWinners = 2,
            waitlistCapacity = 3
        )
        Assert.assertTrue(lottery.isValid(now))
    }

    @Test
    fun one_campsite_still_creates_a_lottery() {
        val now = System.currentTimeMillis()
        val registrationStart = now + 1000L
        val registrationEnd = registrationStart + 1000L
        val bookingStart = registrationEnd + 1000L
        val bookingEnd = bookingStart + 1000L
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one"),
            registrationStart = registrationStart,
            registrationEnd = registrationEnd,
            bookingStart = bookingStart,
            bookingEnd = bookingEnd,
            numberOfWinners = 1
        )
        Assert.assertTrue(lottery.isValid(now))
    }

    @Test
    fun empty_campsite_selection_blocks_save() {
        val now = System.currentTimeMillis()
        val registrationStart = now + 1000L
        val registrationEnd = registrationStart + 1000L
        val bookingStart = registrationEnd + 1000L
        val bookingEnd = bookingStart + 1000L
        val lottery = Lottery(
            ownerId = "owner-1",
            registrationStart = registrationStart,
            registrationEnd = registrationEnd,
            bookingStart = bookingStart,
            bookingEnd = bookingEnd,
            numberOfWinners = 1
        )
        Assert.assertFalse(lottery.isValid(now))
    }

    // The status walk across every boundary of the stated ladder, edges included:
    // a lottery exactly at bookingEnd is still BOOKING, the one after it is CLOSED.
    @Test
    fun statusAt_walks_the_exact_boundaries() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one"),
            registrationStart = 1000L,
            registrationEnd = 2000L,
            bookingStart = 3000L,
            bookingEnd = 4000L,
            numberOfWinners = 1
        )

        Assert.assertEquals(LotteryStatus.UPCOMING, lottery.statusAt(999L))
        Assert.assertEquals(LotteryStatus.OPEN, lottery.statusAt(1000L))
        Assert.assertEquals(LotteryStatus.OPEN, lottery.statusAt(1999L))
        Assert.assertEquals(LotteryStatus.DRAWN, lottery.statusAt(2000L))
        Assert.assertEquals(LotteryStatus.DRAWN, lottery.statusAt(2999L))
        Assert.assertEquals(LotteryStatus.BOOKING, lottery.statusAt(3000L))
        Assert.assertEquals(LotteryStatus.BOOKING, lottery.statusAt(4000L))
        Assert.assertEquals(LotteryStatus.CLOSED, lottery.statusAt(4001L))
    }

    // The remaining isValid rules, one per case, over an otherwise valid range.
    @Test
    fun a_lottery_without_winners_never_creates() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one"),
            registrationStart = now + 1000L,
            registrationEnd = now + 2000L,
            bookingStart = now + 3000L,
            bookingEnd = now + 4000L
        )
        Assert.assertFalse(lottery.isValid(now))
    }

    @Test
    fun capacity_below_the_winners_never_creates() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one"),
            registrationStart = now + 1000L,
            registrationEnd = now + 2000L,
            bookingStart = now + 3000L,
            bookingEnd = now + 4000L,
            numberOfWinners = 3,
            waitlistCapacity = 2
        )
        Assert.assertFalse(lottery.isValid(now))
    }

    @Test
    fun an_unlimited_waitlist_is_allowed() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one"),
            registrationStart = now + 1000L,
            registrationEnd = now + 2000L,
            bookingStart = now + 3000L,
            bookingEnd = now + 4000L,
            numberOfWinners = 3
        )
        Assert.assertTrue(lottery.isValid(now))
    }

    @Test
    fun a_missing_boundary_stamp_never_creates() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one"),
            registrationStart = now + 1000L,
            registrationEnd = now + 2000L,
            bookingStart = now + 3000L,
            numberOfWinners = 1
        )
        Assert.assertFalse(lottery.isValid(now))
    }

    @Test
    fun booking_may_not_open_when_registration_is_still_closing() {
        val now = System.currentTimeMillis()
        val registrationEnd = now + 2000L
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one"),
            registrationStart = now + 1000L,
            registrationEnd = registrationEnd,
            bookingStart = registrationEnd,
            bookingEnd = registrationEnd + 1000L,
            numberOfWinners = 1
        )
        Assert.assertFalse(lottery.isValid(now))
    }

    @Test
    fun booking_may_not_close_before_it_opens() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one"),
            registrationStart = now + 1000L,
            registrationEnd = now + 2000L,
            bookingStart = now + 4000L,
            bookingEnd = now + 3000L,
            numberOfWinners = 1
        )
        Assert.assertFalse(lottery.isValid(now))
    }

    // A joining owner's edit changes only the campsites; the id and every parameter
    // keep standing, exactly as the model contract states.
    @Test
    fun editing_campsites_keeps_the_id_and_every_other_parameter() {
        val now = System.currentTimeMillis()
        val base = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one", "camp-two"),
            registrationStart = now + 1000L,
            registrationEnd = now + 2000L,
            bookingStart = now + 3000L,
            bookingEnd = now + 4000L,
            numberOfWinners = 2,
            waitlistCapacity = 3
        ).also { it.id = "lot-edit" }

        val edited = base.withCampsiteIds(listOf("other"))

        Assert.assertEquals("lot-edit", edited.id)
        Assert.assertEquals(listOf("other"), edited.campsiteIds)
        Assert.assertEquals(base.ownerId, edited.ownerId)
        Assert.assertEquals(base.numberOfWinners, edited.numberOfWinners)
        Assert.assertEquals(base.waitlistCapacity, edited.waitlistCapacity)
        Assert.assertEquals(base.registrationStart, edited.registrationStart)
        Assert.assertEquals(base.registrationEnd, edited.registrationEnd)
        Assert.assertEquals(base.bookingStart, edited.bookingStart)
        Assert.assertEquals(base.bookingEnd, edited.bookingEnd)
    }
}
