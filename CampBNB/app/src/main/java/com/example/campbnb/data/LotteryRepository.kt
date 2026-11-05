package com.example.campbnb.data

import com.example.campbnb.database.Database
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Lottery

/**
 * 05-11-26: Prompted gemini 3.6 to help me fix the syntax of the else failure
 * logic of the save function.
 */

class LotteryRepository {
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
}
