package com.example.campbnb.notificationTests

import com.example.campbnb.models.NotificationType
import com.example.campbnb.notifications.NotificationChannels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationChannelsTest {
    @Test
    fun lottery_results_share_one_channel() {
        assertEquals(NotificationChannels.LOTTERY_RESULTS, NotificationChannels.channelFor(
            NotificationType.WIN))
        assertEquals(NotificationChannels.LOTTERY_RESULTS, NotificationChannels.channelFor(
            NotificationType.LOSE))
    }

    @Test
    fun every_type_has_own_channel() {
        NotificationType.values().forEach {
            assertTrue(NotificationChannels.channelFor(it).isNotBlank())
        }
    }

    @Test
    fun reservation_notifications_share_one_channel() {
        assertEquals(
            NotificationChannels.RESERVATIONS,
            NotificationChannels.channelFor(NotificationType.CONFIRMATION)
        )

        assertEquals(
            NotificationChannels.RESERVATIONS,
            NotificationChannels.channelFor(NotificationType.CANCELLATION)
        )

        assertEquals(
            NotificationChannels.RESERVATIONS,
            NotificationChannels.channelFor(NotificationType.REJECTION)
        )
    }

    @Test
    fun messages_use_messages_channel() {
        assertEquals(
            NotificationChannels.MESSAGES,
            NotificationChannels.channelFor(NotificationType.MESSAGE)
        )
    }

    @Test
    fun owner_notices_use_general_channel() {
        assertEquals(
            NotificationChannels.GENERAL,
            NotificationChannels.channelFor(NotificationType.OWNER_NOTICE)
        )
    }

}