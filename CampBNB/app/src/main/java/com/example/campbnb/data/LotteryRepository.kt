
package com.example.campbnb.data

import com.example.campbnb.database.Database
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.WaitingListEntry

/**
 * Boundary to Firestore for lotteries and their waiting lists.
 * Methods are open so tests can override them with a fake that never touches Firebase.
 */
open class LotteryRepository {

    /** Reads every campsite for a given owner. */
    fun loadCampsitesByOwner(
        onSuccess: (List<Campsite>) -> Unit,
        onFailure: (Exception) -> Unit,
        ownerId: String
    ) {
        Database.Campsites.readEngine().query()
            .whereEquals(Campsite::ownerId, ownerId)
            .execute(onSuccess, onFailure)
    }

    /** Writes a created lottery to the database. */
    fun save(
        lottery: Lottery,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        Database.Lotteries.writeEngine().save(lottery) { result ->
            if (result.isSuccess) {
                onSuccess()
            } else {
                val exception =
                    (result.exceptionOrNull() as? Exception)
                        ?: Exception("Unknown error")
                onFailure(exception)
            }
        }
    }

    /** Reads every lottery. */
    open fun getAllLotteries(
        onSuccess: (List<Lottery>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        Database.Lotteries.readEngine().query()
            .execute(onSuccess, onFailure)
    }

    /** Reads the waiting-list entries of one lottery. */
    open fun getEntries(
        lotteryId: String,
        onSuccess: (List<WaitingListEntry>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        Database.WaitingListEntries.readEngine().query()
            .whereEquals(WaitingListEntry::lotteryId, lotteryId)
            .execute(onSuccess, onFailure)
    }

    /** Reads every waiting-list entry belonging to one user. */
    open fun getEntriesByUser(
        userId: String,
        onSuccess: (List<WaitingListEntry>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        Database.WaitingListEntries.readEngine().query()
            .whereEquals(WaitingListEntry::userId, userId)
            .execute(onSuccess, onFailure)
    }

    /** Adds an entrant to a waiting list. */
    open fun addEntry(
        entry: WaitingListEntry,
        onComplete: (Result<Exception?>) -> Unit
    ) {
        Database.WaitingListEntries.writeEngine().save(entry, onComplete)
    }

    /**
     * Updates an existing waiting-list entry.
     * The entry's deterministic ID means this writes to the same document.
     */
    open fun updateEntry(
        entry: WaitingListEntry,
        onComplete: (Result<Exception?>) -> Unit
    ) {
        Database.WaitingListEntries.writeEngine().save(entry, onComplete)
    }

    /** Removes an entrant from a waiting list. */
    open fun removeEntry(
        lotteryId: String,
        userId: String,
        onComplete: (Result<Exception?>) -> Unit
    ) {
        val id = WaitingListEntry.makeId(lotteryId, userId)
        Database.WaitingListEntries.writeEngine().delete(id, onComplete)
    }
}
