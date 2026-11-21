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
 * This platform, so far, can only hand back no frame: its camera controller
 * (android.hardware.camera2.CameraManager) is a final class whose constructor is
 * package-private, so no Kotlin here can start one, and a captured frame plane
 * (android.media.Image.Plane) keeps its bytes in java.io.ByteBuffer, a buffer type
 * this platform's runtime no longer exposes, so no bytes could be copied out even
 * if a camera could be started. Both facts were read off the platform itself by
 * letting it compile. A real device provider belongs where NoCameraFrame is used
 * below; the frame reader and the screens are written so that adding one changes
 * nothing else.
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
