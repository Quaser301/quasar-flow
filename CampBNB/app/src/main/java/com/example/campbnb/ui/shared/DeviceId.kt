package com.example.campbnb.ui.shared

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/** Reads this device's id, which identifies the user (US 01.09.01). */
object DeviceId {

    /** The Android id of this device, or an empty string if it cannot be read. */
    @SuppressLint("HardwareIds")
    fun get(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (id == null) {
            return ""
        }
        return id
    }
}