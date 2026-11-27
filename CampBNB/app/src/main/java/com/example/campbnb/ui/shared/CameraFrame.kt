package com.example.campbnb.ui.shared

// AI-assisted (US 01.12.01, the camera-frame-to-code adapter): written by Qwen 3.8 Flash
// Next Coder IQ1_M.

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageProxy
import androidx.camera.common.ImagePlane
import java.util.concurrent.Executor
import com.example.campbnb.models.qrTextOfFramePixels

/**
 * The adapter over the two interfaces a code read needs (US 01.12.01): the device's frame
 * interface — `ImageAnalysis.Analyzer`, which the camera calls once per frame with an
 * `ImageProxy` — and this app's reader, which takes a flat grey buffer and the frame's
 * own size (`qrTextOfFramePixels`). Screens bind the node this file builds; the camera
 * then reads its own frames as they come, and each code's text answers through a callback
 * the screen supplies.
 *
 * What this platform can and cannot hand back was re-read off the compiler rather than
 * assumed. What resolves, and what the adapter is built from: `ImageAnalysis.Builder().build()`,
 * the `ImageAnalysis.Analyzer` interface, an `ImageProxy` with `imagePlanes`/`width`/`height`,
 * and the planes themselves through `androidx.camera.common.ImagePlane` (`buffer`, `rowStride`,
 * `pixelStride`) — which is why that package is a dependency: those plane types are NOT in
 * camera-core. Barleader's reader also takes a raw buffer, so pixels can be read with no image
 * codec at all. What does NOT resolve: a frame as the BYTES OF AN ENCODED PICTURE — that needs
 * `javax.imageio` over `java.awt`, which is not on the device runtime — so no frame is ever
 * handed around as a picture file here, and `ImageProcessor.Builder` is not a thing that can be
 * built (`ImageProcessor` is an interface with only `process(Request)`). `setAnalyzer` takes a
 * NON-NULL `java.util.concurrent.Executor`, so this app supplies its own one-method executor
 * that runs the frame's work where it was handed. Note the Kotlin builtin packages
 * `java.concurrent`/`java.thread`/`java.function`/`java.pool` are NOT nameable in this toolchain
 * — only jar-qualified names are.
 */

/**
 * The device's frame interface joined to this app's code reader. The camera calls it once
 * per frame; the frame's first plane is copied into the flat grey buffer the reader takes,
 * honouring the plane's own row and pixel strides so a padded row does not skew the picture.
 * A frame that holds no readable code answers nothing, and a plane that cannot be read
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
