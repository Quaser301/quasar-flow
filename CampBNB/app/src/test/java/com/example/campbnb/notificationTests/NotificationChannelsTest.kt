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

    @Test
    fun invitations_use_their_own_channel() {
        Assert.assertEquals(
            NotificationChannels.LOTTERY_INVITES,
            NotificationChannels.channelFor(NotificationType.INVITE)
        )
    }

    // The channel names are the exact wire strings the OS sees, not only the constants.
    @Test
    fun channel_names_are_the_exact_wire_values() {
        Assert.assertEquals("lottery_results", NotificationChannels.channelFor(
            NotificationType.WIN))
        Assert.assertEquals("lottery_results", NotificationChannels.channelFor(
            NotificationType.LOSE))
        Assert.assertEquals("reservations", NotificationChannels.channelFor(
            NotificationType.CONFIRMATION))
        Assert.assertEquals("reservations", NotificationChannels.channelFor(
            NotificationType.CANCELLATION))
        Assert.assertEquals("reservations", NotificationChannels.channelFor(
            NotificationType.REJECTION))
        Assert.assertEquals("messages", NotificationChannels.channelFor(
            NotificationType.MESSAGE))
        Assert.assertEquals("general", NotificationChannels.channelFor(
            NotificationType.OWNER_NOTICE))
        Assert.assertEquals("lottery_invites", NotificationChannels.channelFor(
            NotificationType.INVITE))
    }

    @Test
    fun blank_record_still_has_a_channel() {
        // The map is TOTAL: a type is enough, no record fields are consulted.
        NotificationType.values().forEach { type ->
            val channel = NotificationChannels.channelFor(type)
            Assert.assertTrue(channel in listOf(
                NotificationChannels.LOTTERY_RESULTS,
                NotificationChannels.RESERVATIONS,
                NotificationChannels.MESSAGES,
                NotificationChannels.GENERAL,
                NotificationChannels.LOTTERY_INVITES
            ))
        }
    }
}