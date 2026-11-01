package com.example.campbnb.database

import com.google.firebase.firestore.FirebaseFirestore


/**
 * Type safe, generic Writing Engine for Firestore.
 *
 * # Example:
 * ```kotlin
 * data class User(val name: String, val age: Int, val admin: Boolean): Identifiable()
 *
 * val firestore = FirebaseFirestore.getInstance()
 * val testUser = User("Tester", 42, True)
 * val userEngine = firestore.writeEngineFor<User>("Users")
 * userEngine.save(testUser) // test user saved to DB!
 * ```
 *
 * @param db An instance of the Firestore database.
 * @param collectionName The collection to query from.
 */
class WriteEngine<T: Identifiable> (
    private val db: FirebaseFirestore,
    private val collectionName:String
) {
    private val collection = db.collection(collectionName)

    /**
     * Saves a given T entry to the database.
     *
     * @param item The item to save to the DB. This item is [Identifiable].
     * @param onComplete The callback used when the transaction is complete. Passed parameter is
     * a [Result]`.success` null value if the deletion was successful, or a `Result.failure` value
     * with the [Exception] otherwise.
     */
    fun save(item: T, onComplete: (Result<Exception?>) -> Unit) {
        collection.document(item.id).set(item)
            .addOnSuccessListener { onComplete(Result.success(null)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    /**
     * Deletes an Item from the Firestore collection, given its [Identifiable] ID.
     *
     * @param onComplete The callback used when the transaction is complete. Passed parameter is
     * a [Result]`.success` null value if the deletion was successful, or a `Result.failure` value
     * with the [Exception] otherwise.
     */
    fun delete(id:String, onComplete: (Result<Exception?>) -> Unit) {
        collection.document(id).delete()
            .addOnSuccessListener { onComplete(Result.success(null)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    /**
     * Updates an Item from the Firestore collection, given its [Identifiable] ID and the FieldMap
     * to use. *Only fields in the field map* will be updated.
     *
     * @param onComplete The callback used when the transaction is complete. Passed parameter is
     * a [Result]`.success` null value if the deletion was successful, or a `Result.failure` value
     * with the [Exception] otherwise.
     */
    fun update(id:String, fieldMap:Map<String, Any?>, onComplete: (Result<Exception?>) -> Unit) {
        collection.document(id).update(fieldMap)
            .addOnSuccessListener { onComplete(Result.success(null)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }
}

// inject this function directly into Firebase

/**
 * Inlined helper function to automate the passing of various values in the construction of a
 * [WriteEngine].
 *
 * @param collectionName The collection to query from.
 * @returns The constructed [WriteEngine] for the specific Schema T.
 */
inline fun <reified T: Identifiable> FirebaseFirestore.writeEngineFor(collectionName:String): WriteEngine<T> {
    return WriteEngine(this, collectionName)
}