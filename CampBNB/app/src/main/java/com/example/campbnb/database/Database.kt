package com.example.campbnb.database


import com.example.campbnb.models.Notification
import com.example.campbnb.models.Message
import android.annotation.SuppressLint
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Rating
import com.example.campbnb.models.User
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.BookedDate
import com.example.campbnb.models.WaitingListEntry
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
    // since this val is only used by functions that are unit tested, its fine
    // we don't make it hard to test.
    @SuppressLint("StaticFieldLeak") // since this is singleton, its invoked once
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    /*
    Insert your DB schema's here, following this format:
    object YourSchemaPluralized {
        const val COLLECTION_NAME = "path/to/collection"

        /** Opens a query engine over the users collection. */
        fun readEngine(): QueryEngine<User> {
            return db.readEngineFor<User>(COLLECTION_NAME)
        }

        /** Returns a write engine for the users collection. */
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

        /** Opens a query engine over the users collection. */
        fun readEngine(): QueryEngine<User> {
            return db.readEngineFor<User>(COLLECTION_NAME)
        }

        /** Returns a write engine for the users collection. */
        fun writeEngine(): WriteEngine<User> {
            return db.writeEngineFor<User>(COLLECTION_NAME)
        }
    }

    /**
     * The Notification Collection. All [Notification] records are stored here.
     */
    object Notifications {
        const val COLLECTION_NAME = "notifications"

        /** Opens a query engine over the notifications collection. */
        fun readEngine(): QueryEngine<Notification> {
            return db.readEngineFor<Notification>(COLLECTION_NAME)
        }

        /** Returns a write engine for the notifications collection. */
        fun writeEngine(): WriteEngine<Notification> {
            return db.writeEngineFor<Notification>(COLLECTION_NAME)
        }
    }

     /** The transaction engine for this Firestore instance.
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

    object Ratings {
        const val COLLECTION_NAME = "ratings"

        /** Opens a query engine over the ratings collection. */
        fun readEngine(): QueryEngine<Rating> {
            return db.readEngineFor<Rating>(COLLECTION_NAME)
        }

        /** Returns a write engine for the ratings collection. */
        fun writeEngine(): WriteEngine<Rating> {
            return db.writeEngineFor<Rating>(COLLECTION_NAME)
        }
    }

    object Lotteries {
        const val COLLECTION_NAME = "lotteries"

        /** Opens a query engine over the lotteries collection. */
        fun readEngine(): QueryEngine<Lottery> {
            return db.readEngineFor<Lottery>(COLLECTION_NAME)
        }

        /** Returns a write engine for the lotteries collection. */
        fun writeEngine(): WriteEngine<Lottery> {
            return db.writeEngineFor<Lottery>(COLLECTION_NAME)
        }
    }
    
    object Reservations {
        const val COLLECTION_NAME = "reservations"

        /** Opens a query engine over the reservations collection. */
        fun readEngine(): QueryEngine<ReservationModel> {
            return db.readEngineFor<ReservationModel>(COLLECTION_NAME)
        }

        /** Returns a write engine for the reservations collection. */
        fun writeEngine(): WriteEngine<ReservationModel> {
            return db.writeEngineFor<ReservationModel>(COLLECTION_NAME)
        }
    }

    object BookedDates {
        const val COLLECTION_NAME = "bookedDates"

        /** Returns a write engine for the booked dates collection. */
        fun writeEngine(): WriteEngine<BookedDate> {
            return db.writeEngineFor<BookedDate>(COLLECTION_NAME)
        }
    }

    object Campsites {
        const val COLLECTION_NAME = "campsites"

        /** Opens a query engine over the campsites collection. */
        fun readEngine(): QueryEngine<Campsite> {
            return db.readEngineFor<Campsite>(COLLECTION_NAME)
        }

        /** Returns a write engine for the campsites collection. */
        fun writeEngine(): WriteEngine<Campsite> {
            return db.writeEngineFor<Campsite>(COLLECTION_NAME)
        }
    }

    /**
     * The Waiting List Collection. All [WaitingListEntry] records are stored here.
     */
    object WaitingListEntries {
        const val COLLECTION_NAME = "waitingList"

        /** Opens a query engine over the waiting list collection. */
        fun readEngine(): QueryEngine<WaitingListEntry> {
            return db.readEngineFor<WaitingListEntry>(COLLECTION_NAME)
        }

        /** Returns a write engine for the waiting list collection. */
        fun writeEngine(): WriteEngine<WaitingListEntry> {
            return db.writeEngineFor<WaitingListEntry>(COLLECTION_NAME)
        }
    }

    /**
     * The Messages collection. One document per chat message; all messages of one reservation
     * share the same reservationId (that is the conversation).
     */
    object Messages {
        const val COLLECTION_NAME = "messages"

        /** Opens a query engine over the messages collection. */
        fun readEngine(): QueryEngine<Message> {
            return db.readEngineFor<Message>(COLLECTION_NAME)
        }

        /** Returns a write engine for the messages collection. */
        fun writeEngine(): WriteEngine<Message> {
            return db.writeEngineFor<Message>(COLLECTION_NAME)
        }
    }
}