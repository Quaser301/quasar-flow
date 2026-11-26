package com.example.campbnb.models

import com.example.campbnb.data.downloadSafeName
import com.example.campbnb.Screen

// The text of a code names CampBNB, the campsite it opens, and the moment it stops
// working, in that order, separated by colons. Campsite ids are Firestore document
// ids and never contain colons, so splitting on them always reads a code back.
private val qrCodePrefix = "CAMPBNB"

/**
 * Entity: a CampBNB QR code standing at a campsite (US 01.12.01). A code embeds
 * exactly two things: which campsite it opens, and the moment it is supposed to stop
 * working. The reserved epoch value 0, January 1, 1970, means the code carries no
 * expiry at all: such a code is an old one and never works (US 01.05.02).
 *
 * @property campsiteId The campsite this code opens.
 * @property stopsAtMillis The moment this code stops working; 0 means no expiry, and
 *   an old code with no expiry works at no time at all.
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

    /** True while this code still works at [atMillis]: a code carrying no expiry never
     * works at all, any other code works up to and including its stop-working moment. */
    fun isValidAt(atMillis: Long): Boolean {
        return stopsAtMillis != 0L && atMillis <= stopsAtMillis
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

/**
 * The code text an opened CampBNB link carries (US 01.05.02): a launcher hands over
 * the URI it opened, and a code's text already IS a URI in this app's own scheme —
 * the scheme is the code's prefix. A URI's scheme carries no case of its own (a
 * launcher normalises it either way), so the text is rebuilt from this app's own
 * prefix rather than from the spelling that arrived. Text that is already code text
 * answers as itself, which is what a launcher that hands over the bare text does.
 *
 * @param openedText The text the device handed over with the opened link.
 * @return The code text the link carries, or null when the text names no link of this app.
 */
fun codeTextOfOpenedLink(openedText: String): String? {
    if (qrCodeOfScannedText(openedText) != null) {
        return openedText
    }
    val scheme = openedText.substringBefore(":")
    if (scheme.isEmpty() || scheme.lowercase() != qrCodePrefix.lowercase()) {
        return null
    }
    val rest = openedText.substringAfter(":")
    if (rest.isEmpty() || rest == openedText) {
        return null
    }
    return "$qrCodePrefix:$rest"
}

// The link story of a code (US 01.05.02): the phone's own camera hands the app the
// code's text through an Intent extra, exactly as a notification tap hands over a
// route; the route resolved from the text opens like any other pending route. The
// old-code notice route is ONE path segment wide so it can never collide with a
// live code's two-segment campsite link, even for a campsite id that spells it.
val qrPayloadExtraKey = "campbnb.qrPayload"
val qrLinkRoute = "qr-link"
val qrOldCodeRoute = "qr-link-old"

/**
 * Resolves the text carried by an opened CampBNB code link into the route the app
 * opens (US 01.05.02): text that is not code text names no route at all; a dead
 * code — its stop-working moment already past, or no expiry at all — answers the
 * old-code notice instead of a destination; a code that still works opens the
 * campsite it names, the same thing a scan opens.
 *
 * @param payload The text carried by the opened link.
 * @param atMillis The moment the link is opened; injectable so the decision tests
 *   at any clock.
 * @return The route the text names, or null when the text names nothing.
 */
fun qrRouteOfLinkPayload(payload: String, atMillis: Long = System.currentTimeMillis()): String? {
    val code = qrCodeOfScannedText(payload) ?: return null
    if (!code.isValidAt(atMillis)) {
        return qrOldCodeRoute
    }
    return "$qrLinkRoute/${code.campsiteId}"
}

/**
 * The same resolution as [qrRouteOfLinkPayload], as the typed [Screen] route to open:
 * the pending-screen bridge carries screens, so the app graph navigates a real screen
 * and a renamed screen breaks the decode at compile time. Kept beside the text form
 * because the text form is the tested decision this one delegates to.
 *
 * @param payload The text carried by the opened link.
 * @param atMillis The moment the link is opened; injectable so the decision tests
 *   at any clock.
 * @return The screen the text names, or null when it names nothing.
 */
fun qrScreenOfLinkPayload(payload: String, atMillis: Long = System.currentTimeMillis()): Screen? {
    val routeText = qrRouteOfLinkPayload(payload, atMillis) ?: return null
    return when {
        routeText == qrOldCodeRoute -> Screen.OldQRCode
        routeText.startsWith("$qrLinkRoute/") ->
            Screen.QrLinkRoute(routeText.substringAfter("$qrLinkRoute/"))
        else -> null
    }
}
