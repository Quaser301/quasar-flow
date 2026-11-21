package com.example.campbnb.notificationTests

import com.example.campbnb.controller.NotificationController
import com.example.campbnb.controller.OptOutChecker
import com.example.campbnb.models.Lottery
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
}
