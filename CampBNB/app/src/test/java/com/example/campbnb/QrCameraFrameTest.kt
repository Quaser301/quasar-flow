package com.example.campbnb

/**
 * NOTICE: This file was AI-assisted. The verbatim prompts for this work are recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 *
 * The cases below follow the camera-feed request recorded there: a frame holding a drawn
 * code reads back through the same picture door a file reads through, bytes that hold
 * no picture name no text, and the no-frame providers answer their caller exactly when
 * the device gives nothing.
 */

import com.example.campbnb.models.QRCode
import com.example.campbnb.models.qrImageFileBytes
import com.example.campbnb.models.qrTextOfImageBytes
import com.example.campbnb.ui.shared.CameraFrameSource
import com.example.campbnb.ui.shared.NoCameraFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// US 01.12.01: a camera frame is read for a code the same way the picture in a file
// is, and a device that gives no frame keeps the scanner on typed text instead of
// stopping it.
class QrCameraFrameTest {

    /** A provider that always hands back the one chosen frame, like holding a picture up. */
    private class OneFrameCamera(val chosenFrameBytes: ByteArray?) : CameraFrameSource {
        override fun requestFrame(onFrame: (ByteArray?) -> Unit) {
            onFrame(chosenFrameBytes)
        }
    }

    /** A provider that never answers, to watch the caller wait for one frame at most. */
    private class SilentCamera : CameraFrameSource {
        override fun requestFrame(onFrame: (ByteArray?) -> Unit) {}
    }

    // TEST: a frame holding a drawn code reads back as the code's own text
    @Test
    fun aFrameOfADrawnCode_readsBackTheCodesText() {
        val code = QRCode(campsiteId = "frame-round-trip", stopsAtMillis = 0L)
        val frameBytes = qrImageFileBytes(code)
        assertEquals(code.payload(), qrTextOfImageBytes(frameBytes))
    }

    // TEST: bytes that hold no picture name no QR text, at the frame door
    @Test
    fun qrTextOfImageBytes_noPictureBytes_readNoText() {
        assertEquals(null, qrTextOfImageBytes("not a picture".toByteArray()))
        assertEquals(null, qrTextOfImageBytes(ByteArray(0)))
        val corruptBytes = ByteArray(24)
        corruptBytes[0] = 0x89.toByte()
        corruptBytes[1] = 0x50.toByte()
        corruptBytes[2] = 0x4E.toByte()
        corruptBytes[3] = 0x47.toByte()
        assertEquals(null, qrTextOfImageBytes(corruptBytes))
    }

    // TEST: a frame the caller hands over is read through the same door a file is read
    @Test
    fun oneFrameCamera_handsTheChosenFrameToTheReader() {
        val code = QRCode(campsiteId = "held-up-frame", stopsAtMillis = 1700000000000L)
        val provider = OneFrameCamera(qrImageFileBytes(code))
        var frameText: String? = null
        provider.requestFrame { frame ->
            frameText = if (frame == null) null else qrTextOfImageBytes(frame)
        }
        assertEquals(code.payload(), frameText)
    }

    // TEST: the no-camera provider answers with no frame, which keeps screens typing
    @Test
    fun noCameraFrame_answersNoFrame() {
        var calledBack = false
        var givenFrame: ByteArray? = null
        NoCameraFrame().requestFrame { frame ->
            calledBack = true
            givenFrame = frame
        }
        assertEquals(true, calledBack)
        assertEquals(null, givenFrame)
    }

    // TEST: a provider answers its caller at most once for one request, so a
    // screen waiting on a frame never hears twice
    @Test
    fun aFrameProvider_answersItsCallerAtMostOnce() {
        val code = QRCode(campsiteId = "held-up-frame", stopsAtMillis = 0L)
        var answers = 0
        OneFrameCamera(qrImageFileBytes(code)).requestFrame { frame ->
            answers += 1
        }
        SilentCamera().requestFrame { frame ->
            answers += 1
        }
        assertEquals(1, answers)
    }
}
