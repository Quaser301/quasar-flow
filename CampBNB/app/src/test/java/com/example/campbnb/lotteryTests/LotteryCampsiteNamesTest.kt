package com.example.campbnb.lotteryTests

import com.example.campbnb.models.Lottery
import com.example.campbnb.ui.lottery.campsiteNamesLine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The lottery views name the campsites a lottery covers; they never draw the ids the
 * campsites are stored under (the device-reported regression: the lottery screens showed
 * UUIDs). The line is built by the pure campsiteNamesLine, which is what these cases
 * pin.
 */
class LotteryCampsiteNamesTest {
    /** A resolver that knows two names, the way the views supply one. */
    private fun nameOf(id: String): String = when (id) {
        "uuid-one" -> "Riverside"
        "uuid-two" -> "Pine Hollow"
        else -> id
    }

    @Test
    fun the_campsite_line_names_the_campsites_the_lottery_lists() {
        assertEquals(
            "Riverside, Pine Hollow",
            campsiteNamesLine(listOf("uuid-one", "uuid-two")) { id -> nameOf(id) }
        )
    }

    @Test
    fun the_campsite_line_keeps_the_order_the_lottery_lists_its_campsites_in() {
        assertEquals(
            "Pine Hollow, Riverside",
            campsiteNamesLine(listOf("uuid-two", "uuid-one")) { id -> nameOf(id) }
        )
    }

    /** A nameless campsite is the only case where an id may be drawn. */
    @Test
    fun an_unknown_campsite_shows_only_its_id() {
        assertEquals("uuid-three", campsiteNamesLine(listOf("uuid-three")) { id -> nameOf(id) })
    }

    @Test
    fun a_lottery_that_covers_nothing_shows_none() {
        assertEquals("None", campsiteNamesLine(emptyList<String>()) { id -> nameOf(id) })
    }

    /** The regression itself: a real lottery's ids must not reach the line it draws. */
    @Test
    fun a_real_lottery_draws_names_not_the_uuids_it_stores() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("uuid-one", "uuid-two")
        )
        val line = campsiteNamesLine(lottery.campsiteIds) { id -> nameOf(id) }

        assertFalse(line.contains("uuid-one"))
        assertFalse(line.contains("uuid-two"))
    }
}
