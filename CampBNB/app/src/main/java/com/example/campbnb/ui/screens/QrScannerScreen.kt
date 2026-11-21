package com.example.campbnb.ui.screens

// AI-assisted (US 01.12.01, scanner screen with the camera-frame affordance): written by Qwen 3.8 Flash
// IQ1_M.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.campbnb.controller.CampsiteController
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.QRCode
import com.example.campbnb.models.qrTextOfImageBytes
import com.example.campbnb.models.qrTextOfImageFile
import com.example.campbnb.ui.shared.CameraFrameSource
import com.example.campbnb.ui.shared.NoCameraFrame
import com.example.campbnb.ui.theme.FieldGrey

/**
 * Camera scanner (US 01.12.01). The frame source is this device's camera: tapping the
 * camera button asks for one frame, and the app reads a code out of the picture it
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
 */
@Composable
fun QrScannerScreen(
    controller: CampsiteController,
    onOpenCampsite: (Campsite) -> Unit = {},
    frameSource: CameraFrameSource = NoCameraFrame()
) {
    var typedCode by remember { mutableStateOf("") }
    var typedImagePath by remember { mutableStateOf("") }
    var scannedText by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    // Text the user typed wins over the last picture this screen read.
    val scannedCode = if (typedCode.isNotBlank()) typedCode else scannedText

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(FieldGrey),
        ) {
            Text(
                text = "Camera view: hold a CampBNB code inside the frame",
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        TextField(
            value = typedCode,
            onValueChange = { typedCode = it },
            label = { Text("Scanned code (type it if the camera cannot read it)") },
            modifier = Modifier.fillMaxSize()
        )

        // The device's own camera: one frame, read for a code.
        Button(
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

        // A picture on this device can be scanned straight from its file.
        TextField(
            value = typedImagePath,
            onValueChange = { typedImagePath = it },
            label = { Text("Image file to scan") },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            enabled = typedImagePath.isNotBlank(),
            onClick = {
                val scanned = qrTextOfImageFile(typedImagePath)
                if (scanned == null) {
                    notice = "That image file holds no readable CampBNB code."
                } else {
                    // The text read out of the picture enters the same flow as typed text.
                    notice = ""
                    scannedText = scanned
                }
            }
        ) {
            Text("Scan Image File")
        }

        Button(
            enabled = scannedCode.isNotBlank(),
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
