package com.example.campbnb.database

import com.example.campbnb.models.User
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Single accessor for ALL Firestore Reading/Writing.
 *
 * All Data schemas are registered in this object.
 */
object Database {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

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

}