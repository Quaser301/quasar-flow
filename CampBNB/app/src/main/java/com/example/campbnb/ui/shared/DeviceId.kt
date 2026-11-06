package com.example.campbnb.ui.shared

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/** Reads this device's id, which identifies the user (US 01.09.01). */
object DeviceId {

    /** The Android id of this device, or an empty string if it cannot be read.
     *
     * @param context Interface for environment information.
     * @param src Lambda function for obtaining the source ID. Can be overriden for testing.
     * */
    @SuppressLint("HardwareIds")
    fun get(
        context: Context,
        src: () -> String = { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }
    ): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: return ""
        return id
    }
}