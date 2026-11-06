package com.example.campbnb.data

import com.example.campbnb.database.Database
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.WaitingListEntry


// 05-11-26: Prompted gemini 3.6 to help me fix the syntax of the else failure
// logic of the save function.


/**
 * Boundary to Firestore for lotteries and their waiting lists. Methods are 'open' so
 * tests can override them with a fake that never touches Firebase.
 */
open class LotteryRepository {
    /** Reads every campsite for a given owner. */
    fun loadCampsitesByOwner(onSuccess: (List<Campsite>) -> Unit, 
                             onFailure: (Exception) -> Unit,
                             ownerId: String) {
        Database.Campsites.readEngine().query().
        whereEquals(Campsite::ownerId, ownerId).execute(onSuccess, onFailure)
    }

    /** Writes created lottery to database. Lottery is validated before this function
     * gets called.
     * */
    fun save(lottery: Lottery, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        Database.Lotteries.writeEngine().save(lottery) { result ->
            if (result.isSuccess) {
                onSuccess()
            } else {
                val exception = (result.exceptionOrNull() as? Exception) ?: Exception("Unknown error")
                onFailure(exception)
            }
        }
    }

    /** Reads every lottery, so the controller can find the open one for a campsite. */
    open fun getAllLotteries(onSuccess: (List<Lottery>) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Lotteries.readEngine().query().execute(onSuccess, onFailure)
    }

    /** Reads lotteries belonging to a specific owner. */
    open fun getLotteriesForOwner(ownerId: String, onSuccess: (List<Lottery>) -> Unit, onFailure: (Exception) -> Unit) {
        Database.Lotteries.readEngine().query()
            .whereEquals(Lottery::ownerId, ownerId)
            .execute(onSuccess, onFailure)
    }

    /** Reads the waiting list entries of one lottery. */
    open fun getEntries(
        lotteryId: String,
        onSuccess: (List<WaitingListEntry>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        Database.WaitingListEntries.readEngine().query()
            .whereEquals(WaitingListEntry::lotteryId, lotteryId)
            .execute(onSuccess, onFailure)
    }

    /** Adds an entrant to a waiting list. */
    open fun addEntry(entry: WaitingListEntry, onComplete: (Result<Exception?>) -> Unit) {
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
