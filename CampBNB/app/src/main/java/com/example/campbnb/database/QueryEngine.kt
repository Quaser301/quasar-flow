package com.example.campbnb.database


import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.reflect.KProperty1
import com.google.firebase.firestore.Query

// TODO: see if we need subcollections or not
// TODO: see if this needs to run as a coroutine.


/**
 * Type safe, generic Querying Engine for Firestore.
 *
 * # Example:
 * ```kotlin
 * data class User(val name: String, val age: Int, val admin: Boolean): Identifiable()
 *
 * val firestore = FirebaseFirestore.getInstance()
 * val userEngine = firestore.readEngineFor<User>("Users")
 * // Returns all users that are admins and older than 18 but younger than 36, ordered in ascending
 * // order according to their names.
 * userEngine.query()
 *    .whereEquals(User::admin, true)
 *    .whereGreaterThan(User::age, 18)
 *    .whereLessThan(User::age, 36)
 *    .orderBy(User::name)
 *    .execute(onSuccess = { ... }, onFailure = {...} )
 * ```
 *
 * @param db An instance of the Firestore database.
 * @param collectionName The collection to query from.
 * @param classInstance: An existent class instance for the runtime to build on.
 */
class QueryEngine<T: Identifiable> (
    // DB connection
    private val db: FirebaseFirestore,
    // which collection to target
    private val collectionName: String,
    // a living instance of the target class T.
    // needed for the runtime to work out how to convert what into where.
    private val classInstance: Class<T>
    ) {

    // Collection entry so that we can actually query the DB.
    private val collection: CollectionReference = db.collection(collectionName)

    /**
     * Starts a new Query.
     *
     * @return A new [QueryChain] class instance.
     */
    fun query(): QueryChain<T> {
        return QueryChain(collection, classInstance)
    }

    /**
     * Internal Class helper that enables advanced, type safe chaining of various conditions
     * for complex querying.
     *
     * @param query The query to build on: typically a [CollectionReference] instance.
     * @param classInstance An existent class instance for the runtime to build on.
     */
    class QueryChain<T:Any> (
        private val query: Query,
        private val classInstance: Class<T>
    ) {
        /**
         * Adds a Where Equals clause to the query, IE Variable = Value.
         *
         * @param property The Variable to query about.
         * @param value The value to query equality for.
         * @return The updated query.
         */
        fun whereEquals(property: KProperty1<T, *>, value: Any?): QueryChain<T> {
            return QueryChain(query.whereEqualTo(property.name, value), classInstance)
        }

        /**
         * Adds a Greater Than clause to the query, IE Variable > Value.
         *
         * @param property The Variable to query about.
         * @param value The value to query order for.
         * @return The updated query.
         */
        fun whereGreaterThan(property: KProperty1<T, *>, value: Any): QueryChain<T> {
            return QueryChain(query.whereGreaterThan(property.name, value), classInstance)
        }

        /**
         * Adds a Less Than clause to the query, IE Variable < Value.
         *
         * @param property The Variable to query about.
         * @param value The value to query order for.
         * @return The updated query.
         */
        fun whereLessThan(property: KProperty1<T, *>, value: Any): QueryChain<T> {
            return QueryChain(query.whereLessThan(property.name, value),classInstance)
        }

        /**
         * Instructs the query to order the return values in a certain way, based on the provided
         * `property`.
         *
         * @param property The Variable to query about.
         * @param direction The way to order the result. Defaults to Ascending Order.
         * @return The updated query.
         */
        fun orderBy(
            property: KProperty1<T, *>,
            direction: Query.Direction = Query.Direction.ASCENDING
        ): QueryChain<T> {
            return QueryChain(query.orderBy(property.name, direction), classInstance)
        }

        /**
         * Limits returned results to the first `limit` entries.
         *
         * @param limit How many entries to return.
         * @returns The updated query.
         */
        fun limitResults(limit: Long): QueryChain<T> {
            return QueryChain(query.limit(limit), classInstance)
        }

        /**
         * Executes the query.
         *
         * @param onSuccess The callback to pass the returned entries to.
         * @param onFailure The callback to pass any failure code to.
         */
        fun execute(onSuccess: (List<T>) -> Unit, onFailure: (Exception) -> Unit) {
            query.get()
                .addOnSuccessListener { onSuccess(it.toObjects(classInstance)) }
                .addOnFailureListener { onFailure(it) }
        }
    }
}

// inject this function directly into Firebase

/**
 * Inlined helper function to automate the passing of various values in the construction of a
 * [QueryEngine].
 *
 * @param collectionName The collection to query from.
 * @returns The constructed [QueryEngine] for the specific Schema T.
 */
inline fun <reified T: Identifiable> FirebaseFirestore.readEngineFor(collectionName: String): QueryEngine<T> {
    return QueryEngine(this, collectionName, T::class.java)
}