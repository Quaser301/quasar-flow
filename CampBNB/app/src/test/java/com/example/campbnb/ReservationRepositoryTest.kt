package com.example.campbnb.data

import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

private const val TODAY = "2026-07-10"

// ---------- Fake Firebase Tasks that fire their listeners immediately ----------

@Suppress("UNCHECKED_CAST")
private fun <T> successTask(value: T): Task<T> {
    val task = mockk<Task<T>>()
    every { task.addOnSuccessListener(any<OnSuccessListener<in T>>()) } answers {
        (firstArg<Any>() as OnSuccessListener<T>).onSuccess(value)
        task
    }
    every { task.addOnFailureListener(any<OnFailureListener>()) } returns task
    return task
}

private fun <T> failedTask(error: Exception): Task<T> {
    val task = mockk<Task<T>>()
    every { task.addOnSuccessListener(any<OnSuccessListener<in T>>()) } returns task
    every { task.addOnFailureListener(any<OnFailureListener>()) } answers {
        firstArg<OnFailureListener>().onFailure(error)
        task
    }
    return task
}

/**
 * Unit tests for [ReservationRepository].
 *
 * Firestore is mocked, so these tests check that each method builds the right query or
 * transaction and routes results and errors to the right callback. They cannot prove that
 * Firestore indexes exist or that real transactions behave under concurrency; use the
 * Firebase Emulator for that.
 *
 * @author Zino
 * @see ReservationRepository
 */
class ReservationRepositoryTest {

    private lateinit var db: FirebaseFirestore
    private lateinit var reservationsCol: CollectionReference
    private lateinit var bookedCol: CollectionReference
    private lateinit var tx: Transaction
    private lateinit var repo: ReservationRepository

    // One stable mock per document ID, so tests can stub and verify specific documents.
    private val resDocs = mutableMapOf<String, DocumentReference>()
    private val bookedDocs = mutableMapOf<String, DocumentReference>()

    @Before
    fun setUp() {
        db = mockk()
        reservationsCol = mockk()
        bookedCol = mockk()
        tx = mockk(relaxed = true)

        every { db.collection("Reservations") } returns reservationsCol
        every { db.collection("BookedDates") } returns bookedCol

        every { reservationsCol.document(any<String>()) } answers {
            resDocs.getOrPut(firstArg<String>()) { mockk() }
        }
        every { bookedCol.document(any<String>()) } answers {
            bookedDocs.getOrPut(firstArg<String>()) { mockk() }
        }

        // The query chain: every clause returns the same mock so calls can be verified in order.
        every { reservationsCol.whereEqualTo(any<String>(), any()) } returns reservationsCol
        every { reservationsCol.whereGreaterThan(any<String>(), any()) } returns reservationsCol
        every { reservationsCol.whereGreaterThanOrEqualTo(any<String>(), any()) } returns reservationsCol
        every { reservationsCol.whereLessThan(any<String>(), any()) } returns reservationsCol
        every { reservationsCol.whereIn(any<String>(), any<List<Any>>()) } returns reservationsCol
        every { reservationsCol.orderBy(any<String>(), any<Query.Direction>()) } returns reservationsCol

        // Run transaction bodies immediately against the mock transaction.
        every { db.runTransaction(any<Transaction.Function<Any?>>()) } answers {
            val body = firstArg<Transaction.Function<Any?>>()
            try {
                successTask<Any?>(body.apply(tx))
            } catch (e: Exception) {
                failedTask<Any?>(e)
            }
        }

        repo = ReservationRepository(db)
    }

    // ---------- Helpers ----------

    private fun reservation(
        entryId: String = "entry1",
        campsiteId: String = "camp1",
        entrantId: String = "user1",
        ownerId: String = "owner1",
        dates: List<String> = listOf("2026-07-14", "2026-07-15"),
        status: ReservationStatus = ReservationStatus.ACTIVE,
        guests: Int = 3
    ) = ReservationModel(
        lotteryEntryId = entryId,
        campsiteId = campsiteId,
        entrantId = entrantId,
        ownerId = ownerId,
        dates = dates,
        startDate = dates.first(),
        endDate = dates.last(),
        status = status,
        guests = guests,
        refundRecorded = false,
        createdAt = System.currentTimeMillis(),
        rules = true
    )

