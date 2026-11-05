package com.example.campbnb.database

/**
 * Indicates the status of a [FirestoreTransactionViewModel].
 *
 * If the State is [Idle], no transaction is in progress.
 *
 * If the State is [Loading], a transaction has started but has not yet committed. Firestore may
 * internally retry a conflicting transaction; from the view's perspective any amount of retries
 * keeps the State [Loading] until the transaction commits or fails.
 *
 * If the State is [Success]ful, the latest transaction executed and committed.
 *
 * If the State is [Fault], the underlying Firebase engine could not complete the transaction.
 * The reason is provided as an [Exception].
 *
 * Sealed to prevent outside state modification.
 */
sealed class TransactionViewState: TransactionView() {
    /**
     * Indicates that no transaction has run yet.
     *
     * Singleton state with no associated data.
     */
    object Idle: TransactionViewState()

    /**
     * Indicates that a transaction is actively running and has not yet committed.
     *
     * Singleton state with no associated data.
     */
    object Loading: TransactionViewState()

    /**
     * Indicates that the latest transaction committed successfully.
     *
     * Singleton state with no associated data.
     */
    object Success: TransactionViewState()

    /**
     * Indicates an error occurred when attempting to execute the transaction.
     *
     * @param exception The specific error that occurred.
     */
    data class Fault(val exception: Exception): TransactionViewState()
}
