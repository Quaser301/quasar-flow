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
        assertEquals("CAMPBNB:camp-1", codeTextOfOpenedLink("CAMPBNB:camp-1"))
        assertEquals(
            "the lower-cased scheme names the same code",
            "CAMPBNB:camp-1",
            codeTextOfOpenedLink("campbnb:camp-1")
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
            "a code opened as a link opens the campsite it names, at every time",
            "qr-link/camp-1",
            qrRouteOfLinkPayload(codeTextOfOpenedLink("campbnb:camp-1") ?: "")
        )
        assertEquals(
            "text carrying a segment past the campsite is not this app's code text",
            null,
            qrRouteOfLinkPayload(codeTextOfOpenedLink("CAMPBNB:camp-1:2000") ?: "")
        )
    }
}
