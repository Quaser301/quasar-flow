package com.example.campbnb.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch




// Developer Note: we inherit from ViewModel to make sure our state is saved
// if any sort of window resizing or other such event happens, since Jetpack
// Compose will otherwise dispose of values and re-render.

// likewise we use Stateflow to provide a readonly, coroutine ready entry for
// superviews to latch on to

/**
 * Provides a concrete [ViewModel] for other `ViewModels` to use or extend.
 *
 * Status of ran queries is provided via [StateFlow].
 *
 * @param engine The query engine to use, specific to the generic `T`.
 * @property queryState The state of the current query, as specified by [QueryState].
 * @param T The data classes queried by this class. This class will only return instances of `T`.
 */
open class FirestoreQueryViewModel<T: Identifiable>(
    private val engine: QueryEngine<T>
): ViewModel() {
    private val _queryState: MutableStateFlow<QueryState<T>> = MutableStateFlow(
        QueryState.NoQuery
    )


    val queryState: StateFlow<QueryState<T>> = _queryState

    /**
     * Fetches all values from the database that match the schema of `T`.
     */
    fun fetchAll() {
        executeQuery {
            this // return the base query
        }
    }

    // allow any superview to dictate exactly what they want.
    // note on the syntax: QueryChain<T>.() means that the block STARTS inside the base
    // query provided by the caller.
    /**
     * Executes a provided Query.
     *
     * Executes a database query provided via a block element, updating the UI StateFlow as the
     * query progresses. Automatically disposes of the query and any other associated data if the
     * view is destroyed via [viewModelScope].
     *
     * @param block A query or query chain to run.
     *
     */
    fun executeQuery(block: QueryEngine.QueryChain<T>.() -> QueryEngine.QueryChain<T>) {
        // use ViewModelScope so that the running query is automagically disposed of
        // if the view is closed before resolving.
        viewModelScope.launch {
            _queryState.value = QueryState.Loading

            val fullQuery = engine.query().block()

            fullQuery.execute(
                onSuccess = { items -> _queryState.value = QueryState.Success(items) },
                onFailure = { fault -> _queryState.value = QueryState.Fault(fault) }
            )
        }
    }
}