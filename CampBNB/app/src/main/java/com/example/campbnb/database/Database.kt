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
import com.example.campbnb.models.NotificationPreferenceModel
import com.example.campbnb.models.AppSettingsModel
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

        // One engine per collection, built the first time it is asked for and kept: an
        // engine holds only the shared Firestore instance, the collection name and the
        // schema class, so handing out a fresh one per call rebuilt the same object on
        // every frame that read the collection (the profile screen and the notice paths
        // read it per draw).
        private val readEngineCached: QueryEngine<User> by lazy { db.readEngineFor<User>(COLLECTION_NAME) }

        fun readEngine(): QueryEngine<User> = readEngineCached

        // Kept for the same reason as the read engine above.
        private val writeEngineCached: WriteEngine<User> by lazy { db.writeEngineFor<User>(COLLECTION_NAME) }

        fun writeEngine(): WriteEngine<User> = writeEngineCached
    }

    /**
     * The Notification Collection. All [Notification] records are stored here.
     */
    object Notifications {
        const val COLLECTION_NAME = "notifications"

        // One engine per collection, built the first time it is asked for and kept: an
        // engine holds only the shared Firestore instance, the collection name and the
        // schema class, so handing out a fresh one per call rebuilt the same object on
        // every frame that read the collection (the profile screen and the notice paths
        // read it per draw).
        private val readEngineCached: QueryEngine<Notification> by lazy { db.readEngineFor<Notification>(COLLECTION_NAME) }

        fun readEngine(): QueryEngine<Notification> = readEngineCached

        // Kept for the same reason as the read engine above.
        private val writeEngineCached: WriteEngine<Notification> by lazy { db.writeEngineFor<Notification>(COLLECTION_NAME) }

        fun writeEngine(): WriteEngine<Notification> = writeEngineCached
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

        // One engine per collection, built the first time it is asked for and kept: an
        // engine holds only the shared Firestore instance, the collection name and the
        // schema class, so handing out a fresh one per call rebuilt the same object on
        // every frame that read the collection (the profile screen and the notice paths
        // read it per draw).
        private val readEngineCached: QueryEngine<Rating> by lazy { db.readEngineFor<Rating>(COLLECTION_NAME) }

        fun readEngine(): QueryEngine<Rating> = readEngineCached

        // Kept for the same reason as the read engine above.
        private val writeEngineCached: WriteEngine<Rating> by lazy { db.writeEngineFor<Rating>(COLLECTION_NAME) }

        fun writeEngine(): WriteEngine<Rating> = writeEngineCached
    }

    object Lotteries {
        const val COLLECTION_NAME = "lotteries"

        // One engine per collection, built the first time it is asked for and kept: an
        // engine holds only the shared Firestore instance, the collection name and the
        // schema class, so handing out a fresh one per call rebuilt the same object on
        // every frame that read the collection (the profile screen and the notice paths
        // read it per draw).
        private val readEngineCached: QueryEngine<Lottery> by lazy { db.readEngineFor<Lottery>(COLLECTION_NAME) }

        fun readEngine(): QueryEngine<Lottery> = readEngineCached

        // Kept for the same reason as the read engine above.
        private val writeEngineCached: WriteEngine<Lottery> by lazy { db.writeEngineFor<Lottery>(COLLECTION_NAME) }

        fun writeEngine(): WriteEngine<Lottery> = writeEngineCached
    }
    
    object Reservations {
        const val COLLECTION_NAME = "reservations"

        // One engine per collection, built the first time it is asked for and kept: an
        // engine holds only the shared Firestore instance, the collection name and the
        // schema class, so handing out a fresh one per call rebuilt the same object on
        // every frame that read the collection (the profile screen and the notice paths
        // read it per draw).
        private val readEngineCached: QueryEngine<ReservationModel> by lazy { db.readEngineFor<ReservationModel>(COLLECTION_NAME) }

        fun readEngine(): QueryEngine<ReservationModel> = readEngineCached

        // Kept for the same reason as the read engine above.
        private val writeEngineCached: WriteEngine<ReservationModel> by lazy { db.writeEngineFor<ReservationModel>(COLLECTION_NAME) }

        fun writeEngine(): WriteEngine<ReservationModel> = writeEngineCached
    }

    /**
     * The user settings collection. Each user has one [NotificationPreferenceModel] record.
     */
    object NotificationPreferences {
        const val COLLECTION_NAME = "notificationPreferences"

        // One engine per collection, built the first time it is asked for and kept: an
        // engine holds only the shared Firestore instance, the collection name and the
        // schema class, so handing out a fresh one per call rebuilt the same object on
        // every frame that read the collection (the profile screen and the notice paths
        // read it per draw).
        private val readEngineCached: QueryEngine<NotificationPreferenceModel> by lazy { db.readEngineFor<NotificationPreferenceModel>(COLLECTION_NAME) }

        fun readEngine(): QueryEngine<NotificationPreferenceModel> = readEngineCached

        // Kept for the same reason as the read engine above.
        private val writeEngineCached: WriteEngine<NotificationPreferenceModel> by lazy { db.writeEngineFor<NotificationPreferenceModel>(COLLECTION_NAME) }

        fun writeEngine(): WriteEngine<NotificationPreferenceModel> = writeEngineCached
    }

    /**
     * The app settings collection. Each user has one [AppSettingsModel] record holding
     * the app view and the role settings they keep between app lifetimes.
     */
    object AppSettings {
        const val COLLECTION_NAME = "appSettings"

        // One engine per collection, built the first time it is asked for and kept: an
        // engine holds only the shared Firestore instance, the collection name and the
        // schema class, so handing out a fresh one per call rebuilt the same object on
        // every frame that read the collection (the profile screen and the notice paths
        // read it per draw).
        private val readEngineCached: QueryEngine<AppSettingsModel> by lazy { db.readEngineFor<AppSettingsModel>(COLLECTION_NAME) }

        fun readEngine(): QueryEngine<AppSettingsModel> = readEngineCached

        // Kept for the same reason as the read engine above.
        private val writeEngineCached: WriteEngine<AppSettingsModel> by lazy { db.writeEngineFor<AppSettingsModel>(COLLECTION_NAME) }

        fun writeEngine(): WriteEngine<AppSettingsModel> = writeEngineCached
    }

    object BookedDates {
        const val COLLECTION_NAME = "bookedDates"

        // Kept for the same reason as the read engine above.
        private val writeEngineCached: WriteEngine<BookedDate> by lazy { db.writeEngineFor<BookedDate>(COLLECTION_NAME) }

        fun writeEngine(): WriteEngine<BookedDate> = writeEngineCached
    }

    object Campsites {
        const val COLLECTION_NAME = "campsites"

        // One engine per collection, built the first time it is asked for and kept: an
        // engine holds only the shared Firestore instance, the collection name and the
        // schema class, so handing out a fresh one per call rebuilt the same object on
        // every frame that read the collection (the profile screen and the notice paths
        // read it per draw).
        private val readEngineCached: QueryEngine<Campsite> by lazy { db.readEngineFor<Campsite>(COLLECTION_NAME) }

        fun readEngine(): QueryEngine<Campsite> = readEngineCached

        // Kept for the same reason as the read engine above.
        private val writeEngineCached: WriteEngine<Campsite> by lazy { db.writeEngineFor<Campsite>(COLLECTION_NAME) }

        fun writeEngine(): WriteEngine<Campsite> = writeEngineCached
    }

    /**
     * The Waiting List Collection. All [WaitingListEntry] records are stored here.
     */
    object WaitingListEntries {
        const val COLLECTION_NAME = "waitingList"

        // One engine per collection, built the first time it is asked for and kept: an
        // engine holds only the shared Firestore instance, the collection name and the
        // schema class, so handing out a fresh one per call rebuilt the same object on
        // every frame that read the collection (the profile screen and the notice paths
        // read it per draw).
        private val readEngineCached: QueryEngine<WaitingListEntry> by lazy { db.readEngineFor<WaitingListEntry>(COLLECTION_NAME) }

        fun readEngine(): QueryEngine<WaitingListEntry> = readEngineCached

        // Kept for the same reason as the read engine above.
        private val writeEngineCached: WriteEngine<WaitingListEntry> by lazy { db.writeEngineFor<WaitingListEntry>(COLLECTION_NAME) }

        fun writeEngine(): WriteEngine<WaitingListEntry> = writeEngineCached
    }

    /**
     * The Messages collection. One document per chat message; all messages of one reservation
     * share the same reservationId (that is the conversation).
     */
    object Messages {
        const val COLLECTION_NAME = "messages"

        // One engine per collection, built the first time it is asked for and kept: an
        // engine holds only the shared Firestore instance, the collection name and the
        // schema class, so handing out a fresh one per call rebuilt the same object on
        // every frame that read the collection (the profile screen and the notice paths
        // read it per draw).
        private val readEngineCached: QueryEngine<Message> by lazy { db.readEngineFor<Message>(COLLECTION_NAME) }

        fun readEngine(): QueryEngine<Message> = readEngineCached

        // Kept for the same reason as the read engine above.
        private val writeEngineCached: WriteEngine<Message> by lazy { db.writeEngineFor<Message>(COLLECTION_NAME) }

        fun writeEngine(): WriteEngine<Message> = writeEngineCached
    }
}