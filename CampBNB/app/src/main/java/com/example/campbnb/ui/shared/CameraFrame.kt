package com.example.campbnb.ui.shared

// AI-assisted (US 01.12.01, camera-frame provider seam and the frame-to-code adapter):
// written by Qwen 3.8 Flash Next Coder IQ1_M.

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageProxy
import androidx.camera.common.ImagePlane
import java.util.concurrent.Executor
import com.example.campbnb.models.qrTextOfFramePixels

/**
 * Provider: what this device's camera sees (US 01.12.01). Screens ask one frame here
 * instead of touching the device; a frame is the bytes of a real picture, which is
 * what reading a code out of a picture needs (qrTextOfImageBytes). A device that
 * gives no frame answers with null and the scanner keeps working on what the user
 * typed. A test provider hands back a chosen frame, the way DeviceId lets a test
 * replace its source.
 *
 * What this platform can and cannot hand back was re-read off the compiler rather than
 * assumed. The analysed-frame path DOES resolve: `ImageAnalysis.Builder().build()`, the
 * `ImageAnalysis.Analyzer` interface the device calls once per frame, an `ImageProxy`
 * with `imagePlanes`, `width` and `height`, and the planes themselves through
 * `androidx.camera.common.ImagePlane` (`buffer`, `rowStride`, `pixelStride`) — which is
 * why that package is a dependency. What does NOT resolve is the one thing this seam
 * asks for: a frame as the BYTES OF AN ENCODED PICTURE. The device hands back raw
 * planes, and the codec that would turn pixels into a picture file is `javax.imageio`
 * over `java.awt`, which is not on the device runtime. So no provider of THIS shape
 * exists on the device and `NoCameraFrame` stays the default; the device's frames are
 * read through the adapter below instead, which answers the code's TEXT rather than
 * picture bytes. (The older dead path, kept for history: the camera2 `CameraManager`
 * is a final class with a package-private constructor, and its frame planes keep bytes
 * in a `java.io.ByteBuffer` type this runtime no longer exposes.)
 */

interface CameraFrameSource {
    /** Called with the bytes of one picture frame, or null when none can be read. */
    fun requestFrame(onFrame: (ByteArray?) -> Unit)
}

/** The provider for devices that cannot give any frame: every request answers with none. */
class NoCameraFrame : CameraFrameSource {
    override fun requestFrame(onFrame: (ByteArray?) -> Unit) {
        onFrame(null)
    }
}

/**
 * The adapter over the two interfaces (US 01.12.01): the device's frame interface —
 * `ImageAnalysis.Analyzer`, which the camera calls once per frame with an `ImageProxy`
 * — and this app's reader, which takes a flat grey buffer and the frame's own size
 * (`qrTextOfFramePixels`). Between the two it copies the frame's first plane honouring
 * the plane's own row and pixel strides, so a padded row does not skew the picture. A
 * frame that holds no readable code answers nothing, and a plane that cannot be read
 * answers nothing rather than ending the camera session.
 *
 * @param onText Called with the text of each code the frame holds.
 */
class CameraFrameReader(private val onText: (String) -> Unit) : androidx.camera.core.ImageAnalysis.Analyzer {
    override fun analyze(image: androidx.camera.core.ImageProxy) {
        val planes = image.imagePlanes
        if (planes.isEmpty()) {
            return
        }
        val framePixels = frameGreyPixelsOf(planes[0], image.width, image.height)
        if (framePixels == null) {
            return
        }
        val readText = qrTextOfFramePixels(framePixels, image.width, image.height)
        if (readText != null) {
            onText(readText)
        }
    }
}

/**
 * The grey pixels of one frame plane, row after row, in the flat shape the reader takes.
 * The plane keeps its own row and pixel strides, so a pixel is at row × rowStride +
 * column × pixelStride rather than at a packed index; a plane whose buffer does not
 * reach the frame's own size answers with no pixels.
 *
 * @param plane The plane the device handed for this frame.
 * @param frameWidth The width of one row of the frame.
 * @param frameHeight The number of those rows.
 * @return The frame's grey pixels, or null when the plane does not cover them.
 */
private fun frameGreyPixelsOf(
    plane: androidx.camera.common.ImagePlane,
    frameWidth: Int,
    frameHeight: Int,
): ByteArray? {
    if (frameWidth <= 0 || frameHeight <= 0) {
        return null
    }
    val pixels = ByteArray(frameWidth * frameHeight)
    try {
        var written = 0
        for (row in 0 until frameHeight) {
            for (column in 0 until frameWidth) {
                pixels[written] = plane.buffer.get(row * plane.rowStride + column * plane.pixelStride)
                written += 1
            }
        }
    } catch (unreachable: Exception) {
        return null
    }
    return pixels
}

/**
 * The device's analysis node with a frame reader attached, built in one step so a screen
 * only has to bind what it gets. The device runs a frame's work through an executor, and
 * this app runs it where it was handed — reading a code is small and immediate work, and
 * a frame that is not read is only a frame the user did not hold still enough.
 *
 * @param onText Called with the text of each code a frame holds.
 * @return The analysis node to bind to the camera session.
 */
fun cameraFrameAnalysis(onText: (String) -> Unit): androidx.camera.core.ImageAnalysis {
    val analysis = ImageAnalysis.Builder().build()
    analysis.setAnalyzer(RunFrameWhereAsked(), CameraFrameReader(onText))
    return analysis
}

/** Runs the work it is handed on the thread that asked for it, the device's own frame callback. */
private class RunFrameWhereAsked : java.util.concurrent.Executor {
    override fun execute(task: java.lang.Runnable) {
        task.run()
    }
}
