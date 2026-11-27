package com.example.campbnb.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

// Prompted Claude Sonnet 5.5 for implementation and concepts related to this file.

/**
 * Boundary that turns a picture the user picked into the text we store, and nothing else
 * (US 02.01.02 campsite images, US 01.08.02 profile images).
 *
 * Pictures live in the database itself: the picture is shrunk, saved as a JPEG and written as
 * Base64 text into the record that owns it (Campsite.campImg or User.profileImg). Firestore
 * allows about 1 MiB per document, so a stored picture has to stay small: [encode] refuses a
 * picture it cannot get under [MAX_BYTES].
 *
 * To move pictures to Firebase Storage later, only this class changes: [encode] would upload
 * the file and return its link, and the same text fields would hold the link instead.
 *
 * Methods are 'open' so tests can override them with a fake.
 *
 * @property resolver Reads the bytes behind the Uri that the phone's picker returns.
 */
open class ImageRepository(private val resolver: ContentResolver) {

    /**
     * Shrinks the picture at [uri] and returns it as the text to store.
     *
     * The longest side ends up at most [maxEdge] pixels, a sideways phone photo is turned
     * upright, and a transparent picture is drawn on white (a JPEG has no transparency). The
     * JPEG quality drops step by step until the picture is at most [MAX_BYTES].
     *
     * @param uri The picture the user picked.
     * @param maxEdge The longest side allowed, in pixels (see [CAMPSITE_EDGE], [PROFILE_EDGE]).
     * @return Base64 text of the JPEG bytes, or null if [uri] is not a readable image or no
     * quality made it small enough.
     */
    open fun encode(uri: Uri, maxEdge: Int): String? {
        try {
            val picture = readShrunk(uri, maxEdge)
            if (picture == null) {
                return null
            }
            val upright = turned(picture, turnOf(uri))
            val flat = onWhite(upright)

            for (quality in QUALITIES) {
                val bytes = ByteArrayOutputStream()
                flat.compress(Bitmap.CompressFormat.JPEG, quality, bytes)
                if (bytes.size() <= MAX_BYTES) {
                    return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                }
            }
            return null
        } catch (e: Exception) {
            // A picture that vanished, or one this app is not allowed to read.
            return null
        }
    }

    private fun streamOf(uri: Uri): InputStream? {
        return resolver.openInputStream(uri)
    }

    /** Reads the picture at [uri], shrunk so its longest side is at most [maxEdge] pixels. */
    private fun readShrunk(uri: Uri, maxEdge: Int): Bitmap? {
        // First pass reads only the picture's size, so we know how much to shrink it.
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        val sizeStream = streamOf(uri)
        if (sizeStream == null) {
            return null
        }
        try {
            BitmapFactory.decodeStream(sizeStream, null, bounds)
        } finally {
            sizeStream.close()
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null   // not an image
        }

        var sampleSize = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sampleSize * 2) >= maxEdge) {
            sampleSize = sampleSize * 2
        }
        val options = BitmapFactory.Options()
        options.inSampleSize = sampleSize
        val pictureStream = streamOf(uri)
        if (pictureStream == null) {
            return null
        }
        var decoded: Bitmap? = null
        try {
            decoded = BitmapFactory.decodeStream(pictureStream, null, options)
        } finally {
            pictureStream.close()
        }
        if (decoded == null) {
            return null
        }

        // Sampling only halves, so finish with an exact scale.
        val side = maxOf(decoded.width, decoded.height)
        if (side <= maxEdge) {
            return decoded
        }
        val scale = maxEdge.toFloat() / side
        val width = maxOf(1, Math.round(decoded.width * scale))
        val height = maxOf(1, Math.round(decoded.height * scale))
        return Bitmap.createScaledBitmap(decoded, width, height, true)
    }

    /** The degrees a photo must be turned to be upright, from its EXIF tag (0 if it has none). */
    private fun turnOf(uri: Uri): Int {
        val stream = streamOf(uri)
        if (stream == null) {
            return 0
        }
        try {
            val exif = ExifInterface(stream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                return 90
            }
            if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                return 180
            }
            if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                return 270
            }
            return 0
        } catch (e: IOException) {
            return 0   // no readable tag, a PNG for example: leave the picture as it is
        } finally {
            stream.close()
        }
    }

    private fun turned(picture: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) {
            return picture
        }
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(picture, 0, 0, picture.width, picture.height, matrix, true)
    }

    /** Draws the picture on a white background, because a JPEG would turn transparency black. */
    private fun onWhite(picture: Bitmap): Bitmap {
        val flat = Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(picture, 0f, 0f, null)
        return flat
    }

    companion object {
        /** Longest side, in pixels, of a campsite's image. The Explore cards show about 600. */
        const val CAMPSITE_EDGE = 800

        /** Longest side, in pixels, of a profile image, which is only ever shown small. */
        const val PROFILE_EDGE = 256

        /**
         * The largest JPEG, in bytes, that is stored. As Base64 text it is a third bigger,
         * which keeps a whole document under Firestore's 1 MiB limit.
         */
        const val MAX_BYTES = 400_000

        private val QUALITIES = intArrayOf(85, 70, 55, 40)
    }
}