    private fun stubQueryResult(results: List<ReservationModel>) {
        val snapshot = mockk<QuerySnapshot>()
        every { snapshot.toObjects(ReservationModel::class.java) } returns results
        every { reservationsCol.get() } returns successTask(snapshot)
    }

    private fun stubQueryFailure(error: Exception) {
        every { reservationsCol.get() } returns failedTask<QuerySnapshot>(error)
    }

    private fun stubDocExists(ref: DocumentReference, exists: Boolean) {
        val snap = mockk<DocumentSnapshot>()
        every { snap.exists() } returns exists
        every { tx.get(ref) } returns snap
    }

    private fun stubDocContents(ref: DocumentReference, model: ReservationModel?) {
        val snap = mockk<DocumentSnapshot>()
        every { snap.toObject(ReservationModel::class.java) } returns model
        every { tx.get(ref) } returns snap
    }

    // ---------- getActive ----------

    @Test
    fun `getActive filters by campsite, active status and endDate on or after today, soonest first`() {
        val expected = listOf(reservation())
        stubQueryResult(expected)
        var result: List<ReservationModel>? = null

        repo.getActive("camp1", onSuccess = { result = it }, onFailure = { fail("unexpected: $it") }, today = TODAY)

        assertEquals(expected, result)
        verifyOrder {
            reservationsCol.whereEqualTo("campsiteId", "camp1")
            reservationsCol.whereEqualTo("status", "ACTIVE")
            reservationsCol.whereGreaterThanOrEqualTo("endDate", TODAY)
            reservationsCol.orderBy("endDate", Query.Direction.ASCENDING)
        }
    }

    @Test
    fun `getActive includes reservations ending today`() {
        stubQueryResult(emptyList())

        repo.getActive("camp1", onSuccess = {}, onFailure = { fail("unexpected: $it") }, today = TODAY)

        // A strict greater-than would drop a reservation whose endDate equals today.
        verify(exactly = 0) { reservationsCol.whereGreaterThan(any<String>(), any()) }
    }

    @Test
    fun `getActive passes failures to onFailure`() {
        val boom = RuntimeException("boom")
        stubQueryFailure(boom)
        var error: Exception? = null

        repo.getActive("camp1", onSuccess = { fail("should not succeed") }, onFailure = { error = it }, today = TODAY)

        assertSame(boom, error)
    }

    // ---------- getPast ----------

    @Test
    fun `getPast filters active reservations that ended before today, oldest first`() {
        val expected = listOf(reservation(dates = listOf("2026-06-01", "2026-06-02")))
        stubQueryResult(expected)
        var result: List<ReservationModel>? = null

        repo.getPast("camp1", onSuccess = { result = it }, onFailure = { fail("unexpected: $it") }, today = TODAY)

        assertEquals(expected, result)
        verifyOrder {
            reservationsCol.whereEqualTo("campsiteId", "camp1")
            reservationsCol.whereEqualTo("status", "ACTIVE")
            reservationsCol.whereLessThan("endDate", TODAY)
            reservationsCol.orderBy("endDate", Query.Direction.ASCENDING)
        }
    }

    @Test
    fun `getPast passes failures to onFailure`() {
        val boom = RuntimeException("boom")
        stubQueryFailure(boom)
        var error: Exception? = null

        repo.getPast("camp1", onSuccess = { fail("should not succeed") }, onFailure = { error = it }, today = TODAY)

        assertSame(boom, error)
    }

    // ---------- getCancelled ----------

    @Test
    fun `getCancelled queries cancelled and rejected statuses for the campsite`() {
        val expected = listOf(
            reservation(entryId = "a", status = ReservationStatus.CANCELLED),
            reservation(entryId = "b", status = ReservationStatus.REJECTED)
        )
        stubQueryResult(expected)
        var result: List<ReservationModel>? = null

        repo.getCancelled("camp1", onSuccess = { result = it }, onFailure = { fail("unexpected: $it") })

        assertEquals(expected, result)
        verifyOrder {
            reservationsCol.whereEqualTo("campsiteId", "camp1")
            reservationsCol.whereIn("status", listOf("CANCELLED", "REJECTED"))
        }
    }

