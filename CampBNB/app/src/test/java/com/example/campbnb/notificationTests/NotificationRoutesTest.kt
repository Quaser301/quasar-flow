
package com.example.campbnb.notificationTests

import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationType
import com.example.campbnb.notifications.NotificationRoutes
import com.example.campbnb.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // The typed twin of the decision above (notifications.md): the SAME arms against the
    // SAME fields, answered as the sealed Screen a tapped notice opens.
    @Test
    fun screenFor_names_the_typed_booking_entry() {
        assertEquals(
            Screen.BookingEntry(lotteryID = "lot-may-2027"),
            NotificationRoutes.screenFor(
                Notification(
                    type = NotificationType.WIN,
                    lotteryId = "lot-may-2027"
                )
            )
        )
        assertEquals(
            Screen.BookingEntry(lotteryID = "lot-may-2027"),
            NotificationRoutes.screenFor(
                Notification(
                    type = NotificationType.INVITE,
                    lotteryId = "lot-may-2027"
                )
            )
        )
    }

    @Test
    fun screenFor_names_the_bridge_for_every_reservation_type() {
        for (type in listOf(
            NotificationType.LOSE,
            NotificationType.OWNER_NOTICE,
            NotificationType.CONFIRMATION,
            NotificationType.CANCELLATION,
            NotificationType.REJECTION
        )) {
            assertTrue(NotificationRoutes.screenFor(Notification(type = type)) is Screen.NotifReservations)
        }
    }

    @Test
    fun screenFor_names_the_typed_chat_bridge() {
        assertEquals(
            Screen.ChatReservationEntry(reservationID = "res-9"),
            NotificationRoutes.screenFor(
                Notification(
                    type = NotificationType.MESSAGE,
                    reservationId = "res-9"
                )
            )
        )
    }

    @Test
    fun screenFor_falls_back_to_the_views_it_names() {
        assertTrue(NotificationRoutes.screenFor(Notification(type = NotificationType.WIN)) is Screen.Notifications)
        assertTrue(NotificationRoutes.screenFor(Notification(type = NotificationType.INVITE)) is Screen.Notifications)
        assertTrue(NotificationRoutes.screenFor(Notification(type = NotificationType.MESSAGE)) is Screen.Messages)
    }

    // NOTE (finding, 2026-11-24): notifications.md also documents the INVERSE decode
    // `screenFromIntentText(intentRoute: String): Screen?` — the named texts read back
    // to their screens, unknown text names nothing — and states the two forms never
    // disagree (`screenFromIntentText(routeFor(n))` is `screenFor(n)`). That public name
    // is NOT reachable from the JVM in this tree (not the object member, not a package
    // nor root-level function): the implementation's decode stays private at its call
    // site. Untested until the spec'd surface lands; NOT guessed to an alternate name.
}
