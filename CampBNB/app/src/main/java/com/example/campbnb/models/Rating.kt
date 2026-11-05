package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

class Rating (
    val campsiteId: String = "",
    val userId: String = "",
    val stars: Int = 0
) : Identifiable() {

    init {
        id = makeId(campsiteId, userId)
    }

    companion object {
        /** The document id for this lottery and user. */
        fun makeId(lotteryId: String, userId: String): String {
            return lotteryId + "_" + userId
        }
    }
}