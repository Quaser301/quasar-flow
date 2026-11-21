package com.example.campbnb.lotteryTests

import com.example.campbnb.controller.LotteryController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.database.QueryEngine
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * US 02.04.08: other owners may be invited to share a lottery. These cases are the
 * invitation's own rules: the model's membership, and how the controller moves owners
 * through invited, joining, and visible at the save boundary. The invitation record and
 * who it reaches belong to the notification feature, covered in NotificationControllerTest.
 */
class LotteryInvitationsTest {

    /** A lottery whose parameters are valid, with campsites and membership as described. */
    private fun shareableLottery(
        ownerId: String,
        invited: List<String>,
        joined: List<String>,
        campsiteIds: List<String> = listOf("camp-one")
    ) = Lottery(
        ownerId = ownerId,
        campsiteIds = campsiteIds,
        registrationStart = System.currentTimeMillis() + 1000L,
        registrationEnd = System.currentTimeMillis() + 600000L,
        bookingStart = System.currentTimeMillis() + 1200000L,
        bookingEnd = System.currentTimeMillis() + 1800000L,
        numberOfWinners = 1,
        invitedOwnerIds = invited,
        joinedOwnerIds = joined
    )

    /** A controller over a stubbed repository; nothing touches Firestore. */
    private fun controllerOn(
        repository: LotteryRepository,
        notifications: NotificationController
    ): LotteryController = LotteryController(
        LotteryModel(),
        repository,
        mockk<QueryEngine<Lottery>>(),
        notifications
    )

    @Test
    fun selected_owners_save_with_their_lottery() {
        assertTrue(
            shareableLottery(
                "owner-1", listOf("co-1", "co-2"), emptyList()
            ).isValid(System.currentTimeMillis())
        )
    }

    @Test
    fun blank_invited_owner_blocks_the_save() {
        assertFalse(
            shareableLottery(
                "owner-1", listOf("co-1", ""), emptyList()
            ).isValid(System.currentTimeMillis())
        )
    }

    @Test
    fun an_owner_cannot_invite_themselves() {
        assertFalse(
            shareableLottery(
                "owner-1", listOf("owner-1"), emptyList()
            ).isValid(System.currentTimeMillis())
        )
    }

    @Test
    fun one_owner_cannot_be_both_invited_and_joining() {
        assertFalse(
            shareableLottery(
                "owner-1", listOf("co-1"), listOf("co-1")
            ).isValid(System.currentTimeMillis())
        )
    }

    @Test
    fun the_same_owner_named_twice_blocks_the_save() {
        assertFalse(
            shareableLottery(
                "owner-1", listOf("co-1", "co-1"), emptyList()
            ).isValid(System.currentTimeMillis())
        )
    }

    /** Accepting moves the one invitation without touching anything else, id included. */
    @Test
    fun accepting_moves_the_owner_from_invited_to_joining() {
        val lottery = shareableLottery("owner-1", listOf("co-1", "co-2"), emptyList())
        val accepted = lottery.withInvitationAcceptedBy("co-1")

        assertEquals(listOf("co-2"), accepted.invitedOwnerIds)
        assertEquals(listOf("co-1"), accepted.joinedOwnerIds)
        assertEquals(lottery.id, accepted.id)
        assertEquals(lottery.ownerId, accepted.ownerId)
        assertEquals(lottery.campsiteIds, accepted.campsiteIds)
        assertEquals(lottery.numberOfWinners, accepted.numberOfWinners)
        assertTrue(accepted.isValid(System.currentTimeMillis()))
    }

    @Test
    fun accepting_an_uninvited_owner_changes_nothing() {
        val lottery = shareableLottery("owner-1", listOf("co-1"), emptyList())
        val accepted = lottery.withInvitationAcceptedBy("stranger")

        assertEquals(listOf("co-1"), accepted.invitedOwnerIds)
        assertTrue(accepted.joinedOwnerIds.isEmpty())
    }

    /** Who may see the lottery: its creator and joining owners; the rest may not. */
    @Test
    fun creator_and_joining_owners_see_the_lottery_and_others_do_not() {
        val lottery = shareableLottery("owner-1", listOf("pending"), listOf("co-1"))
        assertTrue(lottery.visibleTo("owner-1"))
        assertTrue(lottery.visibleTo("co-1"))
        assertFalse(lottery.visibleTo("pending"))
        assertFalse(lottery.visibleTo("outsider"))
    }

    /** Saving a lottery that names owners reports one invitation event. */
    @Test
    fun a_lottery_saved_with_selected_owners_reports_one_invitation() {
        val repository = mockk<LotteryRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val controller = controllerOn(repository, notifications)

        val onSaved = slot<Function0<Unit>>()
        val onFailed = slot<Function1<Exception, Unit>>()
        every {
            repository.save(any(), capture(onSaved), capture(onFailed))
        } answers {
            onSaved.captured.invoke()
        }

        controller.saveLottery(
            shareableLottery("owner-1", listOf("co-1", "co-2"), emptyList()),
            onSuccess = {},
            onFailure = {}
        )

        verify(exactly = 1) { notifications.notify(any()) }
    }

