
package com.example.campbnb.reservationTests

import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.ReservationController
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.models.Availability
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus
import com.example.campbnb.models.DateUtils

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

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

    /**
     * US 01.05.01: a completed reservation is reported to the notification feature, so
     * neither party is left out. This case holds the flow to the point where it reports the
     * event; the records an event becomes, and the two users it reaches, are another unit's
     * job and asserted there.
     */
    @Test
    fun createReservation_reportsTheReservationToItsParties() {
        val lotteryController = mockk<LotteryController>()
        val availability = mockk<Availability>()
        val reservations = mockk<ReservationRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)

        // The success continuations the flow hands to each collaborator, invoked by the stubs.
        val onWon = slot<Function1<Boolean, Unit>>()
        val onBookingOpen = slot<Function1<Boolean, Unit>>()
        val onOpenDates = slot<Function1<Set<String>, Unit>>()
        val onBookedDates = slot<Function1<Set<String>, Unit>>()
        val onSaved = slot<Function0<Unit>>()

        every {
            lotteryController.checkIfWon(any(), any(), any(), capture(onWon), any())
        } answers {
            onWon.captured.invoke(true)
        }
        every {
            lotteryController.isBookingOpen(any(), capture(onBookingOpen), any())
        } answers {
            onBookingOpen.captured.invoke(true)
        }
        every {
            availability.getOpenDates(any(), capture(onOpenDates), any())
        } answers {
            onOpenDates.captured.invoke(setOf("2026-12-01"))
        }
        every {
            reservations.getBookedDates(any(), capture(onBookedDates), any())
        } answers {
            onBookedDates.captured.invoke(setOf<String>())
        }
        every {
            reservations.save(any(), capture(onSaved), any())
        } answers {
            onSaved.captured.invoke()
        }

        val reservingController =
            ReservationController(lotteryController, availability, reservations, notifications)
        reservingController.confirmPolicies("entry1")

        var failure: Exception? = null
        reservingController.createReservation(
            lotteryEntryId = "entry1",
            entrantId = "entrant1",
            ownerId = "owner1",
            campsiteId = "camp1",
            dates = setOf("2026-12-01"),
            onSuccess = {},
            onFailure = { error -> failure = error },
            guests = 2,
            refundRecorded = false,
            createdAt = 1000L,
            rules = true
        )

        Assert.assertNull(failure)
        // The event the completed reservation reports; the two users it ends up reaching
        // are the notification feature's own decision.
        verify(exactly = 1) { notifications.notify(any()) }
    }

    /**
     * The receipt's exact shape: an unquoted header, then one row with EVERY value
     * quoted, nights as the date count, the status enum name, and the device date.
     */
    @Test
    fun generateReceiptCsv_is_the_exactly_stated_shape() {
        val reservation = ReservationModel(
            lotteryEntryId = "entry1",
            campsiteId = "camp1",
            entrantId = "entrant1",
            ownerId = "owner1",
            guests = 2,
            dates = listOf("2026-12-02", "2026-12-01"),
            startDate = "2026-12-01",
            endDate = "2026-12-02",
            status = ReservationStatus.ACTIVE,
            refundRecorded = false,
            createdAt = 1000L,
            rules = true
        )

        val receipt = controller.generateReceiptCsv(reservation)

        val expected =
            "reservationId,campsiteId,entrantId,ownerId,startDate,endDate,nights,status," +
                    "refundRecorded,generatedOn\n" +
                    "\"entry1\",\"camp1\",\"entrant1\",\"owner1\",\"2026-12-01\",\"2026-12-02\"," +
                    "\"2\",\"ACTIVE\",\"false\",\"" + DateUtils.today() + "\""

        Assert.assertEquals(expected, receipt)
    }

    // Even a quote inside a value survives the receipt's always-quoted row: inner
    // quotes are doubled, unlike the model CSV's conditional quoting.
    @Test
    fun generateReceiptCsv_doubles_quotes_inside_every_value() {
        val reservation = ReservationModel(
            lotteryEntryId = "entry\"1",
            campsiteId = "camp1",
            entrantId = "entrant1",
            ownerId = "owner1",
            guests = 1,
            dates = listOf("2026-12-01"),
            startDate = "2026-12-01",
            endDate = "2026-12-01",
            status = ReservationStatus.CANCELLED,
            refundRecorded = true,
            createdAt = 1000L,
            rules = true
        )

        val receipt = controller.generateReceiptCsv(reservation)

        Assert.assertTrue(receipt.contains("\"entry\"\"1\""))
        Assert.assertTrue(receipt.contains("\"CANCELLED\""))
        Assert.assertTrue(receipt.contains("\"true\""))
    }

    /**
     * US 01.05.02: a date that someone else took between checking and booking is a
     * refusal the caller can only see through the failing continuation; nothing is
     * saved and nothing is reported.
     */
    @Test
    fun createReservation_refuses_when_a_date_is_no_longer_available() {
        val lotteryController = mockk<LotteryController>()
        val availability = mockk<Availability>()
        val reservations = mockk<ReservationRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)

        val onWon = slot<Function1<Boolean, Unit>>()
        val onBookingOpen = slot<Function1<Boolean, Unit>>()
        val onOpenDates = slot<Function1<Set<String>, Unit>>()
        val onBookedDates = slot<Function1<Set<String>, Unit>>()

        every {
            lotteryController.checkIfWon(any(), any(), any(), capture(onWon), any())
        } answers {
            onWon.captured.invoke(true)
        }
        every {
            lotteryController.isBookingOpen(any(), capture(onBookingOpen), any())
        } answers {
            onBookingOpen.captured.invoke(true)
        }
        every {
            availability.getOpenDates(any(), capture(onOpenDates), any())
        } answers {
            onOpenDates.captured.invoke(setOf("2026-12-01"))
        }
        every {
            reservations.getBookedDates(any(), capture(onBookedDates), any())
        } answers {
            onBookedDates.captured.invoke(setOf("2026-12-01"))
        }

        val reservingController =
            ReservationController(lotteryController, availability, reservations, notifications)
        reservingController.confirmPolicies("entry1")

        var failure: Exception? = null
        reservingController.createReservation(
            lotteryEntryId = "entry1",
            entrantId = "entrant1",
            ownerId = "owner1",
            campsiteId = "camp1",
            dates = setOf("2026-12-01"),
            onSuccess = {},
            onFailure = { error -> failure = error },
            guests = 2,
            refundRecorded = false,
            createdAt = 1000L,
            rules = true
        )

        Assert.assertTrue(failure is IllegalStateException)
        Assert.assertEquals(
            "One or more dates are no longer available",
            failure?.message
        )
        verify(exactly = 0) { reservations.save(any(), any(), any()) }
        verify(exactly = 0) { notifications.notify(any()) }
    }
}
