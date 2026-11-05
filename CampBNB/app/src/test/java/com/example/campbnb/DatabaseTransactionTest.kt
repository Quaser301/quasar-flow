package com.example.campbnb

/**
 * NOTICE: This file was AI-assisted. The verbatim prompt for this work is recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 *
 * The three tests follow the request made in that prompt: the simplest correct Firestore
 * transaction, a write-before-read transaction which must fail correctly, and a large
 * transaction which must only fail if Firestore itself fails.
 */

import com.example.campbnb.database.TransactionEngine
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
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
    private lateinit var mockTask: Task<Void>
    private lateinit var transactionEngine: TransactionEngine

    @Before
    fun setup() {
        mockDb = mockk(relaxed = true)
        mockTransaction = mockk(relaxed = true)
        mockTask = mockk(relaxed = true)

        // 🌟 Wire up the full hierarchical mock routing chain: the engine asks the db to run
        // a transaction, and the mocked db hands our block a mocked Transaction instead of a
        // real one.
        // The block shape must be stated here: Kotlin cannot reconstruct the function
        // signature from the JVM-typed callback parameter of runTransaction, so the
        // reified type of the invoke matcher names it as the engine's own block type.
        every { mockDb.runTransaction(invoke<(Transaction) -> Any>(mockTransaction)) } returns mockTask

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
        val successSlot = slot<OnSuccessListener<Void>>()

        every { mockTask.addOnSuccessListener(capture(successSlot)) } returns mockTask

        var testResult: Result<Exception?>? = null

        // Act: the simplest correct transaction, one read followed by one write
        transactionEngine.commit({
            val existing = read(reservationRef)
            check(!existing.exists()) { "Entry already has a reservation" }
            save(bookedDateRef, mapOf("date" to "2026-11-04"))
        }) { result ->
            testResult = result
        }

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
        } catch (e: Throwable) {
            // Firestore fails the commit when the block itself fails; through the mock the
            // failure surfaces at the call site instead of through the failure listener.
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
        val successSlot = slot<OnSuccessListener<Void>>()

        every { mockTask.addOnSuccessListener(capture(successSlot)) } returns mockTask

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

        // Simulate a successful remote Firestore transaction completion
        successSlot.captured.onSuccess(null)

        // Assert
        verify(exactly = 100) { mockTransaction.get(any()) }
        verify(exactly = 100) { mockTransaction.set(any(), mapOf("campsiteId" to "campsite_0")) }

        assertTrue(testResult!!.isSuccess)
    }
}
