package com.example.campbnb.lotteryTests

import com.example.campbnb.models.Lottery
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
}
