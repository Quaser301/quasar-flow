package com.example.campbnb.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


/**
 * Provides a concrete [ViewModel] for other `ViewModels` to use or extend.
 *
 * Status of running transactions is provided via [StateFlow], as specified by [TransactionView]
 * through its implementation [TransactionViewState].
 *
 * @param engine The transaction engine to use.
 * @property transactionViewState The state of the current transaction, as specified by
 * [TransactionViewState].
 */
open class FirestoreTransactionViewModel(
    private val engine: TransactionEngine
): ViewModel() {
    private val _transactionViewState: MutableStateFlow<TransactionViewState> = MutableStateFlow(
        TransactionViewState.Idle
    )
    val transactionViewState: StateFlow<TransactionViewState> = _transactionViewState

    /**
     * Runs the provided reads and writes as one atomic transaction through the engine.
     *
     * Applies relevant state updates as specified in [TransactionViewState].
     *
     * @param block The sequence of reads and writes to run in a single transaction. All reads
     * must be issued before any write.
     */
    fun runTransaction(block: TransactionEngine.TransactionChain.() -> Any) {
        viewModelScope.launch {
            _transactionViewState.value = TransactionViewState.Loading

            engine.commit(block) { result ->
                if (result.isSuccess) {
                    _transactionViewState.value = TransactionViewState.Success
                } else {
                    // guaranteed safe since result is not success.
                    val exception: Exception = result.exceptionOrNull()!! as Exception
                    _transactionViewState.value = TransactionViewState.Fault(exception)
                }
            }
        }
    }

    /**
     * Resets the current [TransactionViewState] back to Idle.
     */
    fun resetTransactionViewState() {
        _transactionViewState.value = TransactionViewState.Idle
    }
}
