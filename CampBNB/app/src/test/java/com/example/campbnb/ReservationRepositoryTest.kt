package com.example.campbnb

// AI-assisted (US 01.05.02): written by Qwen 3.8 Flash IQ1_M. The verbatim prompts for
// this work are recorded in AI_Disclosure/Wyatt_AI_Use.md.

import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.database.QueryEngine
import com.example.campbnb.database.WriteEngine
import com.example.campbnb.models.BookedDate
import com.example.campbnb.models.DateUtils
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOTICE: This file was AI-assisted; see the disclosure record above.
 *
 * The reservation state machine (data.md, ReservationRepository.kt): the class is not a
 * seam, so it is stood up with stubbed engines and tested through their answers. The
 * cases pin the storage rules — save claims the entry id and hands the store only
 * PENDING reservations, claims dates one at a time and rolls back exactly what it
 * claimed, close refuses only states that are neither PENDING nor ACTIVE and writes the
 * status fields before releasing dates, and activateDueReservations moves only the
 * records whose start date has arrived.
 */
class ReservationRepositoryTest {

    /** Stands the repository up over stubbed engines; returns the three mocks. */
    private fun engines(
        engine: QueryEngine<ReservationModel>,
        chain: QueryEngine.QueryChain<ReservationModel>,
        reservationWriter: WriteEngine<ReservationModel>,
        bookedDateWriter: WriteEngine<BookedDate>
    ) {
        every { engine.query() } returns chain
        every { chain.whereEquals(any(), any()) } returns chain
        every { chain.whereIn(any(), any()) } returns chain
        every { chain.whereGreaterThanOrEqual(any(), any()) } returns chain
        every { chain.whereGreaterThan(any(), any()) } returns chain
        every { chain.whereLessThan(any(), any()) } returns chain
        every { chain.orderBy(any(), any()) } returns chain
        every { chain.limitResults(any()) } returns chain
    }

    private fun repository(
        engine: QueryEngine<ReservationModel>,
        reservationWriter: WriteEngine<ReservationModel>,
        bookedDateWriter: WriteEngine<BookedDate>
    ): ReservationRepository =
        ReservationRepository(engine, reservationWriter, bookedDateWriter)

    @Test
    fun save_claims_the_entry_id_and_hands_the_store_only_pending_reservations() {
        val engine = mockk<QueryEngine<ReservationModel>>()
        val chain = mockk<QueryEngine.QueryChain<ReservationModel>>()
        val reservationWriter = mockk<WriteEngine<ReservationModel>>()
        val bookedDateWriter = mockk<WriteEngine<BookedDate>>()
        engines(engine, chain, reservationWriter, bookedDateWriter)

        val onExists = slot<Function1<Boolean, Unit>>()
        every {
            engine.exists(any(), capture(onExists), any())
        } answers {
            onExists.captured.invoke(false)
        }
        val onClaimed = slot<Function1<Result<Exception?>, Unit>>()
        every {
            bookedDateWriter.save(any(), capture(onClaimed))
        } answers {
            onClaimed.captured.invoke(Result.success(null))
        }
        val onSaved = slot<Function1<Result<Exception?>, Unit>>()
        every {
            reservationWriter.save(any(), capture(onSaved))
        } answers {
            onSaved.captured.invoke(Result.success(null))
        }

        // the caller built a reservation that CLAIMS to be active and carries its own id
        val reservation = ReservationModel(
            lotteryEntryId = "entry1",
            campsiteId = "camp1",
            entrantId = "entrant1",
            ownerId = "owner1",
            dates = listOf("2026-12-01"),
            startDate = "2026-12-01",
            endDate = "2026-12-01",
            status = ReservationStatus.ACTIVE
        )

        var done = false
        var error: Exception? = null
        repository(engine, reservationWriter, bookedDateWriter)
            .save(
                reservation = reservation,
                onSuccess = { done = true },
                onFailure = { error = it }
            )

        assertTrue(done)
        assertEquals(null, error)
        // the id is claimed on the object itself, and the status is overwritten to PENDING
        assertEquals("entry1", reservation.id)
        assertEquals(ReservationStatus.PENDING, reservation.status)
        verify(exactly = 1) { reservationWriter.save(any(), any()) }
    }

