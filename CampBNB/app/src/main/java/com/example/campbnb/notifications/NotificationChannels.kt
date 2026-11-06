package com.example.campbnb.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.campbnb.models.NotificationType

/**
 * Defines the Android notification channels and decides which channel each NotificationType uses
 */

object NotificationChannels {
    // Channel IDs: internal names never show to user
    const val LOTTERY_RESULTS = "lottery_results"
    const val RESERVATIONS = "reservations"
    const val MESSAGES = "messages"
    const val GENERAL = "general"

    /** which channel a notification of [type] is posted on */
    fun channelFor(type: NotificationType): String = when (type) {
        NotificationType.WIN, NotificationType.LOSE -> LOTTERY_RESULTS

        NotificationType.CONFIRMATION,
        NotificationType.CANCELLATION,
        NotificationType.REJECTION -> RESERVATIONS

        NotificationType.MESSAGE -> MESSAGES
        NotificationType.OWNER_NOTICE -> GENERAL
    }

    /**
     * register all channels so it's safe to call everytime the app starts
     */
    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    LOTTERY_RESULTS,
                    "Lottery results",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Tells you whether you won or lost a campsite lottery" },
                NotificationChannel(
                    RESERVATIONS,
                    "Reservations",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                    .apply { description = "Confirmations, cancellations and rejections" },
                NotificationChannel(MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "New messages from campsite owners and entrants" },
                NotificationChannel(GENERAL, "General", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Other notices from campsite owners" }
            )
        )
    }
}


