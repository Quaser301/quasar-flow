package com.example.campbnb

/**
 * NOTICE: This file was AI-assisted. The verbatim prompt for this work is recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 *
 * The three tests follow the request made in that prompt: the simplest correct Firestore
 * transaction, a write-before-read transaction which must fail correctly, and a large
 * transaction which must only fail if Firestore itself fails. An added case covers a
 * commit block with no operations at all, which must complete like any other.
 */

import com.example.campbnb.database.TransactionEngine
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransactionEngineTest {

    private lateinit var mockDb: FirebaseFirestore
    private lateinit var mockTransaction: Transaction
    // The facade's runTransaction hands back a Task of the top value type, not Task<Void>:
    // the Java type argument is erased to java-object in the facade, so the mock must be
    // declared at that same Task type or the stubbing rejects the value.
    private lateinit var mockTask: Task<Any>
    private lateinit var snapshotStub: DocumentSnapshot
    private lateinit var transactionEngine: TransactionEngine

    @Before
    fun setup() {
        mockDb = mockk(relaxed = true)
        mockTransaction = mockk(relaxed = true)
        mockTask = mockk(relaxed = true)

        // 🌟 Wire up the full hierarchical mock routing chain: the engine asks the db to run
        // a transaction, and the mocked db hands our block a mocked Transaction instead of a
        // real one.
        // The SDK's facade types cannot have values synthesized by the relaxed machinery:
        // it cannot build an instance of a sealed class, so an unstubbed transaction call
        // throws right where the block consumes its result. Every value-producing call the
        // block runs must therefore be stubbed with values we control. The stubbed read
        // reports an absent document, so the simplest correct transaction's check passes.
        // These stubs come first on purpose: a block runs when its test drives the
        // captured callback, so the inner value calls must already answer by then.
        snapshotStub = mockk<DocumentSnapshot>()
        every { snapshotStub.exists() } returns false
        every { mockTransaction.get(any()) } returns snapshotStub
        every { mockTransaction.set(any(), any()) } returns mockTransaction

        // The callback of runTransaction cannot be given to a matcher here: the SDK erases
        // that parameter to a facade function type, and every callback the engine passes
        // arrives behind a compiler bridge no matcher signature can be keyed to. Each
        // test therefore captures the engine's own callback with its own slot and drives
        // it below with the mocked Transaction - the step the real SDK performs between
        // opening a transaction and invoking its listeners.

        // Every database structural mutation returns our tracking task reference
        every { mockTask.addOnSuccessListener(any()) } returns mockTask
        every { mockTask.addOnFailureListener(any()) } returns mockTask

        // Instantiate your exact implementation wrapper
        transactionEngine = TransactionEngine(mockDb)
    }

    @Test
    fun test_Commit_ReadsBeforeWritesTriggersSuccessCallback() {
        val reservationRef = mockk<DocumentReference>()
        val bookedDateRef = mockk<DocumentReference>()
        val successSlot = slot<OnSuccessListener<Any>>()
        val callbackSlot = slot<com.google.firebase.firestore.Transaction.Function<Any>>()

        every { mockTask.addOnSuccessListener(capture(successSlot)) } returns mockTask
        every { mockDb.runTransaction(capture(callbackSlot)) } returns mockTask

        var testResult: Result<Exception?>? = null

        // Act: the simplest correct transaction, one read followed by one write
        transactionEngine.commit({
            val existing = read(reservationRef)
            check(!existing.exists()) { "Entry already has a reservation" }
            save(bookedDateRef, mapOf("date" to "2026-11-04"))
        }) { result ->
            testResult = result
        }

        // Run the captured engine callback with the mocked Transaction, the same step
        // the SDK inserts between opening a transaction and invoking its listeners
        callbackSlot.captured.apply(mockTransaction)

        // Simulate a successful remote Firestore transaction completion
        successSlot.captured.onSuccess(null)

        // Assert
        verify(exactly = 1) { mockTransaction.get(reservationRef) }
        verify(exactly = 1) { mockTransaction.set(bookedDateRef, mapOf("date" to "2026-11-04")) }

        assertTrue(testResult!!.isSuccess)
    }

    @Test
    fun test_Commit_WriteBeforeReadFailsCorrectly() {
        val reservationRef = mockk<DocumentReference>()
        val bookedDateRef = mockk<DocumentReference>()
        val callbackSlot = slot<com.google.firebase.firestore.Transaction.Function<Any>>()

        every { mockDb.runTransaction(capture(callbackSlot)) } returns mockTask

        var testResult: Result<Exception?>? = null
        var transactionFault: Throwable? = null

        // Act: the write happens first, the read after it must fail the transaction
        try {
            transactionEngine.commit({
                save(reservationRef, mapOf("reserved" to true))
                read(bookedDateRef)
            }) { result ->
                if (result.isFailure) {
                    transactionFault = result.exceptionOrNull()
                } else {
                    testResult = result
                }
            }
            // The engine caught the mistake at the point the read was attempted, while
            // running its callback against the transaction
            callbackSlot.captured.apply(mockTransaction)
        } catch (e: Throwable) {
            transactionFault = e
        }

        // Assert
        assertTrue(transactionFault != null)
        assertTrue(testResult == null)

        // The engine caught the mistake at the point it was made: the write happened, the
        // read never reached the transaction, and nothing committed.
        verify(exactly = 1) { mockTransaction.set(reservationRef, mapOf("reserved" to true)) }
        verify(exactly = 0) { mockTransaction.get(any()) }
    }

    @Test
    fun test_Commit_LargeTransactionOnlyFailsIfFirestoreFails() {
        val successSlot = slot<OnSuccessListener<Any>>()
        val callbackSlot = slot<com.google.firebase.firestore.Transaction.Function<Any>>()

        every { mockTask.addOnSuccessListener(capture(successSlot)) } returns mockTask
        every { mockDb.runTransaction(capture(callbackSlot)) } returns mockTask

        var testResult: Result<Exception?>? = null

        // Act: a transaction big enough that only the underlying engine could reasonably fail
        // it; reads all come first, writes all come after.
        val dateRefs = (1..100).map { mockk<DocumentReference>(relaxed = true) }

        transactionEngine.commit({
            dateRefs.forEach { read(it) }
            dateRefs.forEach { save(it, mapOf("campsiteId" to "campsite_0")) }
        }) { result ->
            testResult = result
        }

        // Run the captured engine callback with the mocked Transaction, the same step
        // the SDK inserts between opening a transaction and invoking its listeners
        callbackSlot.captured.apply(mockTransaction)

        // Simulate a successful remote Firestore transaction completion
        successSlot.captured.onSuccess(null)

        // Assert
        verify(exactly = 100) { mockTransaction.get(any()) }
        verify(exactly = 100) { mockTransaction.set(any(), mapOf("campsiteId" to "campsite_0")) }

        assertTrue(testResult!!.isSuccess)
    }

    // Adversarial addition: a commit block with no operations at all
    @Test
    fun test_CommitWithNoOperations_StillCompletesLikeAnyOther() {
        val successSlot = slot<OnSuccessListener<Any>>()
        val callbackSlot = slot<com.google.firebase.firestore.Transaction.Function<Any>>()

        every { mockTask.addOnSuccessListener(capture(successSlot)) } returns mockTask
        every { mockDb.runTransaction(capture(callbackSlot)) } returns mockTask

        var testResult: Result<Exception?>? = null

        transactionEngine.commit({}) { result ->
            testResult = result
        }

        callbackSlot.captured.apply(mockTransaction)
        successSlot.captured.onSuccess(null)

        assertTrue(testResult!!.isSuccess)
        verify(exactly = 0) { mockTransaction.get(any()) }
        verify(exactly = 0) { mockTransaction.set(any(), any()) }
    }
}
