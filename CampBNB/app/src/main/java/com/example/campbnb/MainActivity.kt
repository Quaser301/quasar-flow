package com.example.campbnb

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.example.campbnb.ui.theme.CampBnbTheme
import com.example.campbnb.viewModels.AppViewSettingController
import com.example.campbnb.notifications.NotificationChannels
import com.example.campbnb.notifications.NotificationRoutes
import com.example.campbnb.notifications.NotificationWatcher
import com.example.campbnb.notifications.SystemNotifier
import com.example.campbnb.ui.shared.DeviceId

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
                CampBnbApp(appViewSettingsControl)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
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
    }

    override fun onDestroy() {
        watcher.stop()
        super.onDestroy()
    }
}