    @Test
    fun save_refuses_a_second_reservation_for_the_same_entry() {
        val engine = mockk<QueryEngine<ReservationModel>>()
        val chain = mockk<QueryEngine.QueryChain<ReservationModel>>()
        val reservationWriter = mockk<WriteEngine<ReservationModel>>()
        val bookedDateWriter = mockk<WriteEngine<BookedDate>>()
        engines(engine, chain, reservationWriter, bookedDateWriter)

        val onExists = slot<Function1<Boolean, Unit>>()
        every {
            engine.exists(any(), capture(onExists), any())
        } answers {
            onExists.captured.invoke(true)
        }

        var error: Exception? = null
        repository(engine, reservationWriter, bookedDateWriter)
            .save(
                reservation = ReservationModel(lotteryEntryId = "entry1"),
                onSuccess = {},
                onFailure = { error = it }
            )

        assertTrue(error is IllegalStateException)
        assertEquals("Entry already has a reservation", error?.message)
        verify(exactly = 0) { bookedDateWriter.save(any(), any()) }
        verify(exactly = 0) { reservationWriter.save(any(), any()) }
    }

    @Test
    fun save_releases_only_the_dates_it_had_claimed_when_a_claim_conflicts() {
        val engine = mockk<QueryEngine<ReservationModel>>()
        val chain = mockk<QueryEngine.QueryChain<ReservationModel>>()
        val reservationWriter = mockk<WriteEngine<ReservationModel>>()
        val bookedDateWriter = mockk<WriteEngine<BookedDate>>()
        engines(engine, chain, reservationWriter, bookedDateWriter)

        val onExists = slot<Function1<Boolean, Unit>>()
        every {
            engine.exists(any(), capture(onExists), any())
        } answers {
            onExists.captured.invoke(false)
        }

        // the second claim is taken; the first was this call's own and must be released
        val claimed = slot<BookedDate>()
        val onClaimed = slot<Function1<Result<Exception?>, Unit>>()
        every {
            bookedDateWriter.save(capture(claimed), capture(onClaimed))
        } answers {
            if (claimed.captured.id == "camp1_2026-12-02") {
                onClaimed.captured.invoke(Result.failure(Exception("already there")))
            } else {
                onClaimed.captured.invoke(Result.success(null))
            }
        }
        val released = mutableListOf<String>()
        val releasedId = slot<String>()
        val onReleased = slot<Function1<Result<Exception?>, Unit>>()
        every {
            bookedDateWriter.delete(capture(releasedId), capture(onReleased))
        } answers {
            released.add(releasedId.captured)
            onReleased.captured.invoke(Result.success(null))
        }

        var error: Exception? = null
        repository(engine, reservationWriter, bookedDateWriter)
            .save(
                reservation = ReservationModel(
                    lotteryEntryId = "entry1",
                    campsiteId = "camp1",
                    dates = listOf("2026-12-01", "2026-12-02", "2026-12-03"),
                    startDate = "2026-12-01",
                    endDate = "2026-12-03"
                ),
                onSuccess = {},
                onFailure = { error = it }
            )

        assertTrue(error is IllegalStateException)
        assertEquals("One or more dates are already booked", error?.message)
        // only the date this call had claimed is released; the third was never claimed
        assertEquals(listOf("camp1_2026-12-01"), released)
        verify(exactly = 0) { reservationWriter.save(any(), any()) }
    }

    @Test
    fun save_releases_every_claimed_date_when_the_reservation_itself_fails() {
        val engine = mockk<QueryEngine<ReservationModel>>()
        val chain = mockk<QueryEngine.QueryChain<ReservationModel>>()
        val reservationWriter = mockk<WriteEngine<ReservationModel>>()
        val bookedDateWriter = mockk<WriteEngine<BookedDate>>()
        engines(engine, chain, reservationWriter, bookedDateWriter)

        val onExists = slot<Function1<Boolean, Unit>>()
        every {
            engine.exists(any(), capture(onExists), any())
        } answers {
            onExists.captured.invoke(false)
        }
        val onClaimed = slot<Function1<Result<Exception?>, Unit>>()
        every {
            bookedDateWriter.save(any(), capture(onClaimed))
        } answers {
            onClaimed.captured.invoke(Result.success(null))
        }
        val onSaved = slot<Function1<Result<Exception?>, Unit>>()
        every {
            reservationWriter.save(any(), capture(onSaved))
        } answers {
            onSaved.captured.invoke(Result.failure(Exception("the store said no")))
        }
        val released = mutableListOf<String>()
        val releasedId = slot<String>()
        val onReleased = slot<Function1<Result<Exception?>, Unit>>()
        every {
            bookedDateWriter.delete(capture(releasedId), capture(onReleased))
        } answers {
            released.add(releasedId.captured)
            onReleased.captured.invoke(Result.success(null))
        }

        var error: Exception? = null
        repository(engine, reservationWriter, bookedDateWriter)
            .save(
                reservation = ReservationModel(
                    lotteryEntryId = "entry1",
                    campsiteId = "camp1",
                    dates = listOf("2026-12-01", "2026-12-02"),
                    startDate = "2026-12-01",
                    endDate = "2026-12-02"
                ),
                onSuccess = {},
                onFailure = { error = it }
            )

        // the save's own exception is passed on, and every claimed date is released
        assertEquals("the store said no", error?.message)
        assertEquals(
            listOf("camp1_2026-12-01", "camp1_2026-12-02"),
            released
        )
    }