    // ---------- getBookedDates ----------

    @Test
    fun `getBookedDates returns the union of dates from active reservations`() {
        stubQueryResult(listOf(
            reservation(entryId = "a", dates = listOf("2026-07-14", "2026-07-15")),
            reservation(entryId = "b", dates = listOf("2026-07-15", "2026-07-16"))
        ))
        var result: Set<String>? = null

        repo.getBookedDates("camp1", onSuccess = { result = it }, onFailure = { fail("unexpected: $it") })

        assertEquals(setOf("2026-07-14", "2026-07-15", "2026-07-16"), result)
        verifyOrder {
            reservationsCol.whereEqualTo("campsiteId", "camp1")
            reservationsCol.whereEqualTo("status", "ACTIVE")
        }
    }

    @Test
    fun `getBookedDates returns an empty set when there are no reservations`() {
        stubQueryResult(emptyList())
        var result: Set<String>? = null

        repo.getBookedDates("camp1", onSuccess = { result = it }, onFailure = { fail("unexpected: $it") })

        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `getBookedDates passes failures to onFailure`() {
        val boom = RuntimeException("boom")
        stubQueryFailure(boom)
        var error: Exception? = null

        repo.getBookedDates("camp1", onSuccess = { fail("should not succeed") }, onFailure = { error = it })

        assertSame(boom, error)
    }

    // ---------- getForEntrant ----------

    @Test
    fun `getForEntrant filters by entrant and orders by start date`() {
        val expected = listOf(reservation(entrantId = "user1"))
        stubQueryResult(expected)
        var result: List<ReservationModel>? = null

        repo.getForEntrant("user1", onSuccess = { result = it }, onFailure = { fail("unexpected: $it") })

        assertEquals(expected, result)
        verifyOrder {
            reservationsCol.whereEqualTo("entrantId", "user1")
            reservationsCol.orderBy("startDate", Query.Direction.ASCENDING)
        }
    }

    // ---------- getByLotteryEntry / hasReservation ----------

    @Test
    fun `getByLotteryEntry returns the stored reservation`() {
        val expected = reservation()
        val snap = mockk<DocumentSnapshot>()
        every { snap.toObject(ReservationModel::class.java) } returns expected
        every { reservationsCol.document("entry1").get() } returns successTask(snap)
        var result: ReservationModel? = null

        repo.getByLotteryEntry("entry1", onSuccess = { result = it }, onFailure = { fail("unexpected: $it") })

        assertEquals(expected, result)
    }

    @Test
    fun `getByLotteryEntry returns null when no reservation exists`() {
        val snap = mockk<DocumentSnapshot>()
        every { snap.toObject(ReservationModel::class.java) } returns null
        every { reservationsCol.document("entry1").get() } returns successTask(snap)
        var called = false
        var result: ReservationModel? = reservation()

        repo.getByLotteryEntry("entry1", onSuccess = { called = true; result = it }, onFailure = { fail("unexpected: $it") })

        assertTrue(called)
        assertNull(result)
    }

    @Test
    fun `getByLotteryEntry passes failures to onFailure`() {
        val boom = RuntimeException("boom")
        every { reservationsCol.document("entry1").get() } returns failedTask<DocumentSnapshot>(boom)
        var error: Exception? = null

        repo.getByLotteryEntry("entry1", onSuccess = { fail("should not succeed") }, onFailure = { error = it })

        assertSame(boom, error)
    }

    @Test
    fun `hasReservation is true when the document exists`() {
        val snap = mockk<DocumentSnapshot>()
        every { snap.exists() } returns true
        every { reservationsCol.document("entry1").get() } returns successTask(snap)
        var result: Boolean? = null

        repo.hasReservation("entry1", onSuccess = { result = it }, onFailure = { fail("unexpected: $it") })

        assertEquals(true, result)
    }

    @Test
    fun `hasReservation is false when the document does not exist`() {
        val snap = mockk<DocumentSnapshot>()
        every { snap.exists() } returns false
        every { reservationsCol.document("entry1").get() } returns successTask(snap)
        var result: Boolean? = null

        repo.hasReservation("entry1", onSuccess = { result = it }, onFailure = { fail("unexpected: $it") })

        assertEquals(false, result)
    }

    // ---------- save ----------

    @Test
    fun `save writes the reservation and one booked-date document per date`() {
        val r = reservation()
        val resRef = reservationsCol.document("entry1")
        val dateRefs = r.dates.map { bookedCol.document("camp1_$it") }
        stubDocExists(resRef, false)
        dateRefs.forEach { stubDocExists(it, false) }
        var succeeded = false

        repo.save(r, onSuccess = { succeeded = true }, onFailure = { fail("unexpected: $it") })

        assertTrue(succeeded)
        verify { tx.set(resRef, r) }
        dateRefs.forEach {
            verify { tx.set(it, mapOf("campsiteId" to "camp1", "reservationId" to "entry1")) }
        }
    }

    @Test
    fun `save fails and writes nothing when the entry already has a reservation`() {
        val r = reservation()
        val resRef = reservationsCol.document("entry1")
        stubDocExists(resRef, true)
        r.dates.forEach { stubDocExists(bookedCol.document("camp1_$it"), false) }
        var error: Exception? = null

        repo.save(r, onSuccess = { fail("should not succeed") }, onFailure = { error = it })

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("already has a reservation"))
        verify(exactly = 0) { tx.set(any<DocumentReference>(), any<Any>()) }
    }

