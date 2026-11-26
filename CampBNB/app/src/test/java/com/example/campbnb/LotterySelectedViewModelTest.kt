package com.example.campbnb

import com.example.campbnb.models.Lottery
import com.example.campbnb.viewModels.LotterySelectedViewModel
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOTICE: This file was AI-assisted. The verbatim prompts for this work are recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 *
 * The cases added for the spec-audit request recorded there exercise the new
 * `LotterySelectedViewModel` cell: it starts null, a plain write becomes the read
 * value, and writing null clears the selection.
 */

// US 01.05.02: one state cell, no seams, no init side effects — the app root
// builds it once and every lottery window reads through it.

class LotterySelectedViewModelTest {

    @Test
    fun test_starts_with_no_lottery_selected() {
        val view = LotterySelectedViewModel()

        assertNull(view.selectedLottery.value)
    }

    @Test
    fun test_update_records_the_selected_lottery() {
        val view = LotterySelectedViewModel()
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one"),
            registrationStart = 100L,
            registrationEnd = 200L,
            bookingStart = 300L,
            bookingEnd = 400L
        )

        view.updateLottery(lottery)

        assertTrue(lottery === view.selectedLottery.value)
    }

    @Test
    fun test_update_with_null_clears_the_selection() {
        val view = LotterySelectedViewModel()
        val lottery = Lottery(ownerId = "owner-1")

        view.updateLottery(lottery)
        view.updateLottery(null)

        assertNull(view.selectedLottery.value)
    }
}
