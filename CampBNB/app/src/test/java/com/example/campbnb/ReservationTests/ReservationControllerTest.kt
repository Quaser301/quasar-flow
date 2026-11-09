
package com.example.campbnb.reservationTests

import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.ReservationController
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.models.Availability
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus

import io.mockk.mockk
import io.mockk.every
import io.mockk.slot

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
    fun confirmPolicies_movesTheGuardPastPolicies() {
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

        // The call no longer fails because of the policies: it now reaches the very next
        // guard and is refused by that one instead.
        Assert.assertTrue(error is IllegalArgumentException)
        Assert.assertEquals(
            "Select at least one date",
            error?.message
        )
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

    @Test
    fun createReservation_whenEveryGuardPasses_receivesTheSavedReservation() {
        val lotteryController = mockk<LotteryController>()
        val availability = mockk<Availability>()
        val reservations = mockk<ReservationRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val bookingController = ReservationController(
            lotteryController,
            availability,
            reservations,
            notifications
        )
        bookingController.confirmPolicies("entry1")

        val wonSlot = slot<(Boolean) -> Unit>()
        val openSlot = slot<(Boolean) -> Unit>()
        val openDatesSlot = slot<(Set<String>) -> Unit>()
        val bookedSlot = slot<(Set<String>) -> Unit>()
        val savedSlot = slot<() -> Unit>()

        every {
            lotteryController.checkIfWon(any(), any(), any(), capture(wonSlot), any())
        } returns Unit
        every {
            lotteryController.isBookingOpen(any(), capture(openSlot), any())
        } returns Unit
        every {
            availability.getOpenDates(any(), capture(openDatesSlot), any())
        } returns Unit
        every {
            reservations.getBookedDates(any(), capture(bookedSlot), any())
        } returns Unit
        every {
            reservations.save(any(), capture(savedSlot), any())
        } returns Unit

        var received: ReservationModel? = null
        var error: Exception? = null

        bookingController.createReservation(
            lotteryEntryId = "entry1",
            entrantId = "entrant1",
            ownerId = "owner1",
            campsiteId = "camp1",
            dates = setOf("2026-12-05", "2026-12-01"),
            onSuccess = { received = it },
            onFailure = { error = it },
            guests = 2,
            refundRecorded = false,
            createdAt = 1000L,
            rules = true
        )

        // Drive the chain the controller builds: won, booking open, dates offered,
        // nothing booked against them, and the save completing.
        wonSlot.captured.invoke(true)
        openSlot.captured.invoke(true)
        openDatesSlot.captured.invoke(setOf("2026-12-01", "2026-12-05", "2026-12-09"))
        bookedSlot.captured.invoke(setOf("2026-12-09"))
        savedSlot.captured.invoke()

        Assert.assertTrue(error == null)
        Assert.assertTrue(received != null)
        Assert.assertEquals(listOf("2026-12-01", "2026-12-05"), received!!.dates)
        Assert.assertEquals("2026-12-01", received!!.startDate)
        Assert.assertEquals("2026-12-05", received!!.endDate)
        Assert.assertEquals(ReservationStatus.ACTIVE, received!!.status)
    }

    @Test
    fun createReservation_forDatesAlreadyBooked_refusesTheBooking() {
        val lotteryController = mockk<LotteryController>()
        val availability = mockk<Availability>()
        val reservations = mockk<ReservationRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val bookingController = ReservationController(
            lotteryController,
            availability,
            reservations,
            notifications
        )
        bookingController.confirmPolicies("entry1")

        val wonSlot = slot<(Boolean) -> Unit>()
        val openSlot = slot<(Boolean) -> Unit>()
        val openDatesSlot = slot<(Set<String>) -> Unit>()
        val bookedSlot = slot<(Set<String>) -> Unit>()

        every {
            lotteryController.checkIfWon(any(), any(), any(), capture(wonSlot), any())
        } returns Unit
        every {
            lotteryController.isBookingOpen(any(), capture(openSlot), any())
        } returns Unit
        every {
            availability.getOpenDates(any(), capture(openDatesSlot), any())
        } returns Unit
        every {
            reservations.getBookedDates(any(), capture(bookedSlot), any())
        } returns Unit

        var error: Exception? = null

        bookingController.createReservation(
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

        wonSlot.captured.invoke(true)
        openSlot.captured.invoke(true)
        openDatesSlot.captured.invoke(setOf("2026-12-01"))
        bookedSlot.captured.invoke(setOf("2026-12-01"))

        Assert.assertTrue(error is IllegalStateException)
        Assert.assertEquals(
            "One or more dates are no longer available",
            error?.message
        )
    }

    @Test
    fun createReservation_whenTheEntrantDidNotWin_refusesTheBooking() {
        val lotteryController = mockk<LotteryController>()
        val availability = mockk<Availability>()
        val reservations = mockk<ReservationRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val bookingController = ReservationController(
            lotteryController,
            availability,
            reservations,
            notifications
        )
        bookingController.confirmPolicies("entry1")

        val wonSlot = slot<(Boolean) -> Unit>()

        every {
            lotteryController.checkIfWon(any(), any(), any(), capture(wonSlot), any())
        } returns Unit

        var error: Exception? = null

        bookingController.createReservation(
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

        wonSlot.captured.invoke(false)

        Assert.assertTrue(error is IllegalStateException)
        Assert.assertEquals(
            "Entrant did not win this lottery for this campsite",
            error?.message
        )
    }

    @Test
    fun generateReceiptCsv_withAwkwardIds_escapesEveryFieldAndCountsEveryNight() {
        val reservation = ReservationModel(
            lotteryEntryId = "entry\"1",
            campsiteId = "camp,1",
            entrantId = "entrant1",
            ownerId = "owner1",
            guests = 2,
            dates = (1..300).map { "day-$it" },
            startDate = "day-1",
            endDate = "day-300",
            status = ReservationStatus.ACTIVE,
            refundRecorded = false,
            createdAt = 1000L,
            rules = true
        )

        val receipt = controller.generateReceiptCsv(reservation)

        // Quotes double inside their field, a comma stays inside its quotes, and
        // the nights column carries the true count for a three-hundred-date stay.
        Assert.assertTrue(receipt.contains("\"entry\"\"1\""))
        Assert.assertTrue(receipt.contains("\"camp,1\""))
        Assert.assertTrue(receipt.contains(",\"300\","))
    }
}
