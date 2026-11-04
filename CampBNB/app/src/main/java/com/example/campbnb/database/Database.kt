package com.example.campbnb.database

import com.example.campbnb.models.User
import com.example.campbnb.database.transactionEngine
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Single accessor for ALL Firestore Reading/Writing.
 *
 * All Data schemas are registered in this object.
 *
 * The Firestore instance is resolved once, on first use, and shared by every engine this
 * object hands out.
 */
object Database {
    // Resolved once rather than on each property access, so every engine built from `db`
    // below holds and shares a single Firestore instance. (`const val` is not an option
    // here: it is restricted to primitive and String types.)
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    /*
    Insert your DB schema's here, following this format:
    object YourSchemaPluralized {
        const val COLLECTION_NAME = "path/to/collection"

        fun readEngine(): QueryEngine<User> {
            return db.readEngineFor<User>(COLLECTION_NAME)
        }

        fun writeEngine(): WriteEngine<User> {
            return db.writeEngineFor<User>(COLLECTION_NAME)
        }
    }

    MAKE SURE YOUR CLASSES INHERIT `Identifiable`
    */

    /**
     * The Users Collection. All [User] schemas are stored here.
     */
    object Users {
        const val COLLECTION_NAME = "users"

        fun readEngine(): QueryEngine<User> {
            return db.readEngineFor<User>(COLLECTION_NAME)
        }

        fun writeEngine(): WriteEngine<User> {
            return db.writeEngineFor<User>(COLLECTION_NAME)
        }
    }

    /**
     * The transaction engine for this Firestore instance.
     *
     * Unlike the read and write engines, no transaction engine is registered per schema: a
     * transaction spans whatever collections it needs to hold its invariants, so one engine
     * serves every collection registered here.
     *
     * @returns The constructed [TransactionEngine] for this Firestore instance.
     */
    fun transactionEngine(): TransactionEngine {
        return db.transactionEngine()
    }

}