    @Test
    fun close_refuses_a_stay_that_is_already_closed() {
        val engine = mockk<QueryEngine<ReservationModel>>()
        val chain = mockk<QueryEngine.QueryChain<ReservationModel>>()
        val reservationWriter = mockk<WriteEngine<ReservationModel>>()
        val bookedDateWriter = mockk<WriteEngine<BookedDate>>()
        engines(engine, chain, reservationWriter, bookedDateWriter)

        val onFound = slot<Function1<ReservationModel?, Unit>>()
        every {
            engine.getById(any(), capture(onFound), any())
        } answers {
            onFound.captured.invoke(
                ReservationModel(
                    lotteryEntryId = "entry1",
                    campsiteId = "camp1",
                    dates = listOf("2026-12-01"),
                    startDate = "2026-12-01",
                    endDate = "2026-12-01",
                    status = ReservationStatus.CANCELLED
                )
            )
        }

        var error: Exception? = null
        repository(engine, reservationWriter, bookedDateWriter)
            .markCancelled(
                reservationId = "entry1",
                onSuccess = {},
                onFailure = { error = it }
            )

        assertTrue(error is IllegalStateException)
        assertEquals("Reservation is already CANCELLED", error?.message)
        verify(exactly = 0) { reservationWriter.update(any(), any(), any()) }
    }

    @Test
    fun close_still_opens_a_stay_that_has_not_started() {
        val engine = mockk<QueryEngine<ReservationModel>>()
        val chain = mockk<QueryEngine.QueryChain<ReservationModel>>()
        val reservationWriter = mockk<WriteEngine<ReservationModel>>()
        val bookedDateWriter = mockk<WriteEngine<BookedDate>>()
        engines(engine, chain, reservationWriter, bookedDateWriter)

        val onFound = slot<Function1<ReservationModel?, Unit>>()
        every {
            engine.getById(any(), capture(onFound), any())
        } answers {
            onFound.captured.invoke(
                ReservationModel(
                    lotteryEntryId = "entry1",
                    campsiteId = "camp1",
                    dates = listOf("2026-12-01"),
                    startDate = "2026-12-01",
                    endDate = "2026-12-01",
                    status = ReservationStatus.PENDING
                )
            )
        }
        val writtenFields = slot<Map<String, Any?>>()
        val onUpdated = slot<Function1<Result<Exception?>, Unit>>()
        every {
            reservationWriter.update(any(), capture(writtenFields), capture(onUpdated))
        } answers {
            onUpdated.captured.invoke(Result.success(null))
        }
        val onReleased = slot<Function1<Result<Exception?>, Unit>>()
        every {
            bookedDateWriter.delete(any(), capture(onReleased))
        } answers {
            onReleased.captured.invoke(Result.success(null))
        }

        var done = false
        repository(engine, reservationWriter, bookedDateWriter)
            .markCancelled(
                reservationId = "entry1",
                onSuccess = { done = true },
                onFailure = {}
            )

        // PENDING is a state a real reservation can be in: the widened guard allows it
        assertTrue(done)
        // the status fields are written FIRST, exactly as data.md states
        assertEquals("CANCELLED", writtenFields.captured["status"])
        assertEquals(true, writtenFields.captured["refundRecorded"])
        verify(exactly = 1) { bookedDateWriter.delete(any(), any()) }
    }

