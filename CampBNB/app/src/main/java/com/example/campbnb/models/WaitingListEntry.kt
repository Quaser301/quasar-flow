package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

/**
 * Entity: one entrant on one lottery's waiting list (US 01.02.01).
 * The id is built from the lottery and the user, so an entrant cannot be on a list twice.
 * E and A may add fields later (for example the draw result); give them default values.
 */
class WaitingListEntry(
    val lotteryId: String = "",
    val userId: String = "",
    val joinedAt: Long = 0
) : Identifiable() {

    init {
        id = makeId(lotteryId, userId)
    }

    companion object {
        /** The document id for this lottery and user. */
        fun makeId(lotteryId: String, userId: String): String {
            return lotteryId + "_" + userId
        }
    }
}