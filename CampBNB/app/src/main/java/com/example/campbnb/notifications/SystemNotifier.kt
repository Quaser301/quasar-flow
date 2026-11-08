package com.example.campbnb.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.campbnb.MainActivity
import com.example.campbnb.R
import com.example.campbnb.models.Notification

/** Delivers an in-app notification to the platform banner: the banner channel follows the
 * notification type, and tapping the banner routes back into the app through
 * [NotificationRoutes]. */
class SystemNotifier(private val context: Context) {
    /** Posts one platform banner for the notification; does nothing when the
     *     permission is not granted. */
    @SuppressLint("MissingPermission")
    fun show(n: Notification) {
        if (!NotificationPermission.isGranted(context)) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationRoutes.EXTRA_ROUTE, NotificationRoutes.routeFor(n))
            putExtra(NotificationRoutes.EXTRA_NOTIFICATION_ID, n.id)
        }

        val uniqueNumber = n.id.hashCode()

        val pending = PendingIntent.getActivity(
            context, uniqueNumber, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val banner = NotificationCompat.Builder(
            context,
            NotificationChannels.channelFor(n.type)
        )
            .setSmallIcon(R.drawable.ic_stat_campsite)
            .setContentTitle(n.title)
            .setContentText(n.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.message))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(uniqueNumber, banner)
    }
}