    @Test
    fun close_ignores_a_failed_date_release() {
        val engine = mockk<QueryEngine<ReservationModel>>()
        val chain = mockk<QueryEngine.QueryChain<ReservationModel>>()
        val reservationWriter = mockk<WriteEngine<ReservationModel>>()
        val bookedDateWriter = mockk<WriteEngine<BookedDate>>()
        engines(engine, chain, reservationWriter, bookedDateWriter)

        val onFound = slot<Function1<ReservationModel?, Unit>>()
        every {
            engine.getById(any(), capture(onFound), any())
        } answers {
            onFound.captured.invoke(
                ReservationModel(
                    lotteryEntryId = "entry1",
                    campsiteId = "camp1",
                    dates = listOf("2026-12-01"),
                    startDate = "2026-12-01",
                    endDate = "2026-12-01",
                    status = ReservationStatus.ACTIVE
                )
            )
        }
        val onUpdated = slot<Function1<Result<Exception?>, Unit>>()
        every {
            reservationWriter.update(any(), any(), capture(onUpdated))
        } answers {
            onUpdated.captured.invoke(Result.success(null))
        }
        // the release fails — date release never blocks
        every {
            bookedDateWriter.delete(any(), any())
        } answers {
            secondArg<(Result<Exception?>) -> Unit>().invoke(
                Result.failure(Exception("the store said no"))
            )
        }

        var done = false
        repository(engine, reservationWriter, bookedDateWriter)
            .markRejected(
                reservationId = "entry1",
                onSuccess = { done = true },
                onFailure = {}
            )

        assertTrue(done)
    }

    @Test
    fun activate_moves_only_reservations_whose_start_has_arrived() {
        val engine = mockk<QueryEngine<ReservationModel>>()
        val chain = mockk<QueryEngine.QueryChain<ReservationModel>>()
        val reservationWriter = mockk<WriteEngine<ReservationModel>>()
        val bookedDateWriter = mockk<WriteEngine<BookedDate>>()
        engines(engine, chain, reservationWriter, bookedDateWriter)

        val onFound = slot<Function1<List<ReservationModel>, Unit>>()
        every {
            chain.execute(capture(onFound), any())
        } answers {
            onFound.captured.invoke(listOf(
                ReservationModel(
                    campsiteId = "camp-one",
                    lotteryEntryId = "entry-started",
                    dates = listOf("2020-11-01"),
                    startDate = "2020-11-01",
                    endDate = "2020-11-03",
                    status = ReservationStatus.PENDING
                ).also { it.id = "entry-started" },
                ReservationModel(
                    campsiteId = "camp-one",
                    lotteryEntryId = "entry-boundary",
                    dates = listOf(DateUtils.today()),
                    startDate = DateUtils.today(),
                    endDate = DateUtils.today(),
                    status = ReservationStatus.PENDING
                ).also { it.id = "entry-boundary" },
                ReservationModel(
                    campsiteId = "camp-one",
                    lotteryEntryId = "entry-future",
                    dates = listOf("2099-01-01"),
                    startDate = "2099-01-01",
                    endDate = "2099-01-03",
                    status = ReservationStatus.PENDING
                ).also { it.id = "entry-future" },
                ReservationModel(
                    campsiteId = "camp-one",
                    lotteryEntryId = "entry-blank",
                    startDate = "",
                    status = ReservationStatus.PENDING
                ).also { it.id = "entry-blank" }
            ))
        }

        val updatedIds = mutableListOf<String>()
        val updatedId = slot<String>()
        val writtenFields = slot<Map<String, Any?>>()
        val onUpdated = slot<Function1<Result<Exception?>, Unit>>()
        every {
            reservationWriter.update(capture(updatedId), capture(writtenFields), capture(onUpdated))
        } answers {
            updatedIds.add(updatedId.captured)
            onUpdated.captured.invoke(Result.success(null))
        }

        var result: Result<Int>? = null
        repository(engine, reservationWriter, bookedDateWriter)
            .activateDueReservations { result = it }

        assertTrue(result?.isSuccess == true)
        assertEquals(2, result?.getOrNull())
        assertEquals(listOf("entry-started", "entry-boundary"), updatedIds)
        assertEquals("ACTIVE", writtenFields.captured["status"])
        assertEquals(1, writtenFields.captured.size)
    }

