package com.example.campbnb.models

// AI-assisted (US 01.05.02 and US 01.12.01, QR image files and their reader): written by Qwen 3.8 Flash
// IQ1_M.

import io.github.kamilkrzywanski.barleader.BarcodeReader
import io.github.kamilkrzywanski.barleader.QrCode as BarleaderQrCode
import io.github.kamilkrzywanski.barleader.QrOptions
import io.github.kamilkrzywanski.barleader.ReadOptions
import io.github.kamilkrzywanski.barleader.Symbology
import javax.imageio.ImageIO

/**
 * The real image codec for CampBNB codes, on Barleader (a pure-Java barcode library
 * whose reader path is a port of zxing-cpp) and the JDK's own image codecs. One
 * direction writes the actual picture of a code (the module grid that encodes its
 * text, as a PNG file's bytes), assembled from the grid itself so it runs where the
 * device has no java.awt; the other reads the text out of a real image file, the way
 * the app's scanner must when a camera hands it an image instead of text. The read
 * half uses the JDK's image codec and only runs on the JVM host.
 */

private val qrImageModulePixels = 8

/**
 * The bytes of the real PNG image of this code: a black-and-white module grid any
 * scanner can read, holding exactly this code's text. The picture is drawn straight
 * from the encoded symbol's grid, not through an image class: the JDK image codec
 * needs java.awt, which the Android runtime does not have, and a downloaded QR
 * file has to survive there.
 *
 * @param qrCode The code to draw.
 * @return The PNG file bytes of the drawn code.
 */
fun qrImageFileBytes(qrCode: QRCode): ByteArray {
    val symbol = BarleaderQrCode.encode(qrCode.payload(), QrOptions.defaults())
    return pngBytesOf(symbol)
}

/**
 * The PNG bytes of a code's module grid, written without any image class: a code's
 * picture truly is its grid (a module is dark or light), and Barleader keeps that
 * grid in [symbol] through its size and [isDark], so the picture is assembled here
 * one byte at a time. Each module is a square of qrImageModulePixels, ringed by a
 * quiet zone of light modules so any scanner can locate the code; the rows are then
 * written as a PNG file's own chunks, with the image bytes carried in zlib stored
 * blocks, which every PNG reader must accept and which need no compressor here.
 *
 * @param symbol The encoded code whose grid to draw.
 * @return The PNG file bytes of the drawn code.
 */
