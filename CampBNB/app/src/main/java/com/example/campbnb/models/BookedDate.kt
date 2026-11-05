package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

/**
 * Marks one date at one campsite as taken.
 *
 * The document ID is `campsiteId_yyyy-MM-dd`, so claiming a date means creating its
 * document. Firestore security rules reject a second create on the same ID, which
 * prevents double-booking. The document is deleted when the reservation is cancelled
 * or rejected.
 *
 * @property campsiteId The campsite the date belongs to.
 * @property reservationId The reservation (lottery entry ID) that holds the date.
 */
data class BookedDate(
    val campsiteId: String = "",
    val reservationId: String = ""
) : Identifiable()