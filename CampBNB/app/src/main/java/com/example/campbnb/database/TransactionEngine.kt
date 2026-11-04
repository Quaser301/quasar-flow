package com.example.campbnb.database

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentResult
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction


/**
 * Type safe, generic Transaction Engine for Firestore.
 *
 * Unlike the other engines, this engine is not typed to a single schema: a transaction spans
 * however many collections it needs to hold its invariants (a reservation and its booked dates,
 * for example), so it operates on [DocumentReference]s rather than on a schema `T`.
 *
 * A transaction may not commit on its own: all reads must be issued before any write, and if
 * any part of the transaction fails, none of it is written.
 *
 * # Example:
 * ```kotlin
 * val firestore = FirebaseFirestore.getInstance()
 * val transactionEngine = firestore.transactionEngine()
 * transactionEngine.commit({
 *     val existing = read(reservationRef)
 *     check(!existing.exists()) { "Entry already has a reservation" }
 *     dateRefs.forEach { check(!read(it).exists()) { "One or more dates are already booked" } }
 *     save(reservationRef, reservation)
 *     dateRefs.forEach { save(it, mapOf("campsiteId" to reservation.campsiteId)) }
 * }) { result -> ... }
 * ```
 *
 * @param db An instance of the Firestore database.
 */
class TransactionEngine (
    private val db: FirebaseFirestore
) {

    /**
     * Runs the provided reads and writes as one atomic Firestore transaction.
     *
     * @param block The sequence of reads and writes to run inside a single transaction. All
     * reads must be issued before any write; a read issued after a write fails the transaction
     * at the point of the mistake, before the read itself happens.
     * @param onComplete The callback used when the transaction is complete. Passed parameter is
     * a [Result]`.success` null value if the transaction committed, or a `Result.failure` value
     * with the [Exception] otherwise.
     */
    fun commit(block: TransactionEngine.TransactionChain.() -> Any, onComplete: (Result<Exception?>) -> Unit) {
        db.runTransaction { tx ->
            TransactionChain(tx).block()
        }
            .addOnSuccessListener { onComplete(Result.success(null)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    /**
     * Internal class helper giving a type safe face to a live Firestore [Transaction].
     *
     * The read-before-write rule is enforced here rather than left to the engine: failing at
     * the point the mistake is made keeps a misordered transaction from doing partial work past
     * the mistake.
     *
     * @param tx The live transaction provided by the engine at commit time.
     */
    class TransactionChain (
        private val tx: Transaction
    ) {
        // Marks the point past which reads are no longer allowed.
        private var writesStarted: Boolean = false

        /**
         * Reads a given document inside this transaction.
         *
         * @param document The document to read.
         * @return The read result. Can be checked with `exists()` or converted with `toObject`.
         */
        fun read(document: DocumentReference): DocumentResult {
            check(!writesStarted) {
                "Reads must be issued before writes inside a transaction."
            }
            return tx.get(document)
        }

        /**
         * Writes a given document to this transaction.
         *
         * @param document The document to write.
         * @param item The value to store, either a data class instance or a String/Value map.
         */
        fun save(document: DocumentReference, item: Any?) {
            writesStarted = true
            tx.set(document, item)
        }

        /**
         * Updates only the given fields of a document in this transaction.
         *
         * @param document The document to update.
         * @param fieldMap The String/Value map of which named fields should be changed, and what
         * values they should be changed to.
         */
        fun update(document: DocumentReference, fieldMap: Map<String, Any?>) {
            writesStarted = true
            tx.update(document, fieldMap)
        }

        /**
         * Deletes a given document in this transaction.
         *
         * @param document The document to delete.
         */
        fun delete(document: DocumentReference) {
            writesStarted = true
            tx.delete(document)
        }
    }
}

// inject this function directly into Firebase

/**
 * Inlined helper function to automate the construction of a [TransactionEngine].
 *
 * @returns The constructed [TransactionEngine] for this Firestore instance.
 */
fun FirebaseFirestore.transactionEngine(): TransactionEngine {
    return TransactionEngine(this)
}
