package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

/**
 * A user's star rating of one campsite. The document id joins campsite and user, so a
 * person can rate a given campsite at most once and a re-rate replaces the record.
 */
class Rating (
    val campsiteId: String = "",
    val userId: String = "",
    val stars: Int = 0
) : Identifiable() {

    init {
        id = makeId(campsiteId, userId)
    }

    companion object {

        /** Joins the two ids that make a rating unique. */
        fun makeId(lotteryId: String, userId: String): String {
            return lotteryId + "_" + userId
        }
    }
}