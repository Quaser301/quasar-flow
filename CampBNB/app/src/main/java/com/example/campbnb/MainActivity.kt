package com.example.campbnb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.campbnb.ui.theme.CampBnbTheme
import com.example.campbnb.viewModels.AppViewSettingController
import com.example.campbnb.notifications.NotificationChannels

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appViewSettingsControl = AppViewSettingController()
        NotificationChannels.createAll(applicationContext) // channels must exist before any notifications are posted
        setContent {
            CampBnbTheme {
                CampBnbApp(appViewSettingsControl)
            }
        }
    }
}