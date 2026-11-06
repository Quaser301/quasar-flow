
package com.example.campbnb.reservationTests

import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.ReservationController
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.models.Availability
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus

import io.mockk.mockk

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ReservationControllerTest {

    private lateinit var controller: ReservationController

    @Before
    fun setup() {
        val lotteryController = mockk<LotteryController>()
        val availability = mockk<Availability>()
        val reservations = mockk<ReservationRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)

        controller = ReservationController(
            lotteryController,
            availability,
            reservations,
            notifications
        )
    }

    @Test
    fun createReservation_withoutConfirmedPolicies_fails() {
        var error: Exception? = null

        controller.createReservation(
            lotteryEntryId = "entry1",
            entrantId = "entrant1",
            ownerId = "owner1",
            campsiteId = "camp1",
            dates = setOf("2026-12-01"),
            onSuccess = {},
            onFailure = { error = it },
            guests = 2,
            refundRecorded = false,
            createdAt = 1000L,
            rules = true
        )

        Assert.assertTrue(error is IllegalStateException)
        Assert.assertEquals(
            "Policies must be confirmed before reserving",
            error?.message
        )
    }

    @Test
    fun createReservation_withoutDates_fails() {
        controller.confirmPolicies("entry1")

        var error: Exception? = null

        controller.createReservation(
            lotteryEntryId = "entry1",
            entrantId = "entrant1",
            ownerId = "owner1",
            campsiteId = "camp1",
            dates = emptySet(),
            onSuccess = {},
            onFailure = { error = it },
            guests = 2,
            refundRecorded = false,
            createdAt = 1000L,
            rules = true
        )

        Assert.assertTrue(error is IllegalArgumentException)
        Assert.assertEquals(
            "Select at least one date",
            error?.message
        )
    }

    @Test
    fun confirmPolicies_allowsPoliciesToBeConfirmed() {
        controller.confirmPolicies("entry1")

        // Confirming policies should not throw an exception.
        Assert.assertTrue(true)
    }

    @Test
    fun generateReceiptCsv_returnsCorrectInformation() {
        val reservation = ReservationModel(
            lotteryEntryId = "entry1",
            campsiteId = "camp1",
            entrantId = "entrant1",
            ownerId = "owner1",
            guests = 2,
            dates = listOf("2026-12-01", "2026-12-02"),
            startDate = "2026-12-01",
            endDate = "2026-12-02",
            status = ReservationStatus.ACTIVE,
            refundRecorded = false,
            createdAt = 1000L,
            rules = true
        )

        val receipt = controller.generateReceiptCsv(reservation)

        Assert.assertTrue(receipt.contains("reservationId,campsiteId"))
        Assert.assertTrue(receipt.contains("\"entry1\""))
        Assert.assertTrue(receipt.contains("\"camp1\""))
        Assert.assertTrue(receipt.contains("\"entrant1\""))
        Assert.assertTrue(receipt.contains("\"owner1\""))
        Assert.assertTrue(receipt.contains("\"ACTIVE\""))
    }
}
