package com.example.campbnb

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.campbnb.ui.theme.CampBnbTheme
import com.example.campbnb.viewModels.AppViewSettingController
import com.example.campbnb.notifications.NotificationChannels
import com.example.campbnb.notifications.NotificationRoutes
import com.example.campbnb.notifications.NotificationWatcher
import com.example.campbnb.notifications.SystemNotifier
import com.example.campbnb.ui.shared.DeviceId

/** The Android entry activity: it starts the notification watcher against the signed-in
 * device identity, hosts the Compose app, and routes taps on a notification banner
 * back into the app. */
class MainActivity : ComponentActivity() {
    private val watcher by lazy {
        NotificationWatcher(
            AppContainer.notificationController,
            SystemNotifier(applicationContext)
        )
    }

    // Route to open after the user taps a notification banner. Null when there is none.
    private var pendingRoute by mutableStateOf<String?>(null)

    /** Starts the live side once: platform notification channels, then the app itself,
     *     signed in as the device identity, with the watcher following its notifications. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // declare your static lifetime controllers here.
        val appViewSettingsControl = AppViewSettingController()

        NotificationChannels.createAll(applicationContext) // channels must exist before any notifications are posted

        if (savedInstanceState == null) {
            handleNotificationIntent(intent)
        }
        val deviceId = DeviceId.get(this)

        setContent {
            CampBnbTheme {
                LaunchedEffect(deviceId) {
                    AppContainer.currentUserId = deviceId
                    watcher.start(deviceId)
                }
                CampBnbApp(
                    appViewSettings = appViewSettingsControl,
                    pendingRoute = pendingRoute,
                    onPendingRouteHandled = { pendingRoute = null }
                )
            }
        }
    }
    /** Handles an external intent, such as a tap on a notification banner. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /** A banner tap: marks the notification read and queues its route for the app
     *     to open when the next frame asks for it.
     * @param intent the incoming platform intent; non-intent calls are ignored */
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
        pendingRoute = route
    }

    /** Teardown: stops the notification watcher. */
    override fun onDestroy() {
        watcher.stop()
        super.onDestroy()
    }
}