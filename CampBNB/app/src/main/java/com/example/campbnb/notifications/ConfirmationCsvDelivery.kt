package com.example.campbnb.notifications

import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.FileDownloadWriter
import com.example.campbnb.data.NotificationPreferencesService
import com.example.campbnb.data.ReservationRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationSettings
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.reservationCsvBytesOf
import com.example.campbnb.models.reservationCsvFileName

/**
 * Delivers a reservation confirmation as an auto-downloaded CSV instead of a banner,
 * for entrants whose Confirmation Settings choice is CSV (US 01.05.02).
 */
class ConfirmationCsvDelivery (
    private val preferences: NotificationPreferencesService,
    private val reservations: ReservationRepository,
    private val campsites: CampsiteRepository,
    private val writer: FileDownloadWriter,
    // Called with the file name when a CSV was saved, so the app can tell the user.
    private val onSaved: (String) -> Unit = {}
) {
    /** True when this user chose CSV in Confirmation Settings. */
    fun wantsCsv(userId: String): Boolean =
        NotificationSettings.CSV in preferences.notificationSettingsFor(userId)

    /**
     * Writes the CSV for the reservation the confirmation names.
     *
     * @param onResult true if the file was written; false when it was not (not this user's
     * own reservation, a failed lookup or a failed write), so the caller can fall back.
     */
    fun deliver(notification: Notification, userId: String, onResult: (Boolean) -> Unit) {
        if (notification.reservationId.isBlank()) {
            onResult(false)
            return
        }
        reservations.getByLotteryEntry(
            notification.reservationId,
            { reservation ->
                // The owner is told of the same confirmation; only the entrant gets the CSV.
                if (reservation == null || reservation.entrantId != userId) {
                    onResult(false)
                } else {
                    campsites.getById(
                        reservation.campsiteId,
                        { campsite -> write(reservation, campsite, onResult) },
                        { _ -> write(reservation, null, onResult) } // still write, with the id as location
                    )
                }
            },
            { _ -> onResult(false) }
        )
    }

    private fun write(reservation: ReservationModel, campsite: Campsite?, onResult: (Boolean) -> Unit) {
        val fileName = reservationCsvFileName(reservation)
        writer.downloadFile(
            fileName,
            reservationCsvBytesOf(reservation, campsite)
        ) { result ->
            if (result.isSuccess) onSaved(fileName)
            onResult(result.isSuccess)
        }
    }
}
