package com.example.campbnb

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.example.campbnb.ui.theme.CampBnbTheme
import com.example.campbnb.notifications.NotificationChannels
import com.example.campbnb.notifications.NotificationRoutes
import com.example.campbnb.notifications.NotificationWatcher
import com.example.campbnb.notifications.SystemNotifier
import com.example.campbnb.models.qrPayloadExtraKey
import com.example.campbnb.models.qrRouteOfLinkPayload

class MainActivity : ComponentActivity() {
    private val watcher by lazy {
        NotificationWatcher(
            AppContainer.notificationController,
            SystemNotifier(applicationContext)
        )
    }


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
        // Open the screen the notification points to (for example "chat/{reservationId}").
        AppContainer.pendingRoute = route
    }

    /** A CampBNB code opened by the device's own camera arrives with the code's text in
     * the [qrPayloadExtraKey] extra: resolve it into the route to open (the campsite the
     * code names while it still works, the old-code notice when it is dead). The sign-in
     * above has already run, so the user's data populates before the first screen
     * renders (US 01.05.02). */
    private fun handleQrLinkIntent(intent: Intent?) {
        if (intent == null) return

        val payload = intent.getStringExtra(qrPayloadExtraKey) ?: return

        val route = qrRouteOfLinkPayload(payload)
        if (!route.isNullOrBlank()) {
            AppContainer.pendingRoute = route
        }
    }

    override fun onDestroy() {
        watcher.stop()
        super.onDestroy()
    }
}