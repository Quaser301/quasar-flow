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
import com.example.campbnb.models.qrOldCodeRoute
import com.example.campbnb.models.qrRouteOfLinkPayload
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

// US 01.12.01: a code carries a campsite id and the moment it stops working; the
// reserved 0 (January 1, 1970) carries no expiry at all and is an old, dead code
// (US 01.05.02), and a scan or an opened link reaches a destination only while the
// code still works.
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
        val code = QRCode(campsiteId = "target", stopsAtMillis = 1700000000000L)
        val readBack = qrCodeOfScannedText(code.payload())
        if (readBack == null) {
            assertTrue(false)
        } else {
            assertEquals("target", readBack.campsiteId)
            assertEquals(1700000000000L, readBack.stopsAtMillis)
        }
    }

    // TEST: text that names no campsite is not code text
    @Test
    fun qrCodeOfScannedText_rejectsTextThatNamesNoCampsite() {
        val unreadable = listOf(
            "",
            "hello",
            "CAMPBNB::5",
            "CAMPBNB:c1:soon",
            "OTHER:target:0"
        )
        for (text in unreadable) {
            if (qrCodeOfScannedText(text) == null) {
                assertTrue(true)
            } else {
                assertTrue(false)
            }
        }
    }

    // TEST: text with one segment too many is not code text either
    @Test
    fun scanned_text_with_an_extra_segment_is_not_a_code() {
        assertEquals(null, qrCodeOfScannedText("CAMPBNB:c1:0:extra"))
    }

    // TEST: an already-expired code is still readable code text, and a negative
    // stop moment round-trips unchanged.
    @Test
    fun expired_codes_still_decode_and_negative_stops_round_trip() {
        val expired = qrCodeOfScannedText("CAMPBNB:c1:-1")
        if (expired == null) {
            assertTrue(false)
        } else {
            assertEquals("c1", expired.campsiteId)
            assertEquals(-1L, expired.stopsAtMillis)
        }

        val stopped = QRCode(campsiteId = "target", stopsAtMillis = -5L)
        val readBack = qrCodeOfScannedText(stopped.payload())
        if (readBack == null) {
            assertTrue(false)
        } else {
            assertEquals("target", readBack.campsiteId)
            assertEquals(-5L, readBack.stopsAtMillis)
        }
    }

    // TEST: a code with no expiry at all, the reserved 0, is an old code and works
    // at no time
    @Test
    fun qrCode_codeWithoutExpiry_neverWorksAtAnyTime() {
        val withoutExpiry = QRCode(campsiteId = "target", stopsAtMillis = 0L)
        assertTrue(!withoutExpiry.isValidAt(0L))
        assertTrue(!withoutExpiry.isValidAt(999999999999999L))
    }

    // TEST: an expiring code works up to and including its stop, not one moment later
    @Test
    fun qrCode_expiringCode_stopsWorkingAtItsStopMoment() {
        val stopsAtHighNoon = QRCode(campsiteId = "target", stopsAtMillis = 1200L)
        assertTrue(stopsAtHighNoon.isValidAt(1199L))
        assertTrue(stopsAtHighNoon.isValidAt(1200L))
        assertTrue(!stopsAtHighNoon.isValidAt(1201L))
        // a stop already in the past, even before the epoch, decodes as an expired code
        val longPast = QRCode(campsiteId = "target", stopsAtMillis = -1L)
        assertTrue(!longPast.isValidAt(0L))
    }

    // TEST: a live code's link text resolves to the campsite link route, up to and
    // including its stop moment
    @Test
    fun qrLink_liveCode_routesToTheCampsiteLink() {
        assertEquals(
            "$qrLinkRoute/target",
            qrRouteOfLinkPayload(QRCode(campsiteId = "target", stopsAtMillis = 1000L).payload(), 999L)
        )
        assertEquals(
            "$qrLinkRoute/target",
            qrRouteOfLinkPayload(QRCode(campsiteId = "target", stopsAtMillis = 1000L).payload(), 1000L)
        )
    }

    // TEST: a code whose stop has passed resolves to the old-code notice
    @Test
    fun qrLink_pastExpiry_routesToTheOldCodeNotice() {
        assertEquals(
            qrOldCodeRoute,
            qrRouteOfLinkPayload(QRCode(campsiteId = "target", stopsAtMillis = 1000L).payload(), 1001L)
        )
    }

    // TEST: a code carrying no expiry at all is an old code at any clock
    @Test
    fun qrLink_codeWithoutExpiry_routesToTheOldCodeNoticeAtAnyClock() {
        assertEquals(
            qrOldCodeRoute,
            qrRouteOfLinkPayload(QRCode(campsiteId = "target").payload(), 0L)
        )
        assertEquals(
            qrOldCodeRoute,
            qrRouteOfLinkPayload(QRCode(campsiteId = "target").payload(), 999999999999999L)
        )
    }

    // TEST: link text that is not code text names no route at all
    @Test
    fun qrLink_textThatIsNotCodeText_namesNoRoute() {
        assertEquals(null, qrRouteOfLinkPayload("a note from the front desk", 0L))
    }

    // TEST: a working code opens the campsite it names and the model selects it
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
            QRCode(campsiteId = "target", stopsAtMillis = 1000L).payload(),
            onOpened = { camp -> opened.add(camp) },
            onExpired = { stopped -> },
            onUnreadable = { text -> },
            clock = { 999L }
        )
        assertEquals(1, opened.size)
        assertEquals("target", opened[0].id)
        assertEquals("Target Camp", opened[0].campName)
    }

    // TEST: a code with no expiry at all is an old code even at the latest clock:
    // it reports itself expired and never opens
    @Test
    fun scanQr_codeWithoutExpiry_reportsOldAtAnyClock() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeQrStore(listOf(targetCamp())))
        var openedCount = 0
        var expiredCount = 0
        controller.scanQr(
            QRCode(campsiteId = "target", stopsAtMillis = 0L).payload(),
            onOpened = { camp -> openedCount += 1 },
            onExpired = { stopped -> expiredCount += 1 },
            onUnreadable = { text -> },
            clock = { 999999999999999L }
        )
        assertEquals(0, openedCount)
        assertEquals(1, expiredCount)
    }

    // TEST: a scan at exactly the stop moment still opens; one moment later does not
    @Test
    fun scanQr_atTheStopMoment_boundaryStillOpens() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeQrStore(listOf(targetCamp())))
        var openedCount = 0
        var expiredCount = 0
        controller.scanQr(
            QRCode(campsiteId = "target", stopsAtMillis = 1000L).payload(),
            onOpened = { camp -> openedCount += 1 },
            onExpired = { stopped -> expiredCount += 1 },
            onUnreadable = { text -> },
            clock = { 1000L }
        )
        controller.scanQr(
            QRCode(campsiteId = "target", stopsAtMillis = 1000L).payload(),
            onOpened = { camp -> openedCount += 1 },
            onExpired = { stopped -> expiredCount += 1 },
            onUnreadable = { text -> },
            clock = { 1001L }
        )
        assertEquals(1, openedCount)
        assertEquals(1, expiredCount)
    }

    // TEST: an expired code reports itself expired, never opens
    @Test
    fun scanQr_expiredCode_reportsExpiredNotOpened() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeQrStore(listOf(targetCamp())))
        var stopped: QRCode? = null
        var openedCount = 0
        controller.scanQr(
            QRCode(campsiteId = "target", stopsAtMillis = 1000L).payload(),
            onOpened = { camp -> openedCount += 1 },
            onExpired = { stopped = it },
            onUnreadable = { text -> },
            clock = { 1001L }
        )
        assertEquals(0, openedCount)
        val stoppedCode = stopped
        if (stoppedCode == null) {
            assertTrue(false)
        } else {
            assertEquals("target", stoppedCode.campsiteId)
            assertEquals(1000L, stoppedCode.stopsAtMillis)
        }
        assertEquals("That code stopped working", model.statusMessage)
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
            onExpired = { stopped -> },
            onUnreadable = { text -> rejectedText = text }
        )
        assertEquals("a note from the front desk", rejectedText)
        assertEquals("That text is not CampBNB code text", model.statusMessage)
    }

    // TEST: a code still working at the clock, naming an unregistered campsite,
    // reports itself unreadable
    @Test
    fun scanQr_codeNamingUnregisteredCampsite_reportsUnreadable() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeQrStore(listOf(targetCamp())))
        var openedCount = 0
        controller.scanQr(
            QRCode(campsiteId = "ghost", stopsAtMillis = 1000L).payload(),
            onOpened = { camp -> openedCount += 1 },
            onExpired = { stopped -> },
            onUnreadable = { text -> },
            clock = { 999L }
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
            QRCode(campsiteId = "target", stopsAtMillis = 1000L).payload(),
            onOpened = { camp -> anyCallbackRan = true },
            onExpired = { stopped -> anyCallbackRan = true },
            onUnreadable = { text -> anyCallbackRan = true },
            clock = { 999L }
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
        controller.makeQrCode("target", 1700000000000L) { text -> madePayload = text }
        assertTrue(madePayload != "")
        val made = qrCodeOfScannedText(madePayload)
        if (made == null) {
            assertTrue(false)
        } else {
            assertEquals("target", made.campsiteId)
            assertEquals(1700000000000L, made.stopsAtMillis)
        }
    }

    // TEST: no stop given means the reserved no-expiry value: such a code is an old,
    // dead one
    @Test
    fun makeQrCode_withoutAStop_recordsTheReservedNoExpiry() {
        val model = CampsiteModel()
        val controller = CampsiteController(model, FakeQrStore(listOf(targetCamp())))
        var madePayload = ""
        controller.makeQrCode("target") { text -> madePayload = text }
        val made = qrCodeOfScannedText(madePayload)
        if (made == null) {
            assertTrue(false)
        } else {
            assertEquals("target", made.campsiteId)
            assertEquals(0L, made.stopsAtMillis)
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
        val code = QRCode(campsiteId = "a b", stopsAtMillis = 0L)
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
        val code = QRCode(campsiteId = "round-trip-camp", stopsAtMillis = 0L)
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
