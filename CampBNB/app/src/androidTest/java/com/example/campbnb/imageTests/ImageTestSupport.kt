package com.example.campbnb.imageTests

// tests created with the help of Claude Sonnet 5.5

/**
 * Helpers for the image intent tests (US 02.01.02, US 01.08.02): a stand-in for the phone's
 * photo picker that answers at once, and small test pictures.
 */

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** A picker that answers every launch at once with [uri], as if the user had chosen it. */
fun pickerReturning(uri: Uri): ActivityResultRegistryOwner {
    val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            dispatchResult(requestCode, uri)
        }
    }
    return object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
    }
}

/** Writes a big [color] picture (a PNG, larger than any limit) into the cache; returns its Uri. */
fun writeTestPicture(context: Context, name: String, color: Int): Uri {
    val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(color)
    val file = File(context.cacheDir, name)
    val out = FileOutputStream(file)
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    out.close()
    return Uri.fromFile(file)
}

/** Writes a file that is not a picture at all; returns its Uri. */
fun writeNotAPicture(context: Context, name: String): Uri {
    val file = File(context.cacheDir, name)
    file.writeText("this is plain text, not an image")
    return Uri.fromFile(file)
}

/** A small stored picture (Base64 JPEG text) in [color], as the database would hold it. */
fun storedPicture(color: Int): String {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(color)
    val bytes = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bytes)
    return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
}

/** The picture held by [stored], or null if the text is not an image. */
fun decodeStored(stored: String): Bitmap? {
    val bytes = Base64.decode(stored, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}