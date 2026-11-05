package com.example.campbnb.database


/**
 * Abstract base for viewing the lifecycle of a Firestore transaction from start to finish.
 *
 * Status of running transactions is provided via [StateFlow], typed against this base by way
 * of its implementation [TransactionViewState], so that other ViewModels may latch on to the
 * view contract rather than to one specific implementation.
 */
abstract class TransactionView {
}