private fun pngBytesOf(symbol: BarleaderQrCode): ByteArray {
    val side = (symbol.size() + 2 * BarleaderQrCode.QUIET_ZONE) * qrImageModulePixels

    // The image before it is wrapped: each scanline carries a filter byte 0 (none)
    // before its row of three-byte RGB pixels, black for a dark module and white
    // for a light one, with the quiet zone answering as white.
    val raw = ByteArray(side * (3 * side + 1))
    var at = 0
    var row = 0
    while (row < side) {
        raw[at] = 0.toByte()
        at += 1
        var column = 0
        while (column < side) {
            val x = column / qrImageModulePixels - BarleaderQrCode.QUIET_ZONE
            val y = row / qrImageModulePixels - BarleaderQrCode.QUIET_ZONE
            val inside = x >= 0 && x < symbol.size() && y >= 0 && y < symbol.size()
            val shade = if (inside && symbol.isDark(y, x)) 0.toByte() else 0xFF.toByte()
            var channel = 0
            while (channel < 3) {
                raw[at] = shade
                at += 1
                channel += 1
            }
            column += 1
        }
        row += 1
    }

    // Wrapped as zlib stored blocks: a two-byte header (deflate in the default
    // window, no compression asked for), then the raw rows in blocks of at most
    // 65535 bytes, each block named by its length and the complement of that
    // length so a reader can catch a corrupted count, and last the checksum of
    // the raw rows, Adler-32, which zlib's own format requires.
    val blockCount = if (raw.size == 0) 1 else (raw.size + 65534) / 65535
    val idat = ByteArray(2 + raw.size + blockCount * 5 + 4)
    at = 0
    idat[at] = 0x78.toByte()
    idat[at + 1] = 0x01.toByte()
    at += 2
    var start = 0
    var block = 0
    while (block < blockCount) {
        val length = if (raw.size - start < 65535) raw.size - start else 65535
        idat[at] = if (block == blockCount - 1) 0x01.toByte() else 0x00.toByte()
        idat[at + 1] = (length and 0xFF).toByte()
        idat[at + 2] = ((length ushr 8) and 0xFF).toByte()
        val onesComplement = length xor 0xFFFF
        idat[at + 3] = (onesComplement and 0xFF).toByte()
        idat[at + 4] = ((onesComplement ushr 8) and 0xFF).toByte()
        at += 5
        var copied = 0
        while (copied < length) {
            idat[at] = raw[start + copied]
            at += 1
            copied += 1
        }
        start += length
        block += 1
    }
    writeIntBig(idat, at, adlerOf(raw))

    // The file itself: the signature that marks every PNG, a header naming a
    // color RGB image of one byte a channel at the drawn size, the wrapped rows,
    // and the closing end chunk.
    val picture = ByteArray(13)
    writeIntBig(picture, 0, side)
    writeIntBig(picture, 4, side)
    picture[8] = 8.toByte()
    picture[9] = 2.toByte()
    val header = pngChunkBytes("IHDR", picture)
    val image = pngImageChunks(idat)
    val end = pngChunkBytes("IEND", ByteArray(0))
    val signature = ByteArray(8)
    signature[0] = 0x89.toByte()
    signature[1] = 0x50.toByte()
    signature[2] = 0x4E.toByte()
    signature[3] = 0x47.toByte()
    signature[4] = 0x0D.toByte()
    signature[5] = 0x0A.toByte()
    signature[6] = 0x1A.toByte()
    signature[7] = 0x0A.toByte()

    val png = ByteArray(signature.size + header.size + image.size + end.size)
    var pngAt = copyBytes(png, 0, signature)
    pngAt = copyBytes(png, pngAt, header)
    pngAt = copyBytes(png, pngAt, image)
    copyBytes(png, pngAt, end)
    return png
}

/** Writes an integer into [bytes] at [at] as four bytes, most significant first. */
private fun writeIntBig(bytes: ByteArray, at: Int, value: Int) {
    bytes[at] = ((value ushr 24) and 0xFF).toByte()
    bytes[at + 1] = ((value ushr 16) and 0xFF).toByte()
    bytes[at + 2] = ((value ushr 8) and 0xFF).toByte()
    bytes[at + 3] = (value and 0xFF).toByte()
}

/**
 * One PNG chunk: the length of its data, its type as characters, the data, and the
 * CRC over its type and its data together, exactly as the format requires of every
 * chunk. The type characters are part of that checksum and not just the data: a real
 * reader computes it over both, so a chunk that names only its picture is rejected.
 */
private fun pngChunkBytes(type: String, data: ByteArray): ByteArray {
    val chunk = ByteArray(8 + type.count() + data.size)
    writeIntBig(chunk, 0, data.size)
    var at = 4
    for (character in type) {
        chunk[at] = character.code.toByte()
        at += 1
    }
    var read = 0
    while (read < data.size) {
        chunk[at] = data[read]
        at += 1
        read += 1
    }
    writeIntBig(
        chunk,
        chunk.size - 4,
        pngCrcOf(sliceOf(chunk, 4, type.count() + data.size))
    )
    return chunk
}

private val crcPolynomial = 0xEDB88320.toInt()

/**
 * The image part of a PNG file: the zlib stream as one chunk when it fits, or as
 * several chunks each carrying the next part of the same stream. A reader puts the
 * parts back in order, so the split point is free to choose; it is the largest
 * block a zlib stored block can name (its own length is two bytes), which is what
 * keeps every stored block here legal.
 */
