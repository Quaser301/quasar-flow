package com.example.campbnb.database


/**
 * Indicates the status of a [FirestoreQueryViewModel].
 *
 * If the State is [NoQuery], no query has yet run.
 *
 * If the State is [Loading] a query has started but not yet returned a value.
 *
 * If the State is [Success]ful, the latest query executed successfully and returned some value.
 * NOTE: This does **not** mean that the provided query has at least one relevant entry, just that
 * the query itself did not fail.
 *
 * If the State is [Fault], the underlying Firebase engine could not complete the query.
 * The reason is provided as an [Exception].
 *
 * Sealed to prevent outside state modification.
 *
 * @param T The schema returned by the underlying [FirestoreQueryViewModel].
 */
sealed class QueryState<out T> {
    /**
     * Indicates that no query has run yet.
     *
     * Singleton state with no associated data.
     */
    object NoQuery: QueryState<Nothing>()

    /**
     * Indicates that a query is actively running and has not yet completed.
     *
     * Singleton state with no associated data.
     */
    object Loading: QueryState<Nothing>()

    /**
     * Indicates that a query ran successfully, and returned some value. Does *not* necessarily imply
     * that the provided query had any relevant entries, only that the query encountered no `Faults`.
     *
     * @param data The `T` entries relevant to the provided Query.
     */
    data class Success<T>(val data: List<T>): QueryState<T>()

    /**
     * Indicates that the underlying FireStore Engine encountered an error and could not execute the
     * query.
     *
     * @param exception The reason the query could not be completed.
     */
    data class Fault(val exception: Exception): QueryState<Nothing>()
}