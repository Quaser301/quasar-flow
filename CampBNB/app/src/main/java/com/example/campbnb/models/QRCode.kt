package com.example.campbnb.models

import com.example.campbnb.data.downloadSafeName
import com.example.campbnb.Screen

// The text of a code names CampBNB and the campsite it opens, in that order,
// separated by a colon. Campsite ids are Firestore document ids and never contain
// colons, so splitting on them always reads a code back. A code carries nothing
// else: it works at every time (US 01.05.02, the expiry is dropped).
private val qrCodePrefix = "CAMPBNB"

/**
 * Entity: a CampBNB QR code standing at a campsite (US 01.12.01). A code embeds
 * exactly one thing: which campsite it opens. There is no expiry on a code — a
 * code works at every time, so a code that names a registered campsite always
 * opens it (US 01.05.02, the expiry is dropped).
 *
 * @property campsiteId The campsite this code opens.
 */
data class QRCode(val campsiteId: String = "") {

    /** The text to embed in the QR: this code's campsite. */
    fun payload(): String {
        return "$qrCodePrefix:$campsiteId"
    }

    /** The name of the file this code can be downloaded as. */
    fun fileName(): String {
        return "${downloadSafeName(campsiteId)}.qr.png"
    }
}

/**
 * Reads scanned text back into a [QRCode]: null when the text is not CampBNB code text.
 * Code text names CampBNB and then a campsite, and nothing else — a code carries no
 * expiry, so there is no stale-but-real code to distinguish from a broken one.
 *
 * @param payload The text read from a scanned code.
 * @return The code the text describes, or null if the text is not code text.
 */
fun qrCodeOfScannedText(payload: String): QRCode? {
    val parts = payload.split(":")
    if (parts.size != 2 || parts[0] != qrCodePrefix) {
        return null
    }
    val campsiteId = parts[1]
    if (campsiteId.isBlank()) {
        return null
    }
    return QRCode(campsiteId = campsiteId)
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
 * opens (US 01.05.02): text that is not code text names no route at all; anything
 * that IS code text names the campsite link, because a code carries no expiry and
 * therefore works at every time. The old-code route is no longer reachable — nothing
 * a code says can make it a dead one.
 *
 * @param payload The text carried by the opened link.
 * @return The route the text names, or null when the text names nothing.
 */
fun qrRouteOfLinkPayload(payload: String): String? {
    val code = qrCodeOfScannedText(payload) ?: return null
    return "$qrLinkRoute/${code.campsiteId}"
}

/**
 * The same resolution as [qrRouteOfLinkPayload], as the typed [Screen] route to open:
 * the pending-screen bridge carries screens, so the app graph navigates a real screen
 * and a renamed screen breaks the decode at compile time. Kept beside the text form
 * because the text form is the tested decision this one delegates to.
 *
 * @param payload The text carried by the opened link.
 * @return The screen the text names, or null when it names nothing.
 */
fun qrScreenOfLinkPayload(payload: String): Screen? {
    val routeText = qrRouteOfLinkPayload(payload) ?: return null
    return when {
        routeText == qrOldCodeRoute -> Screen.OldQRCode
        routeText.startsWith("$qrLinkRoute/") ->
            Screen.QrLinkRoute(routeText.substringAfter("$qrLinkRoute/"))
        else -> null
    }
}
