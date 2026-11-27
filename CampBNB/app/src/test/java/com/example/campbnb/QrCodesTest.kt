package com.example.campbnb

import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.InMemoryFileDownloadWriter
import com.example.campbnb.data.NoFileDownloadWriter
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.models.QRCode
import com.example.campbnb.models.qrCodeOfScannedText
import com.example.campbnb.models.qrImageFileBytes
import com.example.campbnb.models.qrLinkRoute
import com.example.campbnb.models.qrRouteOfLinkPayload
import com.example.campbnb.models.qrScreenOfLinkPayload
import com.example.campbnb.models.qrTextOfImageFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOTICE: This file was AI-assisted. The verbatim prompts for this work are recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 *
 * The cases added for the QR-image request recorded there draw codes as real PNG files
 * and read them back through the picture door; the file scan doors answer null when an
 * input names no readable code.
 */

// US 01.12.01: a code carries a campsite id and nothing else — the expiry is dropped
// (US 01.05.02), so a code works at every time and a scan or an opened link reaches
// its campsite at every time.
class QrCodesTest {

    /** A store handing back [kept] campsites, never touching Firebase. */
    private class FakeQrStore(val kept: List<Campsite>) : CampsiteRepository() {
        override fun getAll(
            onSuccess: (List<Campsite>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onSuccess(kept)
        }
    }

    /** A store whose load always fails, to watch a scan weather the store. */
    private class OfflineQrStore : CampsiteRepository() {
        override fun getAll(
            onSuccess: (List<Campsite>) -> Unit,
            onFailure: (Exception) -> Unit
        ) {
            onFailure(Exception("offline"))
        }
    }

    private fun targetCamp(): Campsite {
        return Campsite(ownerId = "owner1", campName = "Target Camp").also { it.id = "target" }
    }

    // TEST: the code's text reads back as the same code
    @Test
    fun qrCode_payload_roundTripsToTheSameCode() {
        val code = QRCode(campsiteId = "target")
        val readBack = qrCodeOfScannedText(code.payload())
        if (readBack == null) {
            assertTrue(false)
        } else {
            assertEquals("target", readBack.campsiteId)
        }
    }

    // TEST: text that names no campsite is not code text
    @Test
    fun qrCodeOfScannedText_rejectsTextThatNamesNoCampsite() {
        val unreadable = listOf(
            "",
            "hello",
            "CAMPBNB:",
            "CAMPBNB:  ",
            "OTHER:target"
        )
        for (text in unreadable) {
            assertTrue(qrCodeOfScannedText(text) == null)
        }
    }

    // TEST: a code carries no stop moment, so any text with a segment past the
    // campsite is not code text
    @Test
    fun scanned_text_with_an_extra_segment_is_not_a_code() {
        assertEquals(null, qrCodeOfScannedText("CAMPBNB:c1:extra"))
        assertEquals(null, qrCodeOfScannedText("CAMPBNB:c1:0"))
    }

    // TEST: the one code text the app writes reads back through the same door
    @Test
    fun scanned_text_of_a_written_code_readsBack() {
        assertEquals("c1", qrCodeOfScannedText("CAMPBNB:c1")?.campsiteId)
    }

    // TEST: a live code's link text resolves to the campsite link route, at every
    // time — there is no clock to ask
    @Test
    fun qrLink_codeText_routesToTheCampsiteLink() {
        assertEquals(
            "$qrLinkRoute/target",
            qrRouteOfLinkPayload(QRCode(campsiteId = "target").payload())
        )
    }

    // TEST: a code's link has ONE route — the campsite link. A legacy three-part
    // spelling is not this app's code text and names no route at all (US 01.05.02,
    // the reading is narrowed to the one spelling a code writes).
    @Test
    fun qrLink_onlyTheCampsiteRouteIsReachable() {
        assertEquals(
            "$qrLinkRoute/target",
            qrRouteOfLinkPayload(QRCode(campsiteId = "target").payload())
        )
        assertEquals(null, qrRouteOfLinkPayload("CAMPBNB:target:1700000000000"))
    }

