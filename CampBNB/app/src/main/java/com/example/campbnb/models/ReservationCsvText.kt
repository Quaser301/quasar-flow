package com.example.campbnb.models

// AI-assisted (reservation CSV download file): written by Qwen 3.8 Flash
// IQ1_M.

import com.example.campbnb.data.downloadSafeName
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.ReservationModel

/**
 * The UTF-8 bytes of this reservation's CSV text: what a CSV file holds when written.
 *
 * @param reservation The reservation the file stands for.
 * @param campsite The campsite the reservation is at, if known.
 * @return The file bytes of the CSV.
 */
fun reservationCsvBytesOf(reservation: ReservationModel, campsite: Campsite?): ByteArray {
    return utf8BytesOf(reservationCsvText(reservation, campsite))
}

/** The UTF-8 bytes of text, the form every text file in this app holds. */
private fun utf8BytesOf(text: String): ByteArray {
    return text.byteInputStream().readBytes()
}

/**
 * The name of the file this reservation's CSV text is downloaded as: one reservation,
 * one file, named after its lottery entry.
 *
 * @param reservation The reservation the file stands for.
 * @return The file name the download system should use.
 */
fun reservationCsvFileName(reservation: ReservationModel): String {
    val id = if (reservation.lotteryEntryId.isNotBlank()) {
        reservation.lotteryEntryId
    } else {
        "unspecified"
    }
    return "${downloadSafeName(id)}.csv"
}

/** Builds the CSV text requested by US 01.05.02 from already-loaded reservation data. */
fun reservationCsvText(reservation: ReservationModel, campsite: Campsite?): String {
    fun csvField(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
        return if (needsQuotes) "\"${escaped}\"" else escaped
    }

    val location = if (campsite == null) {
        reservation.campsiteId
    } else {
        "${campsite.campName} / ${campsite.campLoc}"
    }

    val owner = if (reservation.ownerId.isNotBlank()) {
        reservation.ownerId
    } else if (campsite != null) {
        campsite.ownerId
    } else {
        ""
    }

    return """
        Start,${csvField(reservation.startDate)}
        End,${csvField(reservation.endDate)}
        Campsite Name/Location,${csvField(location)}
        Owner,${csvField(owner)}
    """.trimIndent()
}
