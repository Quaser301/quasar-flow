package com.example.campbnb.models

// AI-assisted (US 01.05.02 and US 01.12.01, QR image files and their reader): written by Qwen 3.8 Flash
// IQ1_M.

import io.github.kamilkrzywanski.barleader.BarcodeReader
import io.github.kamilkrzywanski.barleader.QrCode as BarleaderQrCode
import io.github.kamilkrzywanski.barleader.QrOptions
import io.github.kamilkrzywanski.barleader.ReadOptions
import io.github.kamilkrzywanski.barleader.Symbology
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * The real image codec for CampBNB codes, on Barleader (a pure-Java barcode library
 * whose reader path is a port of zxing-cpp) and the JDK's own image codecs. One
 * direction writes the actual picture of a code (the module grid that encodes its
 * text, as a PNG file's bytes); the other reads the text out of a real image file,
 * the way the app's scanner must when a camera hands it an image instead of text.
 */

private val qrImageModulePixels = 8

/**
 * The bytes of the real PNG image of this code: a black-and-white module grid any
 * scanner can read, holding exactly this code's text. The picture is written through
 * the JDK image codec to a temporary PNG file and its bytes read straight back.
 *
 * @param qrCode The code to draw.
 * @return The PNG file bytes of the drawn code.
 */
fun qrImageFileBytes(qrCode: QRCode): ByteArray {
    val symbol = BarleaderQrCode.encode(qrCode.payload(), QrOptions.defaults())
    val image = symbol.toImage(qrImageModulePixels, BarleaderQrCode.QUIET_ZONE)
    return pngBytesOf(image)
}

/** The PNG bytes an image will have when saved as a file. */
private fun pngBytesOf(image: BufferedImage): ByteArray {
    val pngFile = createTempFile("campbnb-qr", ".png")
    ImageIO.write(image, "png", pngFile)
    val fileBytes = pngFile.readBytes()
    pngFile.delete()
    return fileBytes
}

/**
 * The text held by the first QR Code in the bytes of an encoded picture, whether those
 * bytes came from a file or from a camera frame. Reading is allowed to find nothing:
 * bytes that hold no picture, a picture that will not decode, or a picture with no
 * readable QR all answer with null, the same as an unscannable input elsewhere in
 * this app.
 *
 * @param imageBytes The bytes of the picture to read.
 * @return The text the QR image holds, or null when the bytes name no readable QR.
 */
fun qrTextOfImageBytes(imageBytes: ByteArray): String? {
    val picture = try {
        ImageIO.read(imageBytes.inputStream())
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

/**
 * The text held by the first QR Code image in a real image file. Reading is allowed to
 * find nothing: a file that cannot be opened, a file with no picture in it, or a
 * picture with no readable QR all answer with null, the same as an unscannable input
 * elsewhere in this app.
 *
 * @param imageFilePath The file to read the code from.
 * @return The text the QR image holds, or null when the file names no readable QR.
 */
fun qrTextOfImageFile(imageFilePath: String): String? {
    val imageFile = java.io.File(imageFilePath)
    if (!imageFile.canRead()) {
        return null
    }
    return qrTextOfImageBytes(imageFile.readBytes())
}