    // TEST: link text that is not code text names no route at all
    @Test
    fun qrLink_textThatIsNotCodeText_namesNoRoute() {
        assertEquals(null, qrRouteOfLinkPayload("a note from the front desk"))
    }

    // TEST: the typed twin resolves THROUGH the decision above and decodes the text
    // onto the Screen vocabulary: a code names its campsite link screen
    @Test
    fun qrScreenOfLinkPayload_codeText_namesTheCampsiteLinkScreen() {
        val opened = qrScreenOfLinkPayload(QRCode(campsiteId = "target").payload())

        assertTrue(opened is Screen.QrLinkRoute)
        assertEquals("target", (opened as Screen.QrLinkRoute).campsiteID)
    }

    // TEST: the typed twin names no screen for a legacy spelling — the narrowed
    // reading leaves nothing for it to open.
    @Test
    fun qrScreenOfLinkPayload_noLegacySpelling_namesNoScreen() {
        assertEquals(null, qrScreenOfLinkPayload("CAMPBNB:target:1700000000000"))
    }

    // TEST: the typed twin names no screen for text that is not code text
    @Test
    fun qrScreenOfLinkPayload_textThatIsNotCodeText_namesNoScreen() {
        assertEquals(null, qrScreenOfLinkPayload("a note from the front desk"))
    }

    // TEST: a code opens the campsite it names and the model selects it
    @Test
    fun scanQr_workingCode_opensNamedCampsite() {
        val store = listOf(
            targetCamp(),
            Campsite(ownerId = "owner1", campName = "Other Camp").also { it.id = "other" }
        )
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeQrStore(store))
        val opened = mutableListOf<Campsite>()
        controller.scanQr(
            QRCode(campsiteId = "target").payload(),
            onOpened = { camp -> opened.add(camp) },
            onUnreadable = { text -> }
        )
        assertEquals(1, opened.size)
        assertEquals("target", opened[0].id)
        assertEquals("Target Camp", opened[0].campName)
    }