    /** An unsaved lottery is not one anyone can join, so a failed save reports nothing. */
    @Test
    fun a_failed_save_invites_nobody() {
        val repository = mockk<LotteryRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val controller = controllerOn(repository, notifications)

        val onSaved = slot<Function0<Unit>>()
        val onFailed = slot<Function1<Exception, Unit>>()
        every {
            repository.save(any(), capture(onSaved), capture(onFailed))
        } answers {
            onFailed.captured.invoke(IllegalArgumentException("offline"))
        }

        controller.saveLottery(
            shareableLottery("owner-1", listOf("co-1"), emptyList()),
            onSuccess = {},
            onFailure = {}
        )

        verify(exactly = 0) { notifications.notify(any()) }
    }

    @Test
    fun accepting_an_invitation_saves_the_lottery_with_the_owner_joining() {
        val repository = mockk<LotteryRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val controller = controllerOn(repository, notifications)

        val saved = slot<Lottery>()
        val onSaved = slot<Function0<Unit>>()
        val onFailed = slot<Function1<Exception, Unit>>()
        every {
            repository.save(capture(saved), capture(onSaved), capture(onFailed))
        } answers {
            onSaved.captured.invoke()
        }

        val lottery = shareableLottery("owner-1", listOf("co-1", "co-2"), emptyList())
        controller.acceptInvitation(lottery, "co-1")

        assertEquals(listOf("co-2"), saved.captured.invitedOwnerIds)
        assertEquals(listOf("co-1"), saved.captured.joinedOwnerIds)
        assertEquals(lottery.id, saved.captured.id)
    }

    @Test
    fun accepting_without_being_invited_stores_nothing() {
        val model = LotteryModel()
        val repository = mockk<LotteryRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val controller = LotteryController(
            model,
            repository,
            mockk<QueryEngine<Lottery>>(),
            notifications
        )

        val lottery = shareableLottery("owner-1", listOf("co-1"), emptyList())
        controller.acceptInvitation(lottery, "stranger")

        verify(exactly = 0) { repository.save(any(), any(), any()) }
        assertEquals("This lottery does not invite you", model.statusMessage)
    }

    /** A joining owner's edit keeps every other owner's campsites and every parameter. */
    @Test
    fun a_joining_owner_edit_keeps_other_owners_campsites() {
        val repository = mockk<LotteryRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val controller = controllerOn(repository, notifications)

        val onOwned = slot<Function1<List<Campsite>, Unit>>()
        every {
            repository.loadCampsitesByOwner(capture(onOwned), any(), any())
        } answers {
            onOwned.captured.invoke(
                listOf(
                    Campsite(ownerId = "co-1").also { it.id = "own-one" },
                    Campsite(ownerId = "co-1").also { it.id = "own-two" }
                )
            )
        }

        val saved = slot<Lottery>()
        val onSaved = slot<Function0<Unit>>()
        val onFailed = slot<Function1<Exception, Unit>>()
        every {
            repository.save(capture(saved), capture(onSaved), capture(onFailed))
        } answers {
            onSaved.captured.invoke()
        }

        val lottery = shareableLottery(
            "owner-1",
            emptyList(),
            listOf("co-1"),
            campsiteIds = listOf("camp-one", "own-one")
        )
        controller.saveCampsitesEditedBy(
            editorId = "co-1",
            lottery = lottery,
            selectedCampsiteIds = listOf("own-two"),
            onSuccess = {},
            onFailure = {}
        )

        assertEquals(listOf("camp-one", "own-two"), saved.captured.campsiteIds)
        assertEquals(lottery.id, saved.captured.id)
        assertEquals(listOf("co-1"), saved.captured.joinedOwnerIds)
        assertEquals(lottery.numberOfWinners, saved.captured.numberOfWinners)
    }

    /** A campsite the editor does not own cannot be added, and nothing is saved. */
    @Test
    fun an_edit_refuses_a_campsite_the_editor_does_not_own() {
        val repository = mockk<LotteryRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val controller = controllerOn(repository, notifications)

        val onOwned = slot<Function1<List<Campsite>, Unit>>()
        every {
            repository.loadCampsitesByOwner(capture(onOwned), any(), any())
        } answers {
            onOwned.captured.invoke(
                listOf(Campsite(ownerId = "co-1").also { it.id = "own-one" })
            )
        }

        var failure: Exception? = null
        controller.saveCampsitesEditedBy(
            editorId = "co-1",
            lottery = shareableLottery("owner-1", emptyList(), listOf("co-1")),
            selectedCampsiteIds = listOf("not-mine"),
            onSuccess = {},
            onFailure = { error -> failure = error }
        )

        assertTrue(failure is IllegalArgumentException)
        verify(exactly = 0) { repository.save(any(), any(), any()) }
    }

    /** The lotteries view lists only what the owner may see. */
    @Test
    fun the_owners_view_lists_only_lotteries_visible_to_its_owner() {
        val model = LotteryModel()
        val repository = mockk<LotteryRepository>()
        val notifications = mockk<NotificationController>(relaxed = true)
        val controller = LotteryController(
            model,
            repository,
            mockk<QueryEngine<Lottery>>(),
            notifications
        )

        val mine = shareableLottery("me-1", emptyList(), emptyList())
        val joined = shareableLottery("other-1", emptyList(), listOf("me-1"))
        val foreign = shareableLottery("other-1", emptyList(), listOf("someone-else"))

        val onGot = slot<Function1<List<Lottery>, Unit>>()
        every {
            repository.getAllLotteries(capture(onGot), any())
        } answers {
            onGot.captured.invoke(listOf(mine, joined, foreign))
        }

        controller.loadOwnerLotteries("me-1")

        assertEquals(
            setOf(mine.id, joined.id),
            model.lotteryList.map { it.id }.toSet()
        )
    }
}
