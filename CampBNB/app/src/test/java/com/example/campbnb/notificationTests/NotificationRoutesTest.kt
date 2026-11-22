
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
    fun invite_opens_that_lottery() {
        val n = Notification(
            type = NotificationType.INVITE,
            lotteryId = "lot-may-2027"
        )
        assertEquals("booking/lot-may-2027", NotificationRoutes.routeFor(n))
    }

    @Test
    fun invite_without_lottery_id_falls_back_to_inbox() {
        assertEquals(
            "notifications",
            NotificationRoutes.routeFor(Notification(type = NotificationType.INVITE))
        )
    }

    @Test
    fun lose_needs_no_lottery_id_and_opens_reservations() {
        assertEquals(
            "reservations",
            NotificationRoutes.routeFor(
                Notification(
                    type = NotificationType.LOSE,
                    lotteryId = "lot-may-2027"
                )
            )
        )
    }

    @Test
    fun owner_notice_and_cancellation_and_rejection_open_reservations() {
        assertEquals(
            "reservations",
            NotificationRoutes.routeFor(Notification(type = NotificationType.OWNER_NOTICE))
        )
        assertEquals(
            "reservations",
            NotificationRoutes.routeFor(Notification(type = NotificationType.CANCELLATION))
        )
        assertEquals(
            "reservations",
            NotificationRoutes.routeFor(Notification(type = NotificationType.REJECTION))
        )
    }

    @Test
    fun message_without_a_reservation_opens_the_messages_tab() {
        assertEquals(
            "messages",
            NotificationRoutes.routeFor(Notification(type = NotificationType.MESSAGE))
        )
    }
}
