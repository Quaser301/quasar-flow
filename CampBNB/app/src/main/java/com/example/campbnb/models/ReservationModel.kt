
package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

enum class ReservationStatus {
    PENDING,
    ACTIVE,
    PAST,
    CANCELLED,
    REJECTED
}

data class ReservationModel(
    var lotteryEntryId: String = "",
    var campsiteId: String = "",
    var entrantId: String = "",
    var ownerId: String = "",
    var guests: Int = 1,
    var dates: List<String> = emptyList(),
    var startDate: String = "",
    var endDate: String = "",
    var status: ReservationStatus = ReservationStatus.PENDING,
    var refundRecorded: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    var rules: Boolean = false
) : Identifiable()