    @Test
    fun activate_a_passed_today_selects_but_the_walkers_own_clock_decides() {
        val engine = mockk<QueryEngine<ReservationModel>>()
        val chain = mockk<QueryEngine.QueryChain<ReservationModel>>()
        val reservationWriter = mockk<WriteEngine<ReservationModel>>()
        val bookedDateWriter = mockk<WriteEngine<BookedDate>>()
        engines(engine, chain, reservationWriter, bookedDateWriter)

        val onFound = slot<Function1<List<ReservationModel>, Unit>>()
        every {
            chain.execute(capture(onFound), any())
        } answers {
            onFound.captured.invoke(listOf(
                ReservationModel(
                    campsiteId = "camp-one",
                    lotteryEntryId = "entry-far",
                    dates = listOf("2099-06-01"),
                    startDate = "2099-06-01",
                    endDate = "2099-06-03",
                    status = ReservationStatus.PENDING
                ).also { it.id = "entry-far" }
            ))
        }

        var result: Result<Int>? = null
        repository(engine, reservationWriter, bookedDateWriter)
            .activateDueReservations(
                today = "2099-12-31",
                onComplete = { result = it }
            )

        assertTrue(result?.isSuccess == true)
        assertEquals(0, result?.getOrNull())
        verify(exactly = 0) { reservationWriter.update(any(), any(), any()) }
    }

    @Test
    fun activate_stops_at_the_first_failed_write_and_carries_its_cause() {
        val engine = mockk<QueryEngine<ReservationModel>>()
        val chain = mockk<QueryEngine.QueryChain<ReservationModel>>()
        val reservationWriter = mockk<WriteEngine<ReservationModel>>()
        val bookedDateWriter = mockk<WriteEngine<BookedDate>>()
        engines(engine, chain, reservationWriter, bookedDateWriter)

        val onFound = slot<Function1<List<ReservationModel>, Unit>>()
        every {
            chain.execute(capture(onFound), any())
        } answers {
            onFound.captured.invoke(listOf(
                ReservationModel(
                    campsiteId = "camp-one",
                    lotteryEntryId = "entry-first",
                    dates = listOf("2020-11-01"),
                    startDate = "2020-11-01",
                    endDate = "2020-11-03",
                    status = ReservationStatus.PENDING
                ).also { it.id = "entry-first" },
                ReservationModel(
                    campsiteId = "camp-one",
                    lotteryEntryId = "entry-second",
                    dates = listOf("2020-11-02"),
                    startDate = "2020-11-02",
                    endDate = "2020-11-04",
                    status = ReservationStatus.PENDING
                ).also { it.id = "entry-second" }
            ))
        }

        val updatedIds = mutableListOf<String>()
        val updatedId = slot<String>()
        val onUpdated = slot<Function1<Result<Exception?>, Unit>>()
        every {
            reservationWriter.update(capture(updatedId), any(), capture(onUpdated))
        } answers {
            updatedIds.add(updatedId.captured)
            onUpdated.captured.invoke(
                Result.failure(IllegalStateException("the store refused the write"))
            )
        }

        var result: Result<Int>? = null
        repository(engine, reservationWriter, bookedDateWriter)
            .activateDueReservations { result = it }

        assertTrue(result?.isFailure == true)
        assertEquals("the store refused the write", result?.exceptionOrNull()?.message)
        assertEquals(listOf("entry-first"), updatedIds)
    }

    @Test
    fun activate_reports_the_read_failure_instead_of_moving_anything() {
        val engine = mockk<QueryEngine<ReservationModel>>()
        val chain = mockk<QueryEngine.QueryChain<ReservationModel>>()
        val reservationWriter = mockk<WriteEngine<ReservationModel>>()
        val bookedDateWriter = mockk<WriteEngine<BookedDate>>()
        engines(engine, chain, reservationWriter, bookedDateWriter)

        val onFailed = slot<Function1<Exception, Unit>>()
        every {
            chain.execute(any(), capture(onFailed))
        } answers {
            onFailed.captured.invoke(Exception("the store said no"))
        }

        var result: Result<Int>? = null
        repository(engine, reservationWriter, bookedDateWriter)
            .activateDueReservations(
                today = "2026-12-05",
                onComplete = { result = it }
            )

        assertTrue(result?.isFailure == true)
        assertEquals("the store said no", result?.exceptionOrNull()?.message)
        verify(exactly = 0) { reservationWriter.update(any(), any(), any()) }
    }
}
