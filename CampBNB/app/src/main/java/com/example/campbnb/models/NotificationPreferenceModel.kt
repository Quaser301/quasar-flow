package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

/**
 * Settings a user chooses for notifications and downloads.
 *
 * The document ID is the [userId], so one user has exactly one settings record.
 * Defaults are deliberately boring: push notifications are on, CSV downloads are off.
 *
 * @property userId ID of the user whose settings this record belongs to. Also the document ID.
 * @property allowPushNotifications Whether the user asked for push notification delivery.
 * @property allowCsvReservationDownload Whether the user asked for reservation CSV downloads.
 */
data class NotificationPreferenceModel(
    var userId: String = "",
    var allowPushNotifications: Boolean = true,
    var allowCsvReservationDownload: Boolean = false
) : Identifiable() {
    init {
        if (userId.isNotEmpty()) {
            id = userId
        }
    }
}
