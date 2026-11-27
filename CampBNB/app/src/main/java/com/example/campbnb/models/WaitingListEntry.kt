
package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

/**
 * One entrant on one lottery's waiting list.
 * The ID is built from the lottery and user, preventing duplicate entries.
 */
class WaitingListEntry(
    val lotteryId: String = "",
    val userId: String = "",
    val joinedAt: Long = 0,
    val status: LotteryEntryStatus = LotteryEntryStatus.WAITING_FOR_DRAW
) : Identifiable() {

    init {
        id = makeId(lotteryId, userId)
    }

    companion object {
        fun makeId(lotteryId: String, userId: String): String {
            return lotteryId + "_" + userId
        }
    }
}

/**
 * Tracks an entrant's progress through a lottery.
 */
enum class LotteryEntryStatus {
    WAITING_FOR_DRAW,
    WON_AWAITING_RESPONSE,
    ACCEPTED,
    DECLINED,
    NOT_SELECTED
}
