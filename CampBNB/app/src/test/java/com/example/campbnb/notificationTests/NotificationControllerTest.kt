package com.example.campbnb.notificationTests

import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.OptOutChecker
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.Message
import com.example.campbnb.models.Notification
import com.google.firebase.Timestamp
import com.example.campbnb.models.NotificationIntent
import com.example.campbnb.models.NotificationType
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.ReservationStatus
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class NotificationControllerTest {
    private lateinit var repo: FakeNotificationRepository
    private lateinit var controller: NotificationController

    @Before
    fun setup() {
        repo = FakeNotificationRepository()
        controller = NotificationController(repo)
    }

    private fun <T> result(block: ((Result<T>) -> Unit) -> Unit): Result<T> {
        var captured: Result<T>? = null
        block { captured = it }
        return captured!!
    }

    /** The reservation every reservation-event case describes. */
    private fun reservedByentrant(ownerId: String, entrantId: String) = ReservationModel(
        lotteryEntryId = "entry1",
        campsiteId = "camp1",
        entrantId = entrantId,
        ownerId = ownerId,
        guests = 2,
        dates = listOf("2026-12-01", "2026-12-02"),
        startDate = "2026-12-01",
        endDate = "2026-12-02",
        status = ReservationStatus.ACTIVE,
        refundRecorded = false,
        createdAt = 1000L,
        rules = true
    )

    @Test
    fun text_campsite_and_say_action_needed() {
        result<Int> { done ->
            controller.notify(
                NotificationIntent.LotteryDrawn("lot1", "Banff", listOf("u1"), emptyList()),
                done
            )
        }
        val n = repo.saved.values.single()
        Assert.assertTrue(n.title.contains("Banff"))
        Assert.assertTrue(n.message.contains("Banff"))
        Assert.assertTrue(n.message.contains("Action needed"))
    }

    @Test
    fun duplicate_and_black_ids_ignored() {
        val r = result<Int> { done ->
            controller.notify(
                NotificationIntent.LotteryDrawn(
                    "lot1", "Hinton", listOf("u1", "u1", " ", " "), emptyList()
                ),
                done
            )
        }
        Assert.assertEquals(1, r.getOrNull())
        Assert.assertEquals(1, repo.saved.size)
    }

    @Test
    fun running_same_draw_twice() {
        val ids = listOf("u1", "u2")
        result<Int> { done ->
            controller.notify(
                NotificationIntent.LotteryDrawn("lot1", "Jasper", ids, emptyList()), done
            )
        }
        result<Int> { done ->
            controller.notify(
                NotificationIntent.LotteryDrawn("lot1", "Jasper", ids, emptyList()), done
            )
        }
        Assert.assertEquals(2, repo.saved.size)
        Assert.assertEquals(2, repo.saveCalls)
    }

    @Test
    fun opted_out_get_record_no_banner() {
        val checker = OptOutChecker { ids, done -> done(ids.filter { it != "u2" }) }
        controller = NotificationController(repo, checker)
        result<Int> { done ->
            controller.notify(
                NotificationIntent.LotteryDrawn("lot1", "Banff", listOf("u1", "u2"), emptyList()),
                done
            )
        }
        Assert.assertTrue(repo.saved.values.first { it.recipientId == "u1" }.pushAllowed)
        Assert.assertFalse(repo.saved.values.first { it.recipientId == "u2" }.pushAllowed)
        Assert.assertEquals(
            2,
            repo.saved.size
        ) // the record exists for both since admin log needs it
    }

    @Test
    fun blank_lottery_id_refused_nothing_saved() {
        val r = result<Int> { done ->
            controller.notify(
                NotificationIntent.LotteryDrawn(" ", "X", listOf("u1"), emptyList()), done
            )
        }
        Assert.assertTrue(r.isFailure)
        Assert.assertEquals(0, repo.saveCalls)
    }

    @Test
    fun no_winners_is_succes_with_zero_saved() {
        val r = result<Int> { done ->
            controller.notify(
                NotificationIntent.LotteryDrawn("lot1", "X", emptyList(), emptyList()), done
            )
        }
        Assert.assertEquals(0, r.getOrNull())
        Assert.assertEquals(0, repo.saveCalls)
    }

    @Test
    fun storage_failure_is_reported_not_hidden() {
        repo.failNextSave = true
        val r = result<Int> { done ->
            controller.notify(
                NotificationIntent.LotteryDrawn("lot1", "X", listOf("u1"), emptyList()), done
            )
        }
        Assert.assertTrue(r.isFailure)
    }

    @Test
    fun losers_get_one_lose_notif() {
        val r = result<Int> { done ->
            controller.notify(
                NotificationIntent.LotteryDrawn(
                    "lot-may-2027", "Banff", emptyList(), listOf("u4", "u5")
                ),
                done
            )
        }
        Assert.assertEquals(2, r.getOrNull())
        Assert.assertTrue(repo.saved.values.all { it.type == NotificationType.LOSE })
        Assert.assertEquals(
            setOf("u4", "u5"),
            repo.saved.values.map { it.recipientId }.toSet()
        )
    }

    /**
     * US 01.05.01: a completed reservation reaches both of its parties, so the entrant sees
     * it confirmed in their own inbox, and both records are attributed to the one who
     * completed it.
     */
    @Test
    fun confirmation_reaches_both_parties_and_names_the_reservation() {
        controller.notify(
            NotificationIntent.ReservationConfirmed(reservedByentrant("owner1", "entrant1"))
        )
        val records = repo.saved.values.toList()
        Assert.assertEquals(2, records.size)
        Assert.assertEquals(setOf("owner1", "entrant1"), records.map { it.recipientId }.toSet())
        Assert.assertTrue(records.all { it.type == NotificationType.CONFIRMATION })
        Assert.assertTrue(records.all { it.senderId == "entrant1" })
        Assert.assertTrue(records.all { it.reservationId == "entry1" })
    }

    /** The party that caused an event is never told about it, and the record names the cause. */
    @Test
    fun cancellation_and_rejection_reach_only_the_other_party() {
        val reservation = reservedByentrant("owner1", "entrant1")

        repo.forgetSaved()
        controller.notify(NotificationIntent.ReservationCancelled(reservation))
        val cancelled = repo.saved.values.single()
        Assert.assertEquals("owner1", cancelled.recipientId)
        Assert.assertEquals("entrant1", cancelled.senderId)
        Assert.assertTrue(cancelled.type == NotificationType.CANCELLATION)

        repo.forgetSaved()
        controller.notify(NotificationIntent.ReservationRejected(reservation))
        val rejected = repo.saved.values.single()
        Assert.assertEquals("entrant1", rejected.recipientId)
        Assert.assertEquals("owner1", rejected.senderId)
        Assert.assertTrue(rejected.type == NotificationType.REJECTION)
    }

    /** An owner who is also their own entrant is told once, not twice about one event. */
    @Test
    fun one_person_on_both_sides_of_a_reservation_is_told_once() {
        controller.notify(
            NotificationIntent.ReservationConfirmed(reservedByentrant("solo1", "solo1"))
        )
        Assert.assertEquals(1, repo.saved.size)
        Assert.assertEquals("solo1", repo.saved.values.single().recipientId)
    }

    /**
     * US 02.04.08: an invitation to join a lottery reaches every owner selected for it, and
     * the record is attributed to the owner who did the selecting. The notice names the
     * lottery so opening it opens the lottery; validity of the lottery is the lottery's own
     * concern, not the notice's, so this record skips the dates.
     */
    @Test
    fun invitation_reaches_every_selected_owner_and_names_the_inviter() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one", "camp-two"),
            invitedOwnerIds = listOf("co-1", "co-2")
        )
        controller.notify(NotificationIntent.LotteryInvited(lottery))
        val records = repo.saved.values.toList()
        Assert.assertEquals(2, records.size)
        Assert.assertEquals(setOf("co-1", "co-2"), records.map { it.recipientId }.toSet())
        Assert.assertTrue(records.all { it.type == NotificationType.INVITE })
        Assert.assertTrue(records.all { it.senderId == "owner-1" })
        Assert.assertTrue(records.all { it.lotteryId == lottery.id })
    }

    /** A cause is never told about their own action, and blanks and repeats fold out. */
    @Test
    fun inviter_blanks_and_repeats_are_never_invited() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one"),
            invitedOwnerIds = listOf("co-2", "", " ", "co-2", "owner-1")
        )
        controller.notify(NotificationIntent.LotteryInvited(lottery))
        Assert.assertEquals(1, repo.saved.size)
        Assert.assertEquals("co-2", repo.saved.values.single().recipientId)
    }

    @Test
    fun nobody_selected_means_nothing_stored() {
        val r = result<Int> { done ->
            controller.notify(
                NotificationIntent.LotteryInvited(
                    Lottery(ownerId = "owner-1", campsiteIds = listOf("camp-one"))
                ),
                done
            )
        }
        Assert.assertEquals(0, r.getOrNull())
        Assert.assertEquals(0, repo.saveCalls)
    }

    // US 01.03.02: a message arrival is the fifth reporting family; its wording,
    // preview cap, and fallback line are all stated by the spec and none of them
    // is exercised above.
    @Test
    fun message_arrival_previews_the_message_and_names_the_place() {
        val incoming = Message(
            senderId = "owner-1",
            recipientId = "camper-1",
            reservationId = "entry1",
            content = "The camp closes at dusk today."
        )
        controller.notify(NotificationIntent.MessageArrived(incoming, "Pine Hollow"))
        val record = repo.saved.values.single()
        Assert.assertTrue(record.type == NotificationType.MESSAGE)
        Assert.assertEquals("camper-1", record.recipientId)
        Assert.assertEquals("owner-1", record.senderId)
        Assert.assertEquals("New message about Pine Hollow", record.title)
        Assert.assertEquals(
            "You received a new message: The camp closes at dusk today.",
            record.message
        )
        Assert.assertEquals("entry1", record.reservationId)
        Assert.assertEquals("Pine Hollow", record.campsiteName)
    }

    @Test
    fun blank_place_draws_the_campsite_and_blank_content_the_fallback_line() {
        val incoming = Message(
            senderId = "owner-1",
            recipientId = "camper-1",
            reservationId = "entry1",
            content = ""
        )
        controller.notify(NotificationIntent.MessageArrived(incoming, ""))
        val record = repo.saved.values.single()
        Assert.assertEquals("New message about the campsite", record.title)
        Assert.assertEquals(
            "You received a new message. Open CampBNB to read it.",
            record.message
        )
        Assert.assertEquals("the campsite", record.campsiteName)
    }

    @Test
    fun message_preview_cuts_at_a_hundred_characters_and_exactly_a_hundred_keeps_them() {
        val longContent = "x".repeat(150)
        controller.notify(
            NotificationIntent.MessageArrived(
                Message(
                    senderId = "owner-1",
                    recipientId = "camper-long",
                    reservationId = "entry1",
                    content = longContent
                ),
                "Pine Hollow"
            )
        )
        Assert.assertEquals(
            "You received a new message: " + "x".repeat(100),
            repo.saved.values.single().message
        )

        repo.forgetSaved()
        val exactContent = "y".repeat(100)
        controller.notify(
            NotificationIntent.MessageArrived(
                Message(
                    senderId = "owner-1",
                    recipientId = "camper-exact",
                    reservationId = "entry1",
                    content = exactContent
                ),
                "Pine Hollow"
            )
        )
        Assert.assertEquals(
            "You received a new message: " + exactContent,
            repo.saved.values.single().message
        )
    }

    // The exact copy of every drawn record, including the unspaced lose literal and
    // the forced deterministic record ids that keep one draw storing one record.
    @Test
    fun drawn_records_use_the_exact_copy_and_the_forced_ids() {
        controller.notify(
            NotificationIntent.LotteryDrawn("lot-x", "Pine Ridge", listOf("w1"), listOf("l1"))
        )
        val win = repo.saved.getValue("lot-x_w1_WIN")
        val lose = repo.saved.getValue("lot-x_l1_LOSE")
        Assert.assertEquals("You won the lottery for Pine Ridge!", win.title)
        Assert.assertEquals(
            "Congratulations! You were selected in the draw for Pine Ridge. " +
                    "Action needed: open the app and choose your dates before the booking window closes.",
            win.message
        )
        Assert.assertEquals("Lottery result for Pine Ridge", lose.title)
        Assert.assertEquals(
            "Unfortunately you were not selected in the draw this time.Thank you for entering",
            lose.message
        )
        Assert.assertTrue(win.senderId.isEmpty())
        Assert.assertTrue(lose.senderId.isEmpty())
        Assert.assertEquals("Pine Ridge", win.campsiteName)
        Assert.assertEquals("lot-x", win.lotteryId)
    }

    @Test
    fun an_entrant_in_both_lists_is_told_only_they_won() {
        controller.notify(
            NotificationIntent.LotteryDrawn("lot-fold", "Banff", listOf("both"), listOf("both"))
        )
        Assert.assertEquals(1, repo.saved.size)
        val record = repo.saved.values.single()
        Assert.assertTrue(record.type == NotificationType.WIN)
        Assert.assertEquals("lot-fold_both_WIN", record.id)
    }

    // The invitation record counts the campsites in words and carries its forced id.
    @Test
    fun invited_records_count_the_campsites_in_words() {
        val three = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-a", "camp-b", "camp-c"),
            invitedOwnerIds = listOf("co-three")
        )
        controller.notify(NotificationIntent.LotteryInvited(three))
        val many = repo.saved.getValue(three.id + "_co-three_INVITE")
        Assert.assertEquals("You were invited to a lottery for 3 campsites", many.title)
        Assert.assertEquals(
            "You have been invited to join a campsite lottery. Open CampBNB to see " +
                    "the lottery and its campsites. If you accept, you can add your own campsites to it.",
            many.message
        )
        Assert.assertEquals("owner-1", many.senderId)
        Assert.assertEquals(three.id, many.lotteryId)

        repo.forgetSaved()
        val one = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-one"),
            invitedOwnerIds = listOf("co-one")
        )
        controller.notify(NotificationIntent.LotteryInvited(one))
        Assert.assertEquals(
            "You were invited to a lottery for one campsite",
            repo.saved.values.single().title
        )

        repo.forgetSaved()
        val none = Lottery(
            ownerId = "owner-1",
            campsiteIds = emptyList(),
            invitedOwnerIds = listOf("co-none")
        )
        controller.notify(NotificationIntent.LotteryInvited(none))
        Assert.assertEquals(
            "You were invited to a lottery for the campsite",
            repo.saved.values.single().title
        )
    }

    // The reservation records name "the campsite" only, never the id, with exact copy.
    @Test
    fun reservation_records_use_the_exact_family_copy() {
        val reservation = reservedByentrant("owner1", "entrant1")

        controller.notify(NotificationIntent.ReservationConfirmed(reservation))
        repo.saved.values.forEach {
            Assert.assertEquals("Reservation confirmed for the campsite", it.title)
            Assert.assertEquals("Your reservation has been confirmed.", it.message)
        }

        repo.forgetSaved()
        controller.notify(NotificationIntent.ReservationCancelled(reservation))
        repo.saved.values.forEach {
            Assert.assertEquals("Reservation cancelled for the campsite", it.title)
            Assert.assertEquals("The reservation has been cancelled.", it.message)
        }

        repo.forgetSaved()
        controller.notify(NotificationIntent.ReservationRejected(reservation))
        repo.saved.values.forEach {
            Assert.assertEquals("Reservation rejected for the campsite", it.title)
            Assert.assertEquals("The reservation request has been rejected.", it.message)
        }
    }

    // The ordering companion places unstamped (never-saved) records first.
    @Test
    fun unstamped_records_sort_before_stamped_ones() {
        val older = Notification(
            recipientId = "u1",
            type = NotificationType.WIN,
            sentAt = Timestamp(100L, 0)
        ).also { it.id = "older" }
        val newer = Notification(
            recipientId = "u1",
            type = NotificationType.WIN,
            sentAt = Timestamp(200L, 0)
        ).also { it.id = "newer" }
        val neverSaved = Notification(
            recipientId = "u1",
            type = NotificationType.WIN
        ).also { it.id = "unstamped" }

        val ordered = NotificationController.sortNewestFirst(listOf(older, newer, neverSaved))

        Assert.assertEquals(
            listOf("unstamped", "newer", "older"),
            ordered.map { it.id }
        )
    }
}