private fun pngImageChunks(idat: ByteArray): ByteArray {
    val parts = if (idat.size == 0) 1 else (idat.size + 65534) / 65535
    var all = ByteArray(parts * 12 + idat.size)
    var at = 0
    var start = 0
    var part = 0
    while (part < parts) {
        val length = if (idat.size - start < 65535) idat.size - start else 65535
        val chunk = pngChunkBytes("IDAT", sliceOf(idat, start, length))
        copyBytes(all, at, chunk)
        at += chunk.size
        start += length
        part += 1
    }
    return all
}

/** The bytes of [data] from [at] for [length] of them. */
private fun sliceOf(data: ByteArray, at: Int, length: Int): ByteArray {
    val piece = ByteArray(length)
    var read = 0
    while (read < length) {
        piece[read] = data[at + read]
        read += 1
    }
    return piece
}

/** The CRC-32 over these bytes, the table-free form (a PNG chunk runs it over its type characters and its data). */
private fun pngCrcOf(data: ByteArray): Int {
    var crc = -1
    var read = 0
    while (read < data.size) {
        crc = crc xor (data[read].toInt() and 0xFF)
        var bit = 0
        while (bit < 8) {
            crc = (crc ushr 1) xor (crcPolynomial and -(crc and 1))
            bit += 1
        }
        read += 1
    }
    return crc xor -1
}

/** The Adler-32 a zlib stream closes with, over its uncompressed content. */
private fun adlerOf(data: ByteArray): Int {
    var low = 1
    var high = 0
    var read = 0
    while (read < data.size) {
        low = (low + (data[read].toInt() and 0xFF)) % 65521
        high = (high + low) % 65521
        read += 1
    }
    return (high shl 16) or low
}

/** Copies [from] into [bytes] at [at] and answers where the copy ends. */
private fun copyBytes(bytes: ByteArray, at: Int, from: ByteArray): Int {
    var read = 0
    while (read < from.size) {
        bytes[at + read] = from[read]
        read += 1
    }
    return at + from.size
}

/**
 * The text held by the first QR Code in the grey pixels of one camera frame. Unlike the
 * file path below, this one needs no image codec: the reader takes the flat grey buffer
 * and the frame's own width and height, so it runs on the device as well as on the JVM
 * host. Reading is allowed to find nothing: pixels that hold no code, a buffer that does
 * not match the given size, or a picture with no readable QR all answer with null, the
 * same as an unscannable input elsewhere in this app.
 *
 * @param framePixels The frame's grey pixels, row after row, as the device gave them.
 * @param frameWidth The width of one row of those pixels.
 * @param frameHeight The number of those rows.
 * @return The text the QR pixels hold, or null when they name no readable QR.
 */
fun qrTextOfFramePixels(framePixels: ByteArray, frameWidth: Int, frameHeight: Int): String? {
    if (frameWidth <= 0 || frameHeight <= 0 || framePixels.size < frameWidth * frameHeight) {
        return null
    }
    val hits = BarcodeReader.sequential().read(
        framePixels,
        frameWidth,
        frameHeight,
        ReadOptions.fast().symbologies(Symbology.QR_CODE)
    )
    if (hits.isEmpty()) {
        return null
    }
    return hits[0].text()
}

/**
 * The text held by the first QR Code image in a real image file. Reading is allowed to
 * find nothing: a file that cannot be opened, a file with no picture in it, or a
 * picture with no readable QR all answer with null, the same as an unscannable input
 * elsewhere in this app. This path needs the JDK image codec, so unlike the frame path
 * above it only runs where that codec exists — the Android runtime has no java.awt.
 *
 * @param imageFilePath The file to read the code from.
 * @return The text the QR image holds, or null when the file names no readable QR.
 */
fun qrTextOfImageFile(imageFilePath: String): String? {
    val imageFile = java.io.File(imageFilePath)
    if (!imageFile.canRead()) {
        return null
    }
    val picture = try {
        ImageIO.read(imageFile.readBytes().inputStream())
    } catch (e: Exception) {
        null
    }
    if (picture == null) {
        return null
    }
    val hits = BarcodeReader.sequential().read(
        picture,
        ReadOptions.fast().symbologies(Symbology.QR_CODE)
    )
    if (hits.isEmpty()) {
        return null
    }
    return hits[0].text()
}
