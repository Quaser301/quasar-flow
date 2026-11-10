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

    override fun onDestroy() {
        watcher.stop()
        super.onDestroy()
    }
}