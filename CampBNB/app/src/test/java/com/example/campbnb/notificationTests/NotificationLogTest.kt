package com.example.campbnb.notificationTests

/**
 * NOTICE: This file was AI-assisted. The verbatim prompt for this work is recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 *
 * The four tests follow the request made in that prompt: navigation works throughout
 * (the back button and opening one dropdown leaves the others closed), no inputs shows
 * no log entries at all, and many inputs are grouped newest-first with correct
 * recipient counts and their placeholder bodies kept exactly as written. The screen
 * keeps its behaviour in the [NotificationLogDisplayController] state and the
 * notificationLogGroupsFor grouping model, which is what these tests drive.
 */

import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationType
import com.example.campbnb.ui.notification.logKeyOf
import com.example.campbnb.ui.notification.notificationLogGroupsFor
import com.example.campbnb.ui.notification.notificationLogProfileAt
import com.example.campbnb.ui.notification.summaryLine
import com.example.campbnb.viewModels.NotificationLogDisplayController
import com.google.firebase.Timestamp
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLogTest {

    @Test
    fun test_BackGoesBackByCallingThePassedInLambda() {
        var wentBack = false
        val display = NotificationLogDisplayController({ wentBack = true })

        display.pressBack()

        assertTrue("the back press must run the back lambda it was handed", wentBack)
    }

    @Test
    fun test_ATapOpensOnlyThatLogDropdown() {
        val display = NotificationLogDisplayController({})

        display.toggleGroup("log-a")

        assertTrue(
            "the tapped dropdown opens",
            ("\nlog-a\n") in display.openedLogNames().value
        )
        assertTrue(
            "the other dropdowns stay closed",
            !(("\nlog-b\n") in display.openedLogNames().value)
        )

        display.toggleGroup("log-a")
        assertTrue(
            "tapping again closes it",
            !(("\nlog-a\n") in display.openedLogNames().value)
        )
    }

    @Test
    fun test_WithNoLogsThereAreNoEntriesToShow() {
        val logs = notificationLogGroupsFor(emptyList())

        assertTrue(
            "no saved records means no log entries, so the screen takes its centered message branch",
            logs.count() == 0
        )
    }

    @Test
    fun test_ManyRecordsGroupIntoCountedEntriesNewestFirst() {
        // one system broadcast of forty winners, its placeholders kept as written.
        val winners = (1..40).map { position ->
            Notification(
                recipientId = "camper-$position",
                type = NotificationType.WIN,
                title = "You won!",
                message = "Hello <Name>, You were selected!",
                lotteryId = "lot-l1",
                sentAt = Timestamp(1600000100L, 0)
            )
        }
        // one owner notice to twenty-five entrants, saved earlier.
        val notices = (1..25).map { position ->
            Notification(
                recipientId = "camper-notice-$position",
                senderId = "owner-9",
                type = NotificationType.OWNER_NOTICE,
                title = "Camp closing",
                message = "The camp closes Friday.",
                campsiteName = "Pine Ridge",
                sentAt = Timestamp(1600000000L, 0)
            )
        }
        // one late message pair to five entrants, the newest record of all.
        val messages = (1..5).map { position ->
            Notification(
                recipientId = "camper-msg-$position",
                senderId = "camper-7",
                type = NotificationType.MESSAGE,
                title = "New message",
                message = "See you at the gate.",
                reservationId = "res-3",
                sentAt = Timestamp(1600000200L, 0)
            )
        }

        val logs = notificationLogGroupsFor(winners + notices + messages)

        assertTrue("three broadcasts make three log entries", logs.count() == 3)
        assertTrue("the newest broadcast is first", logs[0].title == "New message")
        assertTrue("its sender is the user who sent it", logs[0].senderId == "camper-7")
        assertTrue("the winner broadcast comes second", logs[1].title == "You won!")
        assertTrue("the oldest broadcast comes last", logs[2].title == "Camp closing")

        assertTrue("the winner entry counts every receiver", logs[1].recipientIds.count() == 40)
        assertTrue("the notice entry counts its twenty-five receivers", logs[2].recipientIds.count() == 25)
        assertTrue("the message entry counts its five receivers", logs[0].recipientIds.count() == 5)

        assertTrue(
            "placeholders in a notification body are kept exactly as written",
            logs[1].message == "Hello <Name>, You were selected!"
        )
        assertTrue(
            "the summary line carries sender, count and timestamp in order",
            "sent by the system" in summaryLine(logs[1]) &&
                    "40 recipient(s)" in summaryLine(logs[1]) &&
                    !("no saved" in summaryLine(logs[1]))
        )

        assertTrue("the second dropdown can scroll to any receiver", notificationLogProfileAt(logs[1], 39) == "camper-40")
        assertTrue("a row past the end is empty", notificationLogProfileAt(logs[1], 40) == "")
    }
}
