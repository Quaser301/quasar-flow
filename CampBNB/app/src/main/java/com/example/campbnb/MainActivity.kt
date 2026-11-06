package com.example.campbnb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.campbnb.ui.theme.CampBnbTheme
import com.example.campbnb.viewModels.AppViewSettingController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // declare your static lifetime controllers here.
        val appViewSettingsControl = AppViewSettingController()


        setContent {
            CampBnbTheme {
                CampBnbApp(appViewSettingsControl)
            }
        }
    }
}