    // TEST: a code works at every time, so the same text that was dead under the
    // old rule now opens its campsite
    @Test
    fun scanQr_codeWithNoExpiry_stillOpens() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeQrStore(listOf(targetCamp())))
        var openedCount = 0
        controller.scanQr(
            QRCode(campsiteId = "target").payload(),
            onOpened = { camp -> openedCount += 1 },
            onUnreadable = { text -> }
        )
        assertEquals(1, openedCount)
        assertEquals("", model.statusMessage)
    }

    // TEST: text that is not code text reports itself unreadable
    @Test
    fun scanQr_textThatIsNotCodeText_reportsUnreadable() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeQrStore(listOf(targetCamp())))
        var rejectedText = ""
        controller.scanQr(
            "a note from the front desk",
            onOpened = { camp -> },
            onUnreadable = { text -> rejectedText = text }
        )
        assertEquals("a note from the front desk", rejectedText)
        assertEquals("That text is not CampBNB code text", model.statusMessage)
    }

    // TEST: a code naming an unregistered campsite reports itself unreadable
    @Test
    fun scanQr_codeNamingUnregisteredCampsite_reportsUnreadable() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeQrStore(listOf(targetCamp())))
        var openedCount = 0
        controller.scanQr(
            QRCode(campsiteId = "ghost").payload(),
            onOpened = { camp -> openedCount += 1 },
            onUnreadable = { text -> }
        )
        assertEquals(0, openedCount)
        assertEquals("No campsite matches that code", model.statusMessage)
    }

    // TEST: when the store fails, only the model's status message reports
    @Test
    fun scanQr_storeFailure_onlyStatusReports() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, OfflineQrStore())
        var anyCallbackRan = false
        controller.scanQr(
            QRCode(campsiteId = "target").payload(),
            onOpened = { camp -> anyCallbackRan = true },
            onUnreadable = { text -> anyCallbackRan = true }
        )
        assertEquals(false, anyCallbackRan)
        assertEquals("Could not open that campsite: offline", model.statusMessage)
    }

    // TEST: a registered campsite gets code text that reads back as the same code
    @Test
    fun makeQrCode_registeredCampsite_producesReadableText() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeQrStore(listOf(targetCamp())))
        var madePayload = ""
        controller.makeQrCode("target") { text -> madePayload = text }
        assertTrue(madePayload != "")
        val made = qrCodeOfScannedText(madePayload)
        if (made == null) {
            assertTrue(false)
        } else {
            assertEquals("target", made.campsiteId)
        }
    }

    // TEST: an unregistered campsite gets no code
    @Test
    fun makeQrCode_unregisteredCampsite_isRejected() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeQrStore(emptyList()))
        var madePayload = ""
        controller.makeQrCode("ghost") { text -> madePayload = text }
        assertEquals("", madePayload)
        assertEquals("That campsite is not registered yet", model.statusMessage)
    }

    // TEST: no campsite named at all is rejected without even loading the store
    @Test
    fun makeQrCode_noCampsiteNamed_isRejected() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, OfflineQrStore())
        var madePayload = ""
        controller.makeQrCode("") { text -> madePayload = text }
        assertEquals("", madePayload)
        assertEquals("Please pick a campsite to make a code for", model.statusMessage)
    }

    // TEST: the code's file is named after its campsite, cleaned like any download name
    @Test
    fun qrCode_fileName_namesTheCampsiteFile() {
        assertEquals("target.qr.png", QRCode(campsiteId = "target").fileName())
        assertEquals("a-b-c.qr.png", QRCode(campsiteId = "a b/c").fileName())
    }

    // TEST: the shared download provider writes the code's real PNG image under that name
    @Test
    fun qrCodeFileDownload_writesTheRealPngUnderTheSafeName() {
        val writer = InMemoryFileDownloadWriter()
        val code = QRCode(campsiteId = "a b")
        val pngBytes = qrImageFileBytes(code)
        var done = false
        writer.downloadFile(code.fileName(), pngBytes) { result ->
            done = result.isSuccess
        }
        assertEquals(true, done)
        val written = writer.writtenFiles["a-b.qr.png"]
        if (written == null) {
            assertTrue(false)
        } else {
            assertTrue(
                "a PNG file starts with its signature",
                written.size > 8 && written[0] == 0x89.toByte() && written[1] == 0x50.toByte() &&
                    written[2] == 0x4E.toByte() && written[3] == 0x47.toByte()
            )
        }
    }

    // TEST: the drawn code really scans back to its text through a real image file
    @Test
    fun qrCode_realRoundTrip_drawsTheCodeWritesItOutAndScansItBack() {
        val code = QRCode(campsiteId = "round-trip-camp")
        val pngBytes = qrImageFileBytes(code)
        val imagePath = "qr-real-round-trip.png"
        val imageFile = java.io.File(imagePath)
        imageFile.writeBytes(pngBytes)
        assertEquals(code.payload(), qrTextOfImageFile(imagePath))
        imageFile.delete()
    }

    // TEST: a file that is not a picture names no QR text
    @Test
    fun qrImageScanner_notAnImageFileAndMissingFiles_areNotReadable() {
        val notImage = "qr-not-an-image.txt"
        java.io.File(notImage).writeText("not a picture")
        assertEquals(null, qrTextOfImageFile(notImage))
        assertEquals(null, qrTextOfImageFile("qr-no-such-file.png"))
        val corruptImage = "qr-corrupt-image.png"
        val corruptFile = java.io.File(corruptImage)
        val corruptBytes = ByteArray(24)
        corruptBytes[0] = 0x89.toByte()
        corruptBytes[1] = 0x50.toByte()
        corruptBytes[2] = 0x4E.toByte()
        corruptBytes[3] = 0x47.toByte()
        corruptFile.writeBytes(corruptBytes)
        assertEquals(null, qrTextOfImageFile(corruptImage))
        corruptFile.delete()
        java.io.File(notImage).delete()
    }

    // TEST: the default no-writer still reports done, so screens stay working
    @Test
    fun fileDownloadWriter_noWriter_reportsDoneWithoutSaving() {
        val writer = NoFileDownloadWriter()
        var done = false
        writer.downloadFile("target.qr.png", ByteArray(0)) { result ->
            done = result.isSuccess
        }
        assertEquals(true, done)
    }
}
