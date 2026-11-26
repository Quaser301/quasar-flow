package com.example.campbnb

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import android.Manifest
import com.example.campbnb.ui.theme.CampBnbTheme
import com.example.campbnb.notifications.NotificationChannels
import com.example.campbnb.notifications.NotificationRoutes
import com.example.campbnb.notifications.NotificationWatcher
import com.example.campbnb.notifications.SystemNotifier
import com.example.campbnb.models.qrPayloadExtraKey
import com.example.campbnb.models.qrScreenOfLinkPayload
import com.example.campbnb.notifications.ConfirmationCsvDelivery

class MainActivity : ComponentActivity() {
    private val watcher by lazy {
        NotificationWatcher(
            AppContainer.notificationController,
            SystemNotifier(applicationContext),
            // Confirmation auto-download for entrants who chose CSV.
            ConfirmationCsvDelivery(
                AppContainer.notificationPreferencesService,
                AppContainer.reservationRepository,
                AppContainer.campsiteRepository,
                AppContainer.fileDownloadWriter,
                onSaved = { fileName ->
                    // The listener may call back off the main thread, so hop to it.
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Reservation confirmed. Saved $fileName to your Downloads folder",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        )
    }

    // Names this app's one permission ask, so the answer the device sends back is
    // matched to the ask it belongs to.
    private val cameraAnswerId = 1

    // The device answers with one result per permission it was asked about, and this
    // is the value that means the user allowed it. The device names this constant in
    // its own permission-result class, which this platform's stubs do not carry.
    private val permissionAllowed = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // channels must exist before any notifications are posted
        NotificationChannels.createAll(applicationContext)

        // run Dependency INIT
        AppContainer.init(applicationContext)

        if (savedInstanceState == null) {
            handleNotificationIntent(intent)
            handleQrLinkIntent(intent)
        }

        // The scanner screen needs the device's camera, and only this app can ask the
        // device to let it be used: the ask belongs to the activity the app runs on,
        // not to a screen. It is asked once, at start, and the answer is carried as a
        // fact the scanner reads, so the screen opens its camera when the answer says
        // yes and stays on its typed and file paths when it says no.
        requestPermissions(arrayOf(android.Manifest.permission.CAMERA), cameraAnswerId)

        setContent {
            CampBnbTheme {
                LaunchedEffect(AppContainer.deviceID) {
                    watcher.start(AppContainer.deviceID)
                }
                CampBnbApp(
                    deps = AppContainer
                )
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        handleQrLinkIntent(intent)
    }

    /** The answer to the camera ask above: the device calls back with what the user
     * chose, and the app carries it as a fact the scanner reads. The default answer
     * still runs, so the dialog the device shows is the device's own. */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraAnswerId) {
            AppContainer.cameraAllowed = grantResults.all { outcome ->
                outcome == permissionAllowed
            }
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return

        val route = intent.getStringExtra(
            NotificationRoutes.EXTRA_ROUTE
        ) ?: return

        val notificationId = intent.getStringExtra(
            NotificationRoutes.EXTRA_NOTIFICATION_ID
        )

        if (!notificationId.isNullOrBlank()) {
            AppContainer.notificationController.markRead(notificationId)
        }
        // Open the screen the notification points to: the Intent's route text is
        // written by SystemNotifier and decodes back to a typed Screen route here,
        // so the app graph navigates a real screen and a renamed screen breaks the
        // decode at compile time instead of opening a dead string route.
        val screen = NotificationRoutes.screenFromIntentText(route)
        if (screen != null) {
            AppContainer.pendingScreen = screen
        }
    }

    /** A CampBNB code opened by the device's own camera arrives with the code's text in
     * the [qrPayloadExtraKey] extra: resolve it into the route to open (the campsite the
     * code names while it still works, the old-code notice when it is dead). The sign-in
     * above has already run, so the user's data populates before the first screen
     * renders (US 01.05.02). */
    private fun handleQrLinkIntent(intent: Intent?) {
        if (intent == null) return

        val payload = intent.getStringExtra(qrPayloadExtraKey) ?: return

        val screen = qrScreenOfLinkPayload(payload)
        if (screen != null) {
            AppContainer.pendingScreen = screen
        }
    }

    override fun onDestroy() {
        watcher.stop()
        super.onDestroy()
    }
}