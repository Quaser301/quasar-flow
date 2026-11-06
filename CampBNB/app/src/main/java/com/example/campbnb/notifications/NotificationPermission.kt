package com.example.campbnb.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * checks whether the app may post notifications, and shows a card that asks for permission or sends the user to the system settings
 */

object NotificationPermission {
    /** true when app is allowed to show notifs */
    fun isGranted(context: Context): Boolean {
        return NotificationManagerCompat
            .from(context)
            .areNotificationsEnabled()
    }
    /** opens apps notification page in settings */
    fun openSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(
                    Settings.EXTRA_APP_PACKAGE,
                    context.packageName
                )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/**
 * a card that explains why notifications matter and asks for permission.
 * Draws nothing at all once permission is granted
 */
@Composable
fun NotificationPermissionCard(modifier: Modifier = Modifier) {

    val context = LocalContext.current

    var granted by remember {
        mutableStateOf(NotificationPermission.isGranted(context))
    }
    var deniedOnce by remember {
        mutableStateOf(false)
    }

    val canShowPrompt =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !deniedOnce

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        granted = allowed
        if (!allowed) deniedOnce = true
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = NotificationPermission.isGranted(context)
    }
    if (granted) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                "Turn on notifications",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "We will tell you the moment you win or lose a lottery, even when the app is in the background. Without this you will only see results inside the app.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (canShowPrompt) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        NotificationPermission.openSettings(context)
                    }
                }
            ) {
                Text(
                    if (canShowPrompt) "Turn on"
                    else "Open notification settings"
                )
            }
        }
    }
}