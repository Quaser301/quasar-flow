package com.example.campbnb.ui.screens

// AI-assisted (US 01.12.01, scanner screen with the camera-frame affordance): written by Qwen 3.8 Flash
// IQ1_M.

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.rememberLifecycleOwner
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.QRCode
import com.example.campbnb.models.qrTextOfImageBytes
import com.example.campbnb.ui.shared.CameraFrameSource
import com.example.campbnb.ui.shared.NoCameraFrame
import com.example.campbnb.ui.theme.FieldGrey

/**
 * Camera scanner (US 01.12.01). When this device's camera can be opened, the frame
 * area shows what the camera sees, live (US 01.05.02, live scanner); what the camera
 * sees still never comes back here as byte frames, so the code itself arrives
 * through the typed text or the picture-file paths. Tapping the camera button asks
 * the frame source for one frame, and the app reads a code out of the picture it
 * gets back. A device with no camera, or a camera that cannot be opened, says so and
 * the screen keeps working on what the user typed. Text a scan produced can also be
 * typed into the field, and a picture on this device can be scanned straight from its
 * file. Whichever way a code is read, a working code opens its campsite through
 * [onOpenCampsite]; a code that stopped working, text or an image that is not a
 * CampBNB code, says so here and stays on this screen so the next scan starts from
 * it. Going back from an opened campsite returns to this screen because the navigation
 * stack keeps it under the details page.
 *
 * @param controller The controller that reads the code and loads the named campsite.
 * @param onOpenCampsite Called with the campsite a working code names; the app opens
 *   its details page.
 * @param frameSource Where the next camera frame comes from; NoCameraFrame keeps the
 *   screen on typed text when this device gives no frames.
 * @param cameraRefused Whether the device said NO to the app using its camera. Only
 *   the app can ask the device, so a refusal arrives here as a fact: true keeps the
 *   frame area on its placeholder and the screen on its typed path. A device that
 *   never answers is not a refusal, so its camera is opened.
 */
@Composable
fun QrScannerScreen(
    controller: CampsiteController,
    onOpenCampsite: (Campsite) -> Unit = {},
    frameSource: CameraFrameSource = NoCameraFrame(),
    cameraRefused: Boolean = false
) {
    var typedCode by remember { mutableStateOf("") }
    var scannedText by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var cameraSurface by remember { mutableStateOf<androidx.camera.core.SurfaceRequest?>(null) }

    // One camera session per screen opening (US 01.05.02, live scanner): the preview
    // surface arrives through the surface-request callback and is shown as it comes.
    // The device only lets a camera start once it has allowed the app to use it, and
    // only the app itself can ask — so the app asked at start and only a refusal
    // arrives here as [cameraRefused]. The camera opens unless the device said no,
    // and the effect re-runs when that answer arrives, so the preview starts as soon
    // as the user has allowed it. A device that shows no dialog and never answers is
    // not a refusal: its camera is opened, and if it cannot start, the failure below
    // says so. This device's camera path hands no byte frames, so the frame-reading
    // flow below is untouched and still reads typed text.
    val context = LocalContext.current
    val lifecycleOwner = rememberLifecycleOwner()

    fun openCamera() {
        try {
            val cameraProvider = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context).get()
            cameraProvider.unbindAll()
            val preview = androidx.camera.core.Preview.Builder().build()
            preview.setSurfaceProvider(CameraSurfaceKeeper { surfaceRequest -> cameraSurface = surfaceRequest })
            // The device's OWN camera, not a lens this app assumes: DEFAULT_BACK_CAMERA asks
            // for a back-facing lens, and a device whose camera reports no lens facing at
            // all (a chromebook's camera answers with no lens category) matches no lens
            // filter, so the bind throws "No available camera can be found" while the
            // device still has a camera. The first camera the device itself lists is the
            // one that can be opened, and naming it by its own identifier asks for no
            // lens at all.
            val listedCameras = cameraProvider.availableCameraInfos
            val deviceLens = listedCameras.firstOrNull()?.cameraIdentifier
            if (deviceLens == null) {
                notice = "This device lists no camera that can be opened. Type the code instead."
            } else {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    androidx.camera.core.CameraSelector.of(deviceLens),
                    preview
                )
            }
        } catch (failure: Exception) {
            // A camera that cannot be opened is LOUD rather than silent: the placeholder
            // alone tells the user nothing about why the preview never came, and the
            // device's own reason is the next thing they can act on. The typed path
            // keeps the scanner working.
            notice = "The camera could not open: ${failure.message}"
        }
    }

    LaunchedEffect(lifecycleOwner, cameraRefused) {
        if (!cameraRefused) {
            openCamera()
        }
    }

    // Text the user typed wins over the last picture this screen read.
    val scannedCode = if (typedCode.isNotBlank()) typedCode else scannedText

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The frame area is bounded, not screen-filling: a child that fills the whole
        // screen leaves no room for the fields and the buttons below it, and they are
        // pushed off a screen the user can still scroll to nothing. The height matches
        // the picture boxes elsewhere in the app.
        val liveSurface = cameraSurface
        if (liveSurface != null) {
            CameraXViewfinder(
                surfaceRequest = liveSurface,
                modifier = Modifier.fillMaxWidth().height(240.dp),
                implementationMode = androidx.camera.viewfinder.core.ImplementationMode.EMBEDDED
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(240.dp).background(FieldGrey),
            ) {
                Text(
                    text = "Camera view: hold a CampBNB code inside the frame",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        TextField(
            value = typedCode,
            onValueChange = { typedCode = it },
            label = { Text("Scanned code (type it if the camera cannot read it)") },
            modifier = Modifier.testTag("qr_code_field").fillMaxWidth()
        )

        // The device's own camera: one frame, read for a code.
        Button(
            modifier = Modifier.testTag("camera_scan"),
            onClick = {
                frameSource.requestFrame { frame ->
                    if (frame == null) {
                        notice = "This device gave no camera frame. Type the code instead."
                    } else {
                        val readFromFrame = qrTextOfImageBytes(frame)
                        if (readFromFrame == null) {
                            notice = "The camera frame holds no readable CampBNB code."
                        } else {
                            notice = ""
                            scannedText = readFromFrame
                        }
                    }
                }
            }
        ) {
            Text("Scan With Camera")
        }

        Button(
            enabled = scannedCode.isNotBlank(),
            modifier = Modifier.testTag("open_scanned_code"),
            onClick = {
                controller.scanQr(
                    payload = scannedCode,
                    onOpened = { campsite ->
                        notice = ""
                        onOpenCampsite(campsite)
                    },
                    onExpired = { stopped ->
                        notice = "That code stopped working. Get a current code."
                    },
                    onUnreadable = { text ->
                        notice = "That text is not a CampBNB code."
                    }
                )
            }
        ) {
            Text("Open Campsite")
        }

        if (notice.isNotBlank()) {
            Text(text = notice)
        }
    }
}

/** Hands the preview surface to the screen: the camera calls back once with the
 * surface request, and the screen shows that surface as it arrives. */
private class CameraSurfaceKeeper(private val onSurfaceSeen: (androidx.camera.core.SurfaceRequest) -> Unit) :
    androidx.camera.core.Preview.SurfaceProvider {
    override fun onSurfaceRequested(surfaceRequest: androidx.camera.core.SurfaceRequest) {
        onSurfaceSeen(surfaceRequest)
    }
}
