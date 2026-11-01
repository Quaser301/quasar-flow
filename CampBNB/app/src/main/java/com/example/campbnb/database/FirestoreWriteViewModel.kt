package com.example.campbnb.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


/**
 * Provides a concrete [ViewModel] for other `ViewModels` to use or extend.
 *
 * Status of write operations is provided via [StateFlow].
 *
 * @param engine The query engine to use, specific to the generic `T`.
 * @property writeState The state of the current write, as specified by [WriteState].
 * @param T The data classes written by this class. This class will only write instances of `T`.
 */
open class FirestoreWriteViewModel<T: Identifiable>(
    private val engine: WriteEngine<T>
): ViewModel() {
    private val _writeState: MutableStateFlow<WriteState> = MutableStateFlow(
        WriteState.Idle
    )
    val writeState: StateFlow<WriteState> = _writeState

    /**
     * CREATE a given Item in the Database.
     *
     * Applies relevant state updates as specified in [WriteState].
     *
     * @param item The item to upload to the database.
     */
    fun create(item:T) {
        viewModelScope.launch {
            _writeState.value = WriteState.Loading

            engine.save(item) { result ->
                if (result.isSuccess) {
                    _writeState.value = WriteState.Success
                } else {
                    // guaranteed safe since result is not success.
                    val exception: Exception = result.exceptionOrNull()!! as Exception
                    _writeState.value = WriteState.Fault(exception)
                }
            }
        }
    }

    /**
     * UPDATE a given Item in the Database.
     *
     * Applies relevant state updates as specified in [WriteState].
     *
     * @param item The item to update in the database.
     * @param updatedFields The String/Value map of which named fields should be changed, and what
     * values they should be changed to.
     */
    fun update(item:T, updatedFields: Map<String, Any?>) {
        viewModelScope.launch {
            _writeState.value = WriteState.Loading

            engine.update(item.id, updatedFields) { result ->
                if (result.isSuccess) {
                    _writeState.value = WriteState.Success
                } else {
                    // guaranteed safe since result is not success.
                    val exception: Exception = result.exceptionOrNull()!! as Exception
                    _writeState.value = WriteState.Fault(exception)
                }
            }
        }
    }

    /**
     * DELETE a given Item in the Database.
     *
     * Applies relevant state updates as specified in [WriteState].
     *
     * @param item The item to delete from the database.
     */
    fun delete(item:T) {
        viewModelScope.launch {
            _writeState.value = WriteState.Loading

            engine.delete(item.id) { result ->
                if (result.isSuccess) {
                    _writeState.value = WriteState.Success
                } else {
                    // guaranteed safe since result is not success.
                    val exception: Exception = result.exceptionOrNull()!! as Exception
                    _writeState.value = WriteState.Fault(exception)
                }
            }
        }
    }

    /**
     * Resets the current [WriteState] back to Idle.
     */
    fun resetWriteState() {
        _writeState.value = WriteState.Idle
    }
}