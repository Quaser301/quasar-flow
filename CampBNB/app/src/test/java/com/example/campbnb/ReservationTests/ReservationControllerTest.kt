package com.example.campbnb.ReservationTests

import com.example.campbnb.controller.LotteryController
import com.example.campbnb.models.Campsite
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.ReservationController
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.models.Availability
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus
import com.example.campbnb.models.DateUtils
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.WaitingListEntry

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
            rules = true,
            lotteryId = "some-lottery-id"
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
            rules = true,
            lotteryId = "some-lottery-id"
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
            lotteryController.checkEntrantAccepted(
                any(),
                any(),
                any(),
                any()
            )
        } answers {
            thirdArg<(Boolean) -> Unit>().invoke(true)
        }
        every {
            lotteryController.isBookingOpen(any(), capture(onBookingOpen), any())
        } answers {
            onBookingOpen.captured.invoke(true)
        }
        every {
            availability.getCampsite(any(), any(), any())
        } answers {
            secondArg<(Campsite?) -> Unit>().invoke(
                Campsite(
                    ownerId = "owner1",
                    campName = "Test Campsite",
                    availableStartDate = "2026-11-27",
                    availableEndDate = "2026-12-01",
                    blockedDates = emptyList()
                )
            )
        }
        every {
            availability.getOpenDates(any(), any(), any())
        } answers {
            secondArg<(Set<String>) -> Unit>().invoke(
                setOf(
                    "2026-11-27",
                    "2026-11-28",
                    "2026-11-29",
                    "2026-11-30",
                    "2026-12-01"
                )
            )
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
        every {
            lotteryController.getLottery(any(), any(), any())
        } answers {
            secondArg<(Lottery?) -> Unit>().invoke(
                Lottery(
                    ownerId = "owner1",
                    campsiteIds = listOf("camp1"),
                    bookingStart = 1795737600000L, // November 27, 2026
                    bookingEnd = 1798675200000L,   // December 31, 2026
                    maxBookingDays = 1
                )
            )
        }

        val reservingController =
            ReservationController(lotteryController, availability, reservations, notifications)
        reservingController.confirmPolicies("entry1")

        var failure: Exception? = null

        val lotteryEntryId = WaitingListEntry.makeId(
            "some-lottery-id",
            "entrant1"
        )

        reservingController.confirmPolicies(lotteryEntryId)

        reservingController.createReservation(
            lotteryEntryId = lotteryEntryId,
            entrantId = "entrant1",
            ownerId = "owner1",
            campsiteId = "camp1",
            dates = setOf("2026-12-01"),
            onSuccess = {},
            onFailure = { error -> failure = error },
            guests = 2,
            refundRecorded = false,
            createdAt = 1000L,
            rules = true,
            lotteryId = "some-lottery-id"
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
            lotteryController.checkEntrantAccepted(
                any(),
                any(),
                any(),
                any()
            )
        } answers {
            thirdArg<(Boolean) -> Unit>().invoke(true)
        }
        every {
            lotteryController.isBookingOpen(any(), capture(onBookingOpen), any())
        } answers {
            onBookingOpen.captured.invoke(true)
        }
        every {
            availability.getCampsite(any(), any(), any())
        } answers {
            secondArg<(Campsite?) -> Unit>().invoke(
                Campsite(
                    ownerId = "owner1",
                    campName = "Test Campsite",
                    availableStartDate = "2026-11-27",
                    availableEndDate = "2026-12-01",
                    blockedDates = emptyList()
                )
            )
        }
        every {
            availability.getOpenDates(any(), any(), any())
        } answers {
            secondArg<(Set<String>) -> Unit>().invoke(
                setOf(
                    "2026-11-27",
                    "2026-11-28",
                    "2026-11-29",
                    "2026-11-30",
                    "2026-12-01"
                )
            )
        }
        every {
            reservations.getBookedDates(any(), capture(onBookedDates), any())
        } answers {
            onBookedDates.captured.invoke(setOf("2026-12-01"))
        }
        every {
            lotteryController.getLottery(any(), any(), any())
        } answers {
            secondArg<(Lottery?) -> Unit>().invoke(
                Lottery(
                    ownerId = "owner1",
                    campsiteIds = listOf("camp1"),
                    bookingStart = 1795737600000L, // November 27, 2026
                    bookingEnd = 1798675200000L,   // December 31, 2026
                    maxBookingDays = 1
                )
            )
        }

        val reservingController =
            ReservationController(lotteryController, availability, reservations, notifications)
        reservingController.confirmPolicies("entry1")

        var failure: Exception? = null

        val lotteryEntryId = WaitingListEntry.makeId(
            "some-lottery-id",
            "entrant1"
        )

        reservingController.confirmPolicies(lotteryEntryId)

        reservingController.createReservation(
            lotteryEntryId = lotteryEntryId,
            entrantId = "entrant1",
            ownerId = "owner1",
            campsiteId = "camp1",
            dates = setOf("2026-12-01"),
            onSuccess = {},
            onFailure = { error -> failure = error },
            guests = 2,
            refundRecorded = false,
            createdAt = 1000L,
            rules = true,
            lotteryId = "some-lottery-id"
        )

        Assert.assertTrue(failure is IllegalStateException)
        Assert.assertEquals(
            "One or more selected dates are no longer available",
            failure?.message
        )
        verify(exactly = 0) { reservations.save(any(), any(), any()) }
        verify(exactly = 0) { notifications.notify(any()) }
    }

    /**
     * The gate arms main's US 02.08.02 made checkable (controller.md): the lottery must
     * exist and offer a stay, the campsite must exist and belong to the owner, and the
     * dates must fit the lottery's bound and step one day at a time. Each refusal goes
     * to onFailure and nothing else runs.
     */
    private fun bookingController(
        lottery: Lottery?,
        campsite: Campsite?
    ): ReservationController {
        val lotteryController = mockk<LotteryController>()
        val availability = mockk<Availability>()
        val notifications = mockk<NotificationController>(relaxed = true)

        every {
            lotteryController.getLottery(any(), any(), any())
        } answers {
            secondArg<(Lottery?) -> Unit>().invoke(lottery)
        }
        every {
            availability.getCampsite(any(), any(), any())
        } answers {
            secondArg<(Campsite?) -> Unit>().invoke(campsite)
        }
        return ReservationController(
            lotteryController,
            availability,
            mockk<ReservationRepository>(),
            notifications
        )
    }

    /** Runs the reservation call past the policy gate with the given dates and rules. */
    private fun reserveOn(
        controller: ReservationController,
        dates: Set<String>,
        rules: Boolean
    ): Exception? {
        controller.confirmPolicies("entry1")

        var error: Exception? = null
        controller.createReservation(
            lotteryEntryId = "entry1",
            entrantId = "entrant1",
            ownerId = "owner1",
            campsiteId = "camp1",
            dates = dates,
            onSuccess = {},
            onFailure = { error = it },
            guests = 2,
            refundRecorded = false,
            createdAt = 1000L,
            rules = rules,
            lotteryId = "some-lottery-id"
        )
        return error
    }

    @Test
    fun createReservation_withoutAcceptedRules_fails() {
        val error = reserveOn(bookingController(Lottery(maxBookingDays = 1), Campsite(ownerId = "owner1")), dates = setOf("2026-12-01"), rules = false)

        Assert.assertTrue(error is IllegalStateException)
        Assert.assertEquals(
            "You must accept the campsite rules and policies",
            error?.message
        )
    }

    @Test
    fun createReservation_refuses_when_the_lottery_is_gone() {
        val error = reserveOn(bookingController(null, Campsite(ownerId = "owner1")), dates = setOf("2026-12-01"), rules = true)

        Assert.assertTrue(error is NoSuchElementException)
        Assert.assertEquals("Lottery not found", error?.message)
    }

    @Test
    fun createReservation_refuses_a_lottery_that_offers_no_stay() {
        val error = reserveOn(bookingController(Lottery(maxBookingDays = 0), Campsite(ownerId = "owner1")), dates = setOf("2026-12-01"), rules = true)

        Assert.assertTrue(error is IllegalStateException)
        Assert.assertEquals(
            "This lottery has an invalid maximum booking length",
            error?.message
        )
    }

    @Test
    fun createReservation_refuses_a_campsite_that_is_not_there() {
        val error = reserveOn(bookingController(Lottery(maxBookingDays = 1), null), dates = setOf("2026-12-01"), rules = true)

        Assert.assertTrue(error is NoSuchElementException)
        Assert.assertEquals("Campsite not found", error?.message)
    }

    @Test
    fun createReservation_refuses_an_owner_who_does_not_own_the_campsite() {
        val error = reserveOn(
            bookingController(
                Lottery(maxBookingDays = 1),
                Campsite(ownerId = "someone-else")
            ),
            dates = setOf("2026-12-01"),
            rules = true
        )

        Assert.assertTrue(error is SecurityException)
        Assert.assertEquals(
            "The selected owner does not own this campsite",
            error?.message
        )
    }

    @Test
    fun createReservation_refuses_more_dates_than_the_lottery_offers() {
        val error = reserveOn(
            bookingController(Lottery(maxBookingDays = 1), Campsite(ownerId = "owner1")),
            dates = setOf("2026-12-01", "2026-12-02"),
            rules = true
        )

        Assert.assertTrue(error is IllegalArgumentException)
        Assert.assertEquals("You can select at most 1 consecutive days", error?.message)
    }

    @Test
    fun createReservation_refuses_dates_that_are_not_consecutive() {
        val error = reserveOn(
            bookingController(Lottery(maxBookingDays = 2), Campsite(ownerId = "owner1")),
            dates = setOf("2026-12-01", "2026-12-03"),
            rules = true
        )

        Assert.assertTrue(error is IllegalArgumentException)
        Assert.assertEquals("Your reservation dates must be consecutive", error?.message)
    }

    /**
     * The success half of the gate (controller.md step 8): the model handed to
     * onSuccess carries the dates SORTED as text, startDate the first and endDate the
     * last, and status PENDING — a reservation is NOT active when it is made.
     */
    @Test
    fun createReservation_builds_the_model_the_spec_states() {
        val lotteryController = mockk<LotteryController>()
        val availability = mockk<Availability>()
        val reservations = mockk<ReservationRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)

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
            lotteryController.checkEntrantAccepted(any(), any(), any(), any())
        } answers {
            thirdArg<(Boolean) -> Unit>().invoke(true)
        }
        every {
            lotteryController.isBookingOpen(any(), capture(onBookingOpen), any())
        } answers {
            onBookingOpen.captured.invoke(true)
        }
        every {
            lotteryController.getLottery(any(), any(), any())
        } answers {
            secondArg<(Lottery?) -> Unit>().invoke(
                Lottery(
                    ownerId = "owner1",
                    campsiteIds = listOf("camp1"),
                    bookingStart = 1795737600000L, // November 27, 2026
                    bookingEnd = 1798675200000L,   // December 31, 2026
                    maxBookingDays = 2
                )
            )
        }
        every {
            availability.getCampsite(any(), any(), any())
        } answers {
            secondArg<(Campsite?) -> Unit>().invoke(
                Campsite(ownerId = "owner1", campName = "Test Campsite")
            )
        }
        every {
            availability.getOpenDates(any(), capture(onOpenDates), any())
        } answers {
            onOpenDates.captured.invoke(setOf("2026-12-02", "2026-12-01"))
        }
        every {
            reservations.getBookedDates(any(), capture(onBookedDates), any())
        } answers {
            onBookedDates.captured.invoke(setOf())
        }
        every {
            reservations.save(any(), capture(onSaved), any())
        } answers {
            onSaved.captured.invoke()
        }

        val reservingController =
            ReservationController(lotteryController, availability, reservations, notifications)

        val lotteryEntryId = WaitingListEntry.makeId("some-lottery-id", "entrant1")
        reservingController.confirmPolicies(lotteryEntryId)

        var built: ReservationModel? = null
        reservingController.createReservation(
            lotteryEntryId = lotteryEntryId,
            entrantId = "entrant1",
            ownerId = "owner1",
            campsiteId = "camp1",
            // offered out of order: the model must sort them as text
            dates = setOf("2026-12-02", "2026-12-01"),
            onSuccess = { built = it },
            onFailure = {},
            guests = 2,
            refundRecorded = false,
            createdAt = 1000L,
            rules = true,
            lotteryId = "some-lottery-id"
        )

        Assert.assertNotNull(built)
        Assert.assertEquals(listOf("2026-12-01", "2026-12-02"), built?.dates)
        Assert.assertEquals("2026-12-01", built?.startDate)
        Assert.assertEquals("2026-12-02", built?.endDate)
        Assert.assertEquals(ReservationStatus.PENDING, built?.status)
    }

    /** The record is read with getByLotteryEntry; an absent one is named in the refusal. */
    @Test
    fun cancelReservation_names_a_record_that_is_not_there() {
        val reservations = mockk<ReservationRepository>()
        val onFound = slot<Function1<ReservationModel?, Unit>>()
        every {
            reservations.getByLotteryEntry(any(), capture(onFound), any())
        } answers {
            onFound.captured.invoke(null)
        }
        val reservingController = ReservationController(
            mockk<LotteryController>(),
            mockk<Availability>(),
            reservations,
            mockk<NotificationController>(relaxed = true)
        )

        var error: Exception? = null
        reservingController.cancelReservation(
            reservationId = "entry1",
            requesterId = "entrant1",
            onSuccess = {},
            onFailure = { error = it }
        )

        Assert.assertTrue(error is NoSuchElementException)
        Assert.assertEquals("No reservation entry1", error?.message)
    }

    @Test
    fun cancelReservation_only_the_entrant_can_cancel() {
        val reservations = mockk<ReservationRepository>()
        val onFound = slot<Function1<ReservationModel?, Unit>>()
        every {
            reservations.getByLotteryEntry(any(), capture(onFound), any())
        } answers {
            onFound.captured.invoke(
                ReservationModel(
                    lotteryEntryId = "entry1",
                    campsiteId = "camp1",
                    entrantId = "entrant1",
                    ownerId = "owner1",
                    dates = listOf("2026-12-01"),
                    startDate = "2026-12-01",
                    endDate = "2026-12-01",
                    status = ReservationStatus.PENDING
                )
            )
        }
        val reservingController = ReservationController(
            mockk<LotteryController>(),
            mockk<Availability>(),
            reservations,
            mockk<NotificationController>(relaxed = true)
        )

        var error: Exception? = null
        reservingController.cancelReservation(
            reservationId = "entry1",
            requesterId = "someone-else",
            onSuccess = {},
            onFailure = { error = it }
        )

        Assert.assertTrue(error is SecurityException)
        Assert.assertEquals(
            "Only the entrant can cancel this reservation",
            error?.message
        )
    }

    @Test
    fun rejectReservation_only_the_owner_can_reject() {
        val reservations = mockk<ReservationRepository>()
        val onFound = slot<Function1<ReservationModel?, Unit>>()
        every {
            reservations.getByLotteryEntry(any(), capture(onFound), any())
        } answers {
            onFound.captured.invoke(
                ReservationModel(
                    lotteryEntryId = "entry1",
                    campsiteId = "camp1",
                    entrantId = "entrant1",
                    ownerId = "owner1",
                    dates = listOf("2026-12-01"),
                    startDate = "2026-12-01",
                    endDate = "2026-12-01",
                    status = ReservationStatus.PENDING
                )
            )
        }
        val reservingController = ReservationController(
            mockk<LotteryController>(),
            mockk<Availability>(),
            reservations,
            mockk<NotificationController>(relaxed = true)
        )

        var error: Exception? = null
        reservingController.rejectReservation(
            reservationId = "entry1",
            requesterId = "someone-else",
            onSuccess = {},
            onFailure = { error = it }
        )

        Assert.assertTrue(error is SecurityException)
        Assert.assertEquals(
            "Only the campsite owner can reject this reservation",
            error?.message
        )
    }

    /** The entrant cancelling their own stay: markCancelled runs and it is reported. */
    @Test
    fun cancelReservation_the_entrant_cancels_their_own_stay() {
        val reservations = mockk<ReservationRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val onFound = slot<Function1<ReservationModel?, Unit>>()
        every {
            reservations.getByLotteryEntry(any(), capture(onFound), any())
        } answers {
            onFound.captured.invoke(
                ReservationModel(
                    lotteryEntryId = "entry1",
                    campsiteId = "camp1",
                    entrantId = "entrant1",
                    ownerId = "owner1",
                    dates = listOf("2026-12-01"),
                    startDate = "2026-12-01",
                    endDate = "2026-12-01",
                    status = ReservationStatus.PENDING
                )
            )
        }
        val onCancelled = slot<Function0<Unit>>()
        every {
            reservations.markCancelled(any(), capture(onCancelled), any())
        } answers {
            onCancelled.captured.invoke()
        }
        val reservingController = ReservationController(
            mockk<LotteryController>(),
            mockk<Availability>(),
            reservations,
            notifications
        )

        var done = false
        reservingController.cancelReservation(
            reservationId = "entry1",
            requesterId = "entrant1",
            onSuccess = { done = true },
            onFailure = {}
        )

        Assert.assertTrue(done)
        verify(exactly = 1) { reservations.markCancelled(any(), any(), any()) }
        verify(exactly = 1) { notifications.notify(any()) }
    }
}