    @Test
    fun `save fails and writes nothing when any date is already booked`() {
        val r = reservation(dates = listOf("2026-07-14", "2026-07-15"))
        stubDocExists(reservationsCol.document("entry1"), false)
        stubDocExists(bookedCol.document("camp1_2026-07-14"), false)
        stubDocExists(bookedCol.document("camp1_2026-07-15"), true) // second date is taken
        var error: Exception? = null

        repo.save(r, onSuccess = { fail("should not succeed") }, onFailure = { error = it })

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("already booked"))
        verify(exactly = 0) { tx.set(any<DocumentReference>(), any<Any>()) }
    }

    // ---------- markCancelled / markRejected ----------

    @Test
    fun `markCancelled sets status, records the refund and frees every date`() {
        val r = reservation()
        val resRef = reservationsCol.document("entry1")
        stubDocContents(resRef, r)
        var succeeded = false

        repo.markCancelled("entry1", onSuccess = { succeeded = true }, onFailure = { fail("unexpected: $it") })

        assertTrue(succeeded)
        verify { tx.update(resRef, mapOf("status" to "CANCELLED", "refundRecorded" to true)) }
        r.dates.forEach { verify { tx.delete(bookedCol.document("camp1_$it")) } }
    }

    @Test
    fun `markRejected sets status, records the refund and frees every date`() {
        val r = reservation()
        val resRef = reservationsCol.document("entry1")
        stubDocContents(resRef, r)
        var succeeded = false

        repo.markRejected("entry1", onSuccess = { succeeded = true }, onFailure = { fail("unexpected: $it") })

        assertTrue(succeeded)
        verify { tx.update(resRef, mapOf("status" to "REJECTED", "refundRecorded" to true)) }
        r.dates.forEach { verify { tx.delete(bookedCol.document("camp1_$it")) } }
    }

    @Test
    fun `close fails with NoSuchElementException when the reservation does not exist`() {
        stubDocContents(reservationsCol.document("missing"), null)
        var error: Exception? = null

        repo.markCancelled("missing", onSuccess = { fail("should not succeed") }, onFailure = { error = it })

        assertTrue(error is NoSuchElementException)
        verify(exactly = 0) { tx.delete(any<DocumentReference>()) }
    }

    @Test
    fun `close fails and changes nothing when the reservation is already cancelled`() {
        val resRef = reservationsCol.document("entry1")
        stubDocContents(resRef, reservation(status = ReservationStatus.CANCELLED))
        var error: Exception? = null

        repo.markRejected("entry1", onSuccess = { fail("should not succeed") }, onFailure = { error = it })

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("already CANCELLED"))
        verify(exactly = 0) { tx.update(any<DocumentReference>(), any<Map<String, Any>>()) }
        verify(exactly = 0) { tx.delete(any<DocumentReference>()) }
    }
}