package com.example.campbnb.database

import com.example.campbnb.models.Lottery
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Rating
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

    object Ratings {
        const val COLLECTION_NAME = "ratings"

        fun readEngine(): QueryEngine<Rating> {
            return db.readEngineFor<Rating>(COLLECTION_NAME)
        }

        fun writeEngine(): WriteEngine<Rating> {
            return db.writeEngineFor<Rating>(COLLECTION_NAME)
        }
    }

    object Lotteries {
        const val COLLECTION_NAME = "lotteries"

        fun readEngine(): QueryEngine<Lottery> {
            return db.readEngineFor<Lottery>(COLLECTION_NAME)
        }

        fun writeEngine(): WriteEngine<Lottery> {
            return db.writeEngineFor<Lottery>(COLLECTION_NAME)
        }
    }


    object Campsites {
        const val COLLECTION_NAME = "campsites"

        fun readEngine(): QueryEngine<Campsite> {
            return db.readEngineFor<Campsite>(COLLECTION_NAME)
        }

        fun writeEngine(): WriteEngine<Campsite> {
            return db.writeEngineFor<Campsite>(COLLECTION_NAME)
        }
    }
}