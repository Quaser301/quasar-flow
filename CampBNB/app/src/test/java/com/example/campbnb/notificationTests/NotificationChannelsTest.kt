package com.example.campbnb.notificationTests

import com.example.campbnb.models.NotificationType
import com.example.campbnb.notifications.NotificationChannels
import org.junit.Assert
import org.junit.Test

class NotificationChannelsTest {
    @Test
    fun lottery_results_share_one_channel() {
        Assert.assertEquals(NotificationChannels.LOTTERY_RESULTS, NotificationChannels.channelFor(
            NotificationType.WIN))
        Assert.assertEquals(NotificationChannels.LOTTERY_RESULTS, NotificationChannels.channelFor(
            NotificationType.LOSE))
    }

    @Test
    fun every_type_has_own_channel() {
        NotificationType.values().forEach {
            Assert.assertTrue(NotificationChannels.channelFor(it).isNotBlank())
        }
    }

    @Test
    fun reservation_notifications_share_one_channel() {
        Assert.assertEquals(
            NotificationChannels.RESERVATIONS,
            NotificationChannels.channelFor(NotificationType.CONFIRMATION)
        )

        Assert.assertEquals(
            NotificationChannels.RESERVATIONS,
            NotificationChannels.channelFor(NotificationType.CANCELLATION)
        )

        Assert.assertEquals(
            NotificationChannels.RESERVATIONS,
            NotificationChannels.channelFor(NotificationType.REJECTION)
        )
    }

    @Test
    fun messages_use_messages_channel() {
        Assert.assertEquals(
            NotificationChannels.MESSAGES,
            NotificationChannels.channelFor(NotificationType.MESSAGE)
        )
    }

    @Test
    fun owner_notices_use_general_channel() {
        Assert.assertEquals(
            NotificationChannels.GENERAL,
            NotificationChannels.channelFor(NotificationType.OWNER_NOTICE)
        )
    }

}