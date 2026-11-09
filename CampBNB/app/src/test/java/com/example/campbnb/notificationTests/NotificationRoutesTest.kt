
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

    @Test
    fun cancellation_rejection_and_owner_notice_open_reservations() {
        assertEquals(
            "reservations",
            NotificationRoutes.routeFor(Notification(type = NotificationType.CANCELLATION))
        )
        assertEquals(
            "reservations",
            NotificationRoutes.routeFor(Notification(type = NotificationType.REJECTION))
        )
        assertEquals(
            "reservations",
            NotificationRoutes.routeFor(Notification(type = NotificationType.OWNER_NOTICE))
        )
    }

    @Test
    fun message_without_a_reservation_opens_the_plain_messages_screen() {
        assertEquals(
            "messages",
            NotificationRoutes.routeFor(Notification(type = NotificationType.MESSAGE))
        )
    }

    @Test
    fun an_awkward_reservation_id_reaches_the_route_untouched() {
        assertEquals(
            "chat/res 9/\"x\"",
            NotificationRoutes.routeFor(
                Notification(type = NotificationType.MESSAGE, reservationId = "res 9/\"x\"")
            )
        )
    }
}
