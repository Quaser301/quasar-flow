package com.example.campbnb.models

// AI-assisted (app settings persistence record): written by Qwen 3.8 Flash Next Coder IQ1_M.

import com.example.campbnb.database.Identifiable

/**
 * Entity: the app settings a user holds between app lifetimes.
 *
 * The document ID is the [userId], so one user has exactly one settings record, the
 * same way [NotificationPreferenceModel] keeps one preferences record per user. Values
 * left as the empty string mean "not chosen": the setting keeps its controller's
 * boring default until the user picks one. The one boolean follows the owner
 * settings controller's default, so an absent choice changes nothing.
 *
 * @property userId ID of the user whose settings this record belongs to. Also the document ID.
 * @property appViewChoice The view the user is currently in, by its name, or empty for no choice.
 * @property entrantBookingsOrder The chosen entrant bookings ordering, or empty for none.
 * @property entrantInboxAlerts The chosen entrant inbox alerting, or empty for none.
 * @property entrantMessagesFilter The chosen entrant message filtering, or empty for none.
 * @property ownerListingVisibility The chosen owner listing visibility, or empty for none.
 * @property ownerLotteryAccepting Whether the owner's lotteries accept new entrants.
 * @property ownerAvailabilityNotice The chosen owner availability notice, or empty for none.
 */
data class AppSettingsModel(
    var userId: String = "",
    var appViewChoice: String = "",
    var entrantBookingsOrder: String = "",
    var entrantInboxAlerts: String = "",
    var entrantMessagesFilter: String = "",
    var ownerListingVisibility: String = "",
    var ownerLotteryAccepting: Boolean = true,
    var ownerAvailabilityNotice: String = ""
) : Identifiable() {
    init {
        if (userId.isNotEmpty()) {
            id = userId
        }
    }
}
