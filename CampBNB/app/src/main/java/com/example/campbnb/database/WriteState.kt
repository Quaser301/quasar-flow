package com.example.campbnb.database

/**
 * Indicates the status of a [FirestoreWriteViewModel].
 *
 * If the State is [Idle], no write operations are in progress.
 *
 * If the State is [Loading] a write operation has started but not yet returned a value.
 *
 * If the State is [Success]ful, the latest write operations executed successfully.
 *
 * If the State is [Fault], the underlying Firebase engine could not complete the write operations.
 * The reason is provided as an [Exception].
 *
 * Sealed to prevent outside state modification.
 */
sealed class WriteState {
    /**
     * Indicates no write operations are active.
     *
     * Singleton state with no associated data.
     */
    object Idle: WriteState()

    /**
     * Indicates write operations are currently active but not yet resolved.
     *
     * Singleton state with no associated data.
     */
    object Loading: WriteState()

    /**
     * Indicates that write operations have successfully executed.
     *
     * Singleton state with no associated data.
     */
    object Success: WriteState()

    /**
     * Indicates an error occurred when attempting to execute a write operation.
     *
     * @param exception The specific error that occurred.
     */
    data class Fault(val exception: Exception): WriteState()
}