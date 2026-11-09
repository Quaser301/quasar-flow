package com.example.campbnb.notificationTests

// AI-assisted (re-pull of US 03.08.01, tests): written by Qwen 3.8 Flash Next Coder IQ1_M.
// No disclosure-table row: the user asked to stop logging prompts at this point.
//
// What these cases cover: the ideal flow (records read through the repository contract,
// grouped newest first, one entry per broadcast with every receiver counted), the extremes
// (no records at all, a hundred records over three broadcasts, a record whose timestamp was
// never served), wrongly entered data (an empty dropdown name, a thread whose two messages
// must not merge, an unknown profile reference), and stress (mashing dropdowns). The screen
// itself is not driven here; the state it draws is, through [NotificationLogViewModel] and
// the shared [FakeNotificationRepository].

import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.NotificationUpdate
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationType
import com.example.campbnb.models.notificationLogKeyOf
import com.example.campbnb.models.notificationLogGroupsFor
import com.example.campbnb.models.NotificationLogGroup
import com.example.campbnb.models.User
import com.example.campbnb.ui.components.profileLabelOf
import com.example.campbnb.viewModels.LogViewState
import com.example.campbnb.viewModels.NotificationLogViewModel
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLogTest {
    private val repository = FakeNotificationRepository()

    private fun record(
        id: String,
        receiver: String,
        type: NotificationType,
        title: String,
        message: String,
        seconds: Long,
        lotteryId: String = "",
        reservationId: String = "",
        sender: String = "",
        campsite: String = ""
    ): Notification = Notification(
        recipientId = receiver,
        senderId = sender,
        type = type,
        title = title,
        message = message,
        lotteryId = lotteryId,
        reservationId = reservationId,
        campsiteName = campsite,
        sentAt = if (seconds < 0) null else Timestamp(seconds, 0)
    ).also { it.id = id }

    private fun view() = NotificationLogViewModel(NotificationController(repository)) {}

    @Test
    fun startsInLoadingState() {
        assertTrue(
            "nothing has been read yet, so the log is loading",
            view().state.value is LogViewState.Loading
        )
    }

    @Test
    fun readFailureShowsMessageInsteadOfEntries() {
        repository.failNextListenAll = true

        val viewModel = view()

        assertTrue(
            "the read failure reaches the error branch",
            viewModel.state.value is LogViewState.Error
        )
    }

    @Test
    fun noRecordsShowsNoEntries() {
        val viewModel = view()
        repository.emitAll(NotificationUpdate(all = emptyList(), added = emptyList()))

        val ready = viewModel.state.value as LogViewState.Ready
        assertEquals(0, ready.logs.count())
    }

    @Test
    fun entriesFollowNewestFirstOrder() {
        val viewModel = view()
        val older = record("o1", "camper-1", NotificationType.WIN, "You won!", "Selected.", 100,
            lotteryId = "lot-old")
        val newer = record("n1", "camper-2", NotificationType.LOSE, "Result", "Not selected.", 200,
            lotteryId = "lot-new")

        repository.emitAll(NotificationUpdate(all = listOf(older, newer), added = emptyList()))

        val ready = viewModel.state.value as LogViewState.Ready
        assertEquals(2, ready.logs.count())
        assertEquals("Result", ready.logs.first().title)
    }

    @Test
    fun oneBroadcastCountsEveryReceiver() {
        val viewModel = view()
        val winners = (1..40).map { position ->
            record("w$position", "camper-$position", NotificationType.WIN, "You won!",
                "Selected in the draw.", 160, lotteryId = "lot-l1", campsite = "Pine Ridge")
        }

        repository.emitAll(NotificationUpdate(all = winners, added = emptyList()))

        val ready = viewModel.state.value as LogViewState.Ready
        assertEquals(1, ready.logs.count())
        assertEquals(40, ready.logs.first().recipientIds.count())
    }

    @Test
    fun recordsOfOneDrawShareOneKey() {
        val first = record("a", "camper-1", NotificationType.WIN, "You won!", "Selected.", 10,
            lotteryId = "lot-l1")
        val second = record("b", "camper-2", NotificationType.WIN, "You won!", "Selected.", 10,
            lotteryId = "lot-l1")

        assertEquals(
            "the same fields except the receiver name one broadcast",
            notificationLogKeyOf(first),
            notificationLogKeyOf(second)
        )
    }

    @Test
    fun differentDrawsNeverShareAKey() {
        val first = record("a", "camper-1", NotificationType.WIN, "You won!", "Selected.", 10,
            lotteryId = "lot-l1")
        val other = record("b", "camper-2", NotificationType.WIN, "You won!", "Selected.", 10,
            lotteryId = "lot-l2")

        assertTrue(
            "another lottery is another entry",
            notificationLogKeyOf(first) !=
                notificationLogKeyOf(other)
        )
    }

    @Test
    fun messageThreadStaysOneEntryPerMessage() {
        val viewModel = view()
        val first = record("m1", "camper-1", NotificationType.MESSAGE, "New message",
            "You received a new message: first", 100, reservationId = "res-3")
        val second = record("m2", "camper-1", NotificationType.MESSAGE, "New message",
            "You received a new message: second", 120, reservationId = "res-3")

        repository.emitAll(NotificationUpdate(all = listOf(first, second), added = emptyList()))

        val ready = viewModel.state.value as LogViewState.Ready
        assertEquals(2, ready.logs.count())
        assertEquals("You received a new message: second", ready.logs.first().message)
    }

    @Test
    fun unservedRecordStillCarriesTheSavedTimestamp() {
        val viewModel = view()
        val served = record("s", "camper-1", NotificationType.CONFIRMATION, "Confirmed",
            "Your reservation has been confirmed.", 300, reservationId = "res-9")
        val unserved = record("u", "camper-2", NotificationType.CONFIRMATION, "Confirmed",
            "Your reservation has been confirmed.", -1, reservationId = "res-9")

        repository.emitAll(NotificationUpdate(all = listOf(unserved, served), added = emptyList()))

        val ready = viewModel.state.value as LogViewState.Ready
        assertEquals(1, ready.logs.count())
        assertEquals(300L, ready.logs.first().sentAt?.seconds ?: -1L)
    }

    @Test
    fun openingOneLogLeavesTheOthersClosed() {
        val viewModel = view()

        viewModel.toggleLog("log-a")

        assertEquals(1, viewModel.openLogs.value.count())
        assertTrue("the tapped one is open", "log-a" in viewModel.openLogs.value)
        assertTrue("the others are not", !("log-b" in viewModel.openLogs.value))
    }

    @Test
    fun tappingAgainClosesThatLog() {
        val viewModel = view()

        viewModel.toggleLog("log-a")
        viewModel.toggleLog("log-a")

        assertEquals(0, viewModel.openLogs.value.count())
    }

    @Test
    fun emptyLogNameOpensNothing() {
        val viewModel = view()

        viewModel.toggleLog("")

        assertEquals(0, viewModel.openLogs.value.count())
    }

    @Test
    fun mashingDropdownsLeavesTheLastTapStanding() {
        val viewModel = view()

        for (round in 1..20) {
            viewModel.toggleLog("log-a")
            viewModel.toggleLog("log-b")
            viewModel.toggleLog("log-c")
            viewModel.toggleLog("log-b")
        }
        viewModel.toggleLog("log-a")
        viewModel.toggleLog("log-c")

        val open = viewModel.openLogs.value
        assertEquals(2, open.count())
        assertTrue("an odd number of taps leaves it open", "log-a" in open && "log-c" in open)
        assertTrue("an even number of taps leaves it closed", !("log-b" in open))
    }

    @Test
    fun logsKeepTheirOwnOpenStateApart() {
        val viewModel = view()

        viewModel.toggleLog("log-a")

        assertTrue(
            "opening one log never opens a log whose name carries it",
            !("log-ab" in viewModel.openLogs.value)
        )
    }

    @Test
    fun backPressRunsTheHandedLambda() {
        var wentBack = false
        val viewModel = NotificationLogViewModel(NotificationController(repository)) { wentBack = true }

        viewModel.pressBack()

        assertTrue("back runs the lambda it was handed", wentBack)
    }

    @Test
    fun startingAgainStopsThePreviousListener() {
        val viewModel = view()
        viewModel.start()

        assertEquals(1, repository.stopCalls)
    }

    @Test
    fun stoppingEndsTheLiveListener() {
        val viewModel = view()

        viewModel.stop()

        assertEquals(1, repository.stopCalls)
    }

    @Test
    fun stoppingTwiceChangesNothingFurther() {
        val viewModel = view()

        viewModel.stop()
        viewModel.stop()

        assertEquals(1, repository.stopCalls)
    }

    @Test
    fun unknownProfileReferenceIsLabelledAsSuch() {
        assertEquals("unknown (camper-7)", profileLabelOf("camper-7", emptyList()))
    }

    @Test
    fun namedProfileDrawsItsName() {
        val named = User(deviceId = "camper-7", name = "  Dana  ").also { it.id = "camper-7" }

        assertEquals("Dana", profileLabelOf("camper-7", listOf(named)))
    }

    @Test
    fun unnamedProfileDrawsAPlaceholder() {
        val nameless = User(deviceId = "camper-8").also { it.id = "camper-8" }

        assertEquals("(no name)", profileLabelOf("camper-8", listOf(nameless)))
    }

    @Test
    fun emptyReferenceDrawsNoProfileNote() {
        assertEquals("(no profile)", profileLabelOf("", listOf(User(deviceId = "camper-8"))))
    }

    @Test
    fun entriesKeepTheirOwnContentAndRoute() {
        val win = record("w", "camper-1", NotificationType.WIN, "You won!",
            "Selected in the draw.", 100, lotteryId = "lot-l1")
        val message = record("m", "camper-2", NotificationType.MESSAGE, "New message",
            "Open CampBNB to read it.", 200)

        val groups = notificationLogGroupsFor(listOf(win, message))

        assertEquals(2, groups.count())
        assertEquals("booking/lot-l1", groups.first().routeText)
        assertEquals("messages", groups.last().routeText)
        assertEquals("Selected in the draw.", groups.first().message)
        assertEquals("Open CampBNB to read it.", groups.last().message)
    }

    @Test
    fun hundredRecordsOverThreeBroadcastsGroupIntoThree() {
        val draw = (1..40).map { position ->
            record("d$position", "camper-$position", NotificationType.WIN, "You won!",
                "Selected in the draw.", 100, lotteryId = "lot-a", campsite = "Pine Ridge")
        }
        val notices = (1..35).map { position ->
            record("n$position", "camper-n$position", NotificationType.OWNER_NOTICE, "Camp closing",
                "The camp closes Friday.", 200, sender = "owner-9", campsite = "Pine Ridge")
        }
        val losses = (1..25).map { position ->
            record("l$position", "camper-l$position", NotificationType.LOSE, "Result",
                "Not selected.", 300, lotteryId = "lot-b")
        }

        val groups = notificationLogGroupsFor(draw + notices + losses)

        assertEquals(3, groups.count())
        assertEquals(
            listOf(40, 35, 25),
            groups.map { it.recipientIds.count() }
        )
    }
}
