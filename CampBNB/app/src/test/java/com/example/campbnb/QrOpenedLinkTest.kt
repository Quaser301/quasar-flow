package com.example.campbnb

// AI-assisted (US 01.05.02 night batch, a code's link opened by the device's own camera): written by Qwen 3.8 Flash
// Next Coder IQ1_M.
//
// What these cases cover: the text a device hands over when it opens a code's link, which is a
// URI in this app's own scheme and whose scheme carries no case of its own, read back into code
// text whatever spelling arrived; text that names no link of this app, which names no code; and
// the route the read-back text resolves to, which is the same one a scan of that code opens.

import com.example.campbnb.models.codeTextOfOpenedLink
import com.example.campbnb.models.qrRouteOfLinkPayload

import org.junit.Assert.assertEquals
import org.junit.Test

class QrOpenedLinkTest {

    @Test
    fun test_CodeText_OpenedAsItsOwnLink_StillNamesTheCode() {
        assertEquals("CAMPBNB:camp-1:2000", codeTextOfOpenedLink("CAMPBNB:camp-1:2000"))
        assertEquals(
            "the lower-cased scheme names the same code",
            "CAMPBNB:camp-1:2000",
            codeTextOfOpenedLink("campbnb:camp-1:2000")
        )
    }

    @Test
    fun test_TextThatNamesNoLinkOfThisApp_namesNoCode() {
        assertEquals(null, codeTextOfOpenedLink("https://campbnb.example/qr"))
        assertEquals(null, codeTextOfOpenedLink("campbnb"))
        assertEquals(null, codeTextOfOpenedLink("campbnb:"))
    }

    @Test
    fun test_AnOpenedLink_resolvesToTheSameRouteAScanDoes() {
        assertEquals(
            "a live code opened as a link opens the campsite it names",
            "qr-link/camp-1",
            qrRouteOfLinkPayload(codeTextOfOpenedLink("campbnb:camp-1:2000") ?: "", 1000L)
        )
        assertEquals(
            "a dead code opened as a link still answers the old-code notice",
            "qr-link-old",
            qrRouteOfLinkPayload(codeTextOfOpenedLink("campbnb:camp-1:2000") ?: "", 2001L)
        )
    }
}
