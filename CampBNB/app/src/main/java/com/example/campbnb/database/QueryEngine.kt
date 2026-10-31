package com.example.campbnb.database


// For compile-time query checks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.reflect.KProperty1
import com.google.firebase.firestore.Query

// TODO: see if we need subcollections or not
// TODO: see if this needs to run as a coroutine.

private fun<T> keyOf(property: KProperty1<T, *>): String {
    return property.name
}

class QueryEngine<T: Any> (
    private val db: FirebaseFirestore,
    private val collectionName: String,
    private val classInstance: Class<T>
    ) {
    private val collection: CollectionReference = db.collection(collectionName)

    fun query(): QueryChain<T> {
        return QueryChain(collection, classInstance)
    }

    class QueryChain<T:Any> (
        private val query: Query,
        private val classInstance: Class<T>
    ) {
        fun whereEquals(property: KProperty1<T, *>, value: Any?): QueryChain<T> {
            return QueryChain(query.whereEqualTo(property.name, value), classInstance)
        }

        fun whereGreaterThan(property: KProperty1<T, *>, value: Any): QueryChain<T> {
            return QueryChain(query.whereGreaterThan(property.name, value), classInstance)
        }

        fun whereLessThan(property: KProperty1<T, *>, value: Any): QueryChain<T> {
            return QueryChain(query.whereLessThan(property.name, value),classInstance)
        }

        fun orderBy(
            property: KProperty1<T, *>,
            direction: Query.Direction = Query.Direction.ASCENDING
        ): QueryChain<T> {
            return QueryChain(query.orderBy(property.name, direction), classInstance)
        }

        fun limitResults(limit: Long): QueryChain<T> {
            return QueryChain(query.limit(limit), classInstance)
        }
    }
}

inline fun <reified T: Any> FirebaseFirestore.engineFor(collectionName: String): QueryEngine<T> {
    return QueryEngine(this, collectionName, T::class.java)
}