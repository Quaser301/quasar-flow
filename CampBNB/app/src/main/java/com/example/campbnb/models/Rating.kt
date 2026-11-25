package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

/**
 * One user's rating of one campsite, with the written review attached to it (US 01.13.01,
 * US 01.13.02). The rating and the review are one document, so they are shown and deleted together.
 *
 * Every parameter has a default value because Firestore needs that to read a document.
 *
 * @property campsiteId The campsite that was rated.
 * @property userId The user who rated it.
 * @property stars 1 to 5.
 * @property userName The reviewer's name as shown on the reviews screen. Copied from the profile
 * when the rating is saved, so it does not change if the profile is renamed later.
 * @property review The written review. Empty if the user only gave stars.
 */
class Rating (
    val campsiteId: String = "",
    val userId: String = "",
    val stars: Int = 0,
    val userName: String = "",
    val review: String = ""
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