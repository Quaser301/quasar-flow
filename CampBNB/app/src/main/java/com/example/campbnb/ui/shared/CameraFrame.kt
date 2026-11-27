package com.example.campbnb.ui.shared

// AI-assisted (US 01.12.01, camera-frame provider seam): written by Qwen 3.8 Flash Next Coder
// IQ1_M.

/**
 * Provider: what this device's camera sees (US 01.12.01). Screens ask one frame here
 * instead of touching the device; a frame is the bytes of a real picture, which is
 * what reading a code out of a picture needs (qrTextOfImageBytes). A device that
 * gives no frame answers with null and the scanner keeps working on what the user
 * typed. A test provider hands back a chosen frame, the way DeviceId lets a test
 * replace its source.
 *
 * This platform, so far, can only hand back no frame of bytes, and that was re-read
 * off the compiler rather than assumed: what resolves here is the live-preview path
 * (`Preview.Builder`, `CameraSelector.of`, `ImageProcessor.Builder`) — what does NOT is
 * anything that could produce an analyzed frame: `androidx.camera.core.ImageAnalysis`
 * names a class but its nested `Builder` does not resolve, `androidx.camera.core.ImageSource`
 * is absent, and neither `processWithProcessor` nor `attachImageProcessor` is a member.
 * So no frame can be started from this app's camera session, which is the whole reason
 * the seam stays frameless on the device. (The older dead path, kept for history: the
 * camera2 `CameraManager` is a final class with a package-private constructor, and its
 * frame planes keep bytes in a `java.io.ByteBuffer` type this runtime no longer exposes.)
 * A QR DECODER over pixels is separately absent too — Barleader's `BarcodeReader.read`
 * takes only the unnameable `BufferedImage!` that `javax.imageio.ImageIO` returns, and
 * that runtime is not on the device — so a frame's bytes could not be read even if one
 * could be had. A real device provider belongs where NoCameraFrame is used below; the
 * frame reader and the screens are written so that adding one changes nothing else.
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
