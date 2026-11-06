
package com.example.campbnb.notificationTests

import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationType
import com.example.campbnb.notifications.NotificationRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRoutesTest {

    @Test
    fun win_opens_booking_for_that_lottery() {
        val n = Notification(
            type = NotificationType.WIN,
            lotteryId = "lot-may-2027"
        )
        assertEquals("booking/lot-may-2027", NotificationRoutes.routeFor(n))
    }

    @Test
    fun win_without_lottery_id_falls_back_to_inbox() {
        assertEquals(
            "notifications",
            NotificationRoutes.routeFor(Notification(type = NotificationType.WIN))
        )
    }

    @Test
    fun lose_opens_reservations() {
        assertEquals(
            "reservations",
            NotificationRoutes.routeFor(Notification(type = NotificationType.LOSE))
        )
    }

    @Test
    fun message_opens_that_chat() {
        val n = Notification(
            type = NotificationType.MESSAGE,
            reservationId = "res-9"
        )
        assertEquals("chat/res-9", NotificationRoutes.routeFor(n))
    }

    @Test
    fun confirmation_opens_reservations() {
        assertEquals(
            "reservations",
            NotificationRoutes.routeFor(Notification(type = NotificationType.CONFIRMATION))
        )
    }
}
