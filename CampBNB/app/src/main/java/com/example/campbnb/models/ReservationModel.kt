package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

enum class ReservationStatus { ACTIVE, CANCELLED, REJECTED }

/**
 * A single reservation document, shared by the owner and entrant views.
 *
 * Dates are stored as ISO strings (yyyy-MM-dd) because Firestore has no date-only type and
 * ISO strings sort correctly, so range queries on [endDate] work. Every property has a
 * default because Firestore needs a no-argument constructor to read documents back.
 *
 * The document ID is the [lotteryEntryId], so a second reservation for the same entry
 * is impossible.
 *
 * @property lotteryEntryId ID of the lottery win this reservation belongs to. Also the document ID.
 * @property campsiteId The campsite being reserved.
 * @property entrantId The entrant who made the reservation.
 * @property ownerId The owner of the campsite.
 * @property guests How many guests are staying.
 * @property dates Every reserved date as yyyy-MM-dd. Fixed once created.
 * @property startDate Earliest reserved date, kept for range queries.
 * @property endDate Latest reserved date, kept for range queries.
 * @property status Current [ReservationStatus].
 * @property refundRecorded Whether a refund was recorded after a cancellation or rejection.
 * @property createdAt Epoch millis when the reservation was made.
 * @property rules Whether the entrant accepted the campsite rules and policies before reserving.
 */
data class ReservationModel(
    var lotteryEntryId: String = "",
    var campsiteId: String = "",
    var entrantId: String = "",
    var ownerId: String = "",
    var guests: Int = 1,
    var dates: List<String> = emptyList(),
    var startDate: String = "",
    var endDate: String = "",
    var status: ReservationStatus = ReservationStatus.ACTIVE,
    var refundRecorded: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    var rules: Boolean = false
) : Identifiable()