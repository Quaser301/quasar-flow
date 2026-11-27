package com.example.campbnb

/**
 * NOTICE: This file was AI-assisted. The verbatim prompts for this work are recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 *
 * The cases below follow the frame-to-code adapter recorded there: the grey pixels of
 * one frame read back as the code's own text through the reader that takes a flat
 * buffer and the frame's size, a buffer that does not match the size it is given names
 * no text, and the frame is deliberately NOT square so a swapped size cannot pass by
 * accident.
 */

import com.example.campbnb.models.QRCode
import com.example.campbnb.models.qrTextOfFramePixels
import io.github.kamilkrzywanski.barleader.QrCode
import io.github.kamilkrzywanski.barleader.QrOptions
import org.junit.Assert.assertEquals
import org.junit.Test

// US 01.12.01: the pixels a camera frame carries are read for a code without any image
// codec, which is what lets the device's own frames be read at all.
class QrFramePixelsTest {

    /** The grey pixels of a drawn code, row after row, with extra light columns on the
     *  right so the frame is wider than it is tall. */
    private class GreyFrameOfCode(private val payload: String) {
        val symbol = QrCode.encode(payload, QrOptions.defaults())
        val quiet = QrCode.QUIET_ZONE
        val modulePixels = 8
        val frameWidth = (symbol.size() + 2 * quiet) * modulePixels + 3 * modulePixels
        val frameHeight = (symbol.size() + 2 * quiet) * modulePixels

        val framePixels: ByteArray = run {
            val pixels = ByteArray(frameWidth * frameHeight)
            pixels.fill(0xFF.toByte())
            var row = 0
            while (row < frameHeight) {
                var column = 0
                while (column < frameWidth) {
                    val x = column / modulePixels - quiet
                    val y = row / modulePixels - quiet
                    val inside = x >= 0 && x < symbol.size() && y >= 0 && y < symbol.size()
                    if (inside && symbol.isDark(y, x)) {
                        pixels[row * frameWidth + column] = 0.toByte()
                    }
                    column += 1
                }
                row += 1
            }
            pixels
        }
    }

    // TEST: the grey pixels of a drawn code read back as the code's own text
    @Test
    fun aFramesGreyPixels_readTheCodesText() {
        val code = QRCode(campsiteId = "grey-frame-read")
        val frame = GreyFrameOfCode(code.payload())
        assertEquals(code.payload(), qrTextOfFramePixels(frame.framePixels, frame.frameWidth, frame.frameHeight))
    }

    // TEST: the frame is wider than it is tall, so reading its size the other way
    // round does not find the code — the two are not interchangeable
    @Test
    fun aFramesGreyPixels_areNotReadWithASwappedSize() {
        val code = QRCode(campsiteId = "grey-frame-read")
        val frame = GreyFrameOfCode(code.payload())
        assertEquals(null, qrTextOfFramePixels(frame.framePixels, frame.frameHeight, frame.frameWidth))
    }

    // TEST: a buffer that does not cover the size it is given names no text
    @Test
    fun qrTextOfFramePixels_bufferShorterThanTheSize_readNoText() {
        assertEquals(null, qrTextOfFramePixels(ByteArray(12), 8, 8))
        assertEquals(null, qrTextOfFramePixels(ByteArray(0), 0, 0))
        assertEquals(null, qrTextOfFramePixels(ByteArray(64), 8, 0))
    }
}
