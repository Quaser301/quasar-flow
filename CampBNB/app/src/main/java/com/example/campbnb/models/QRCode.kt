package com.example.campbnb.models

import com.example.campbnb.data.downloadSafeName

// The text of a code names CampBNB, the campsite it opens, and the moment it stops
// working, in that order, separated by colons. Campsite ids are Firestore document
// ids and never contain colons, so splitting on them always reads a code back.
private val qrCodePrefix = "CAMPBNB"

/**
 * Entity: a CampBNB QR code standing at a campsite (US 01.12.01). A code embeds
 * exactly two things: which campsite it opens, and the moment it is supposed to stop
 * working. The reserved epoch value 0, January 1, 1970, means the code never stops
 * working.
 *
 * @property campsiteId The campsite this code opens.
 * @property stopsAtMillis The moment this code stops working; 0 means never.
 */
data class QRCode(val campsiteId: String = "", val stopsAtMillis: Long = 0) {

    /** The text to embed in the QR: this code's campsite and stop-working moment. */
    fun payload(): String {
        return "$qrCodePrefix:$campsiteId:$stopsAtMillis"
    }

    /** The name of the file this code can be downloaded as. */
    fun fileName(): String {
        return "${downloadSafeName(campsiteId)}.qr.png"
    }

    /** True while this code still works at [atMillis]: a never-expiring code works at
     * any time, any other code works up to and including its stop-working moment. */
    fun isValidAt(atMillis: Long): Boolean {
        return stopsAtMillis == 0L || atMillis <= stopsAtMillis
    }
}

/**
 * Reads scanned text back into a [QRCode]: null when the text is not CampBNB code text.
 * A stop-working moment in the past is a real, just expired, code rather than a broken
 * one, so it decodes; only text that does not name a campsite fails to decode.
 *
 * @param payload The text read from a scanned code.
 * @return The code the text describes, or null if the text is not code text.
 */
fun qrCodeOfScannedText(payload: String): QRCode? {
    val parts = payload.split(":")
    if (parts.size != 3 || parts[0] != qrCodePrefix) {
        return null
    }
    val campsiteId = parts[1]
    if (campsiteId.isBlank()) {
        return null
    }
    val stopsAtMillis = parts[2].toLongOrNull() ?: return null
    return QRCode(campsiteId = campsiteId, stopsAtMillis = stopsAtMillis)
}
