package com.example.campbnb.notificationTests

// AI-assisted (re-pull of US 03.08.01, tests): written by Qwen 3.8 Flash Next Coder IQ1_M.
//
// What these cases cover: the ideal flow (records saved, read once when the log opens,
// grouped newest first, one entry per broadcast, a dropdown opened drawing its receivers and
// its notice), the extremes (nothing saved at all, an unserved record with no saved timestamp,
// a hundred records over three broadcasts), wrongly entered data (an empty dropdown name, a log
// name that matches no entry, a receiver whose id spells another line's key, a profile reference
// that cannot be resolved), and stress (mashing dropdowns, tapping refresh, a failed read).
// The screen itself is not driven here; the state it draws is, through
// [NotificationLogViewModel], [notificationLogLinesFor], and the shared
// [FakeNotificationRepository] - the same contract the real repository implements, so these
// cases run against either. The screen's back button is not driven here because navigation is
// handled by the [androidx.navigation.NavController] the screen is handed, not by the view state.
//
// The fake keeps whatever was saved between cases, so every case that counts what a read
// serves goes through openLog, which forgets the previous records first.

import com.example.campbnb.controller.NotificationController
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationType
import com.example.campbnb.models.NotificationLogGroup
import com.example.campbnb.models.NotificationLogLine
import com.example.campbnb.models.LogLineStyle
import com.example.campbnb.models.notificationLogKeyOf
import com.example.campbnb.models.notificationLogGroupsFor
import com.example.campbnb.models.notificationLogLinesFor
import com.example.campbnb.models.User
import com.example.campbnb.ui.components.profileLabelOf
import com.example.campbnb.ui.components.unresolvableProfileLabel
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

    /**
     * The log, opened fresh over records that are already saved: the fake forgets whatever an
     * earlier case left behind, saves these, and reads them as opening the screen does.
     */
    private fun openLog(
        records: List<Notification>
    ): NotificationLogViewModel {
        repository.forgetSaved()
        repository.saveAll(records) {}
        return NotificationLogViewModel(NotificationController(repository)) { userId -> "profile $userId" }
    }

    /** The state of a log that has successfully read its entries. */
    private fun openedLog(viewModel: NotificationLogViewModel): LogViewState.Ready {
        val shown = viewModel.state.value
        assertTrue(shown is LogViewState.Ready)
        return shown as LogViewState.Ready
    }

    /** The lines of one log, filtered by the look they take. */
    private fun linesOf(viewModel: NotificationLogViewModel, style: LogLineStyle): List<NotificationLogLine> =
        openedLog(viewModel).lines.filter { it.style == style }

    @Test
    fun nothingSavedReadsAnEmptyLog() {
        val shown = openedLog(openLog(emptyList()))

        assertEquals(0, shown.logs.count())
        assertEquals(0, shown.lines.count())
    }

    @Test
    fun readFailureShowsMessageInsteadOfEntries() {
        repository.forgetSaved()
        repository.failNextReadAll = true

        val log = NotificationLogViewModel(NotificationController(repository)) { userId -> userId }
        val shown = log.state.value

        assertTrue(shown is LogViewState.Error)
        assertEquals("fake read failure", (shown as LogViewState.Error).message)
    }

    @Test
    fun failedRefreshShowsTheNewFailureInsteadOfThePreviousLog() {
        val draw = record("w", "camper-1", NotificationType.WIN, "You won!", "Selected.", 100,
            lotteryId = "lot-l1")
        val log = openLog(listOf(draw))
        log.toggleLog(notificationLogKeyOf(draw))
        repository.failNextReadAll = true
        log.readLog()

        val shown = log.state.value

        assertTrue(shown is LogViewState.Error)
        assertEquals("fake read failure", (shown as LogViewState.Error).message)
    }

    @Test
    fun openStateSurvivesAFailedReadAndAppliesOnTheNextOne() {
        val draw = record("w", "camper-1", NotificationType.WIN, "You won!", "Selected.", 100,
            lotteryId = "lot-l1")
        repository.saveAll(listOf(draw)) {}
        repository.failNextReadAll = true
        val log = NotificationLogViewModel(NotificationController(repository)) { userId -> userId }
        val name = notificationLogKeyOf(draw)

        log.toggleLog(name)
        assertTrue(log.state.value is LogViewState.Error)

        log.readLog()
        val shown = openedLog(log)

        assertEquals(1, shown.logs.count())
        assertEquals(4, shown.lines.count())
        assertEquals(1, linesOf(log, LogLineStyle.RECIPIENT).count())
        assertEquals(1, linesOf(log, LogLineStyle.NOTICE_TITLE).count())
        assertEquals(1, linesOf(log, LogLineStyle.NOTICE_BODY).count())
    }

    @Test
    fun savedRecordsAreReadOnOpening() {
        val win = record("w", "camper-1", NotificationType.WIN, "You won!", "Selected.", 100,
            lotteryId = "lot-open")
        val notice = record("n", "camper-2", NotificationType.OWNER_NOTICE, "Camp closing",
            "The camp closes Friday.", 200, sender = "owner-9")

        val shown = openedLog(openLog(listOf(win, notice)))

        assertEquals(2, shown.logs.count())
        assertEquals(2, shown.lines.count())
        assertEquals(2, linesOf(openLog(listOf(win, notice)), LogLineStyle.DROPDOWN).count())
    }

    @Test
    fun entriesFollowNewestFirstOrder() {
        val older = record("o1", "camper-1", NotificationType.WIN, "Older", "Old notice.", 100,
            lotteryId = "lot-old")
        val newer = record("n1", "camper-2", NotificationType.LOSE, "Newer", "New notice.", 200,
            lotteryId = "lot-new")

        val shown = openedLog(openLog(listOf(older, newer)))

        assertEquals(2, shown.logs.count())
        assertEquals("Newer", shown.logs.first().title)
    }

    @Test
    fun oneBroadcastCountsEveryReceiver() {
        val winners = (1..40).map { position ->
            record("w$position", "camper-$position", NotificationType.WIN, "You won!",
                "Selected in the draw.", 160, lotteryId = "lot-l1", campsite = "Pine Ridge")
        }

        val log = openLog(winners)
        val shown = openedLog(log)

        assertEquals(1, shown.logs.count())
        assertEquals(40, shown.logs.first().recipientIds.count())
        assertEquals(1, shown.lines.count())
        assertEquals(1, linesOf(log, LogLineStyle.DROPDOWN).count())
    }

    @Test
    fun openedDropdownDrawsItsReceiversAndItsNotice() {
        val draw = (1..3).map { position ->
            record("w$position", "camper-$position", NotificationType.WIN, "You won!",
                "Selected in the draw.", 160, lotteryId = "lot-l1", campsite = "Pine Ridge")
        }
        val log = openLog(draw)
        log.toggleLog(notificationLogKeyOf(draw.first()))

        val shown = openedLog(log)

        assertEquals(1, shown.logs.count())
        assertEquals(6, shown.lines.count())
        assertEquals(1, linesOf(log, LogLineStyle.DROPDOWN).count())
        assertEquals(3, linesOf(log, LogLineStyle.RECIPIENT).count())
        assertEquals(1, linesOf(log, LogLineStyle.NOTICE_TITLE).count())
        assertEquals(1, linesOf(log, LogLineStyle.NOTICE_BODY).count())
        assertEquals(3, linesOf(log, LogLineStyle.RECIPIENT).map { it.text }.toSet().count())
        assertEquals("You won!", linesOf(log, LogLineStyle.NOTICE_TITLE).first().text)
        assertEquals("Selected in the draw.", linesOf(log, LogLineStyle.NOTICE_BODY).first().text)
    }

    @Test
    fun openingOneLogLeavesTheOthersClosed() {
        val opened = record("a", "camper-open", NotificationType.WIN, "You won!", "Selected.", 100,
            lotteryId = "lot-open")
        val leftClosed = record("b", "camper-closed", NotificationType.OWNER_NOTICE, "Camp closing",
            "The camp closes Friday.", 200, sender = "owner-9")

        val log = openLog(listOf(opened, leftClosed))
        log.toggleLog(notificationLogKeyOf(opened))
        val shown = openedLog(log)

        assertEquals(2, shown.logs.count())
        assertEquals(5, shown.lines.count())
        assertEquals(2, linesOf(log, LogLineStyle.DROPDOWN).count())
        assertEquals(1, linesOf(log, LogLineStyle.RECIPIENT).count())
        assertEquals(1, linesOf(log, LogLineStyle.NOTICE_TITLE).count())
        assertEquals(1, linesOf(log, LogLineStyle.NOTICE_BODY).count())
    }

    @Test
    fun tappingAgainClosesThatLog() {
        val draw = (1..3).map { position ->
            record("w$position", "camper-$position", NotificationType.WIN, "You won!",
                "Selected in the draw.", 160, lotteryId = "lot-l1")
        }
        val log = openLog(draw)
        val name = notificationLogKeyOf(draw.first())

        log.toggleLog(name)
        log.toggleLog(name)
        val shown = openedLog(log)

        assertEquals(1, shown.lines.count())
        assertEquals(1, linesOf(log, LogLineStyle.DROPDOWN).count())
        assertEquals(0, linesOf(log, LogLineStyle.RECIPIENT).count())
    }

    @Test
    fun emptyLogNameOpensNothing() {
        val draw = (1..3).map { position ->
            record("w$position", "camper-$position", NotificationType.WIN, "You won!",
                "Selected in the draw.", 160, lotteryId = "lot-l1")
        }
        val log = openLog(draw)

        log.toggleLog("")
        val shown = openedLog(log)

        assertEquals(1, shown.lines.count())
        assertEquals(1, linesOf(log, LogLineStyle.DROPDOWN).count())
        assertEquals(0, log.openLogs.value.count())
    }

    @Test
    fun nameMatchingNoEntryChangesNothing() {
        val draw = record("w", "camper-1", NotificationType.WIN, "You won!", "Selected.", 100,
            lotteryId = "lot-l1")
        val log = openLog(listOf(draw))

        log.toggleLog("no-such-log")
        val shown = openedLog(log)

        assertEquals(1, shown.lines.count())
        assertEquals(1, linesOf(log, LogLineStyle.DROPDOWN).count())
    }

    @Test
    fun mashingDropdownsLeavesTheLastTapStanding() {
        val alpha = record("a", "camper-alpha", NotificationType.WIN, "Alpha", "Alpha notice.", 100,
            lotteryId = "lot-alpha")
        val beta = record("b", "camper-beta", NotificationType.LOSE, "Beta", "Beta notice.", 200,
            lotteryId = "lot-beta")
        val log = openLog(listOf(alpha, beta))

        val alphaName = notificationLogKeyOf(alpha)
        val betaName = notificationLogKeyOf(beta)
        for (tap in 1..21) {
            log.toggleLog(alphaName)
            log.toggleLog(betaName)
            log.toggleLog(betaName)
        }

        val shown = openedLog(log)

        assertEquals(5, shown.lines.count())
        assertEquals(1, linesOf(log, LogLineStyle.RECIPIENT).count())
        assertEquals("profile camper-alpha", linesOf(log, LogLineStyle.RECIPIENT).first().text)
    }

    @Test
    fun refreshReadsWhatIsSavedNow() {
        val first = record("a", "camper-1", NotificationType.WIN, "You won!", "Selected.", 100,
            lotteryId = "lot-a")
        val addedLater = record("z", "camper-2", NotificationType.LOSE, "Result", "Not selected.", 50,
            lotteryId = "lot-z")

        val log = openLog(listOf(first))
        repository.saveAll(listOf(addedLater)) {}
        log.readLog()
        val shown = openedLog(log)

        assertEquals(2, shown.logs.count())
        assertEquals("You won!", shown.logs.first().title)
    }

    @Test
    fun everyRefreshTapReadsExactlyOnce() {
        val log = openLog(emptyList())

        for (tap in 1..3) {
            log.readLog()
        }

        assertEquals(4, repository.readAllCalls)
    }

    @Test
    fun lineKeysStayUniqueWithinOneEntry() {
        val lookalike = record("a", "notice#title", NotificationType.WIN, "You won!",
            "Selected.", 100, lotteryId = "lot-l1")
        val other = record("b", "notice#body", NotificationType.WIN, "You won!",
            "Selected.", 100, lotteryId = "lot-l1")
        val groups = notificationLogGroupsFor(listOf(lookalike, other))
        val lines = notificationLogLinesFor(groups, setOf(notificationLogKeyOf(lookalike))) { userId -> userId }

        assertEquals(5, lines.count())
        assertEquals(lines.count(), lines.map { it.lineKey }.distinct().count())
    }

    @Test
    fun sameReceiverInTwoEntriesNeverSharesALine() {
        val win = record("a", "camper-1", NotificationType.WIN, "You won!", "Selected.", 100,
            lotteryId = "lot-a")
        val lose = record("b", "camper-1", NotificationType.LOSE, "Result", "Not selected.", 300,
            lotteryId = "lot-b")
        val groups = notificationLogGroupsFor(listOf(win, lose))
        val lines = notificationLogLinesFor(groups, groups.map { it.logId }.toSet()) { userId -> userId }

        assertEquals(8, lines.count())
        assertEquals(lines.count(), lines.map { it.lineKey }.distinct().count())
    }

    @Test
    fun nothingOpenResolvesNoProfileLabel() {
        val draw = (1..3).map { position ->
            record("w$position", "camper-$position", NotificationType.WIN, "You won!",
                "Selected in the draw.", 160, lotteryId = "lot-l1")
        }
        val groups = notificationLogGroupsFor(draw)
        var resolved = 0
        val lines = notificationLogLinesFor(groups, emptySet<String>()) { userId ->
            resolved++
            "profile $userId"
        }

        assertEquals(0, resolved)
        assertEquals(1, lines.count())
        assertEquals(1, lines.filter { it.style == LogLineStyle.DROPDOWN }.count())
        assertEquals(0, lines.filter { it.style == LogLineStyle.RECIPIENT }.count())
    }

    @Test
    fun openingADropdownResolvesEachReceiverOnce() {
        val draw = (1..3).map { position ->
            record("w$position", "camper-$position", NotificationType.WIN, "You won!",
                "Selected in the draw.", 160, lotteryId = "lot-l1")
        }
        val groups = notificationLogGroupsFor(draw)
        var resolved = 0
        val lines = notificationLogLinesFor(groups, setOf(notificationLogKeyOf(draw.first()))) { userId ->
            resolved++
            "profile $userId"
        }

        assertEquals(3, resolved)
        assertEquals(6, lines.count())
        assertEquals(3, lines.filter { it.style == LogLineStyle.RECIPIENT }.count())
    }

    @Test
    fun recordNeverSavedLabelsItAsSuch() {
        val unsaved = record("u", "camper-2", NotificationType.CONFIRMATION, "Confirmed",
            "Your reservation has been confirmed.", -1, reservationId = "res-9")

        val groups = notificationLogGroupsFor(listOf(unsaved))

        assertEquals(1, groups.count())
        assertEquals("no saved timestamp", groups.first().sentAtLabel)
    }

    @Test
    fun savedRecordDoesNotLabelItAsMissing() {
        val saved = record("s", "camper-1", NotificationType.CONFIRMATION, "Confirmed",
            "Your reservation has been confirmed.", 300, reservationId = "res-9")

        val groups = notificationLogGroupsFor(listOf(saved))

        assertEquals(1, groups.count())
        assertTrue(groups.first().sentAtLabel != "no saved timestamp")
    }

    @Test
    fun recordsOfOneDrawShareOneKey() {
        val first = record("a", "camper-1", NotificationType.WIN, "You won!", "Selected.", 10,
            lotteryId = "lot-l1")
        val second = record("b", "camper-2", NotificationType.WIN, "You won!", "Selected.", 10,
            lotteryId = "lot-l1")

        assertEquals(notificationLogKeyOf(first), notificationLogKeyOf(second))
    }

    @Test
    fun differentDrawsNeverShareAKey() {
        val first = record("a", "camper-1", NotificationType.WIN, "You won!", "Selected.", 10,
            lotteryId = "lot-l1")
        val other = record("b", "camper-2", NotificationType.WIN, "You won!", "Selected.", 10,
            lotteryId = "lot-l2")

        assertTrue(notificationLogKeyOf(first) != notificationLogKeyOf(other))
    }

    @Test
    fun fieldsThatRunTogetherNeverForgeOneKey() {
        val left = record("a", "camper-1", NotificationType.WIN, "You won!", "Selected.", 10,
            lotteryId = "lot-l1", reservationId = "res")
        val right = record("b", "camper-1", NotificationType.WIN, "You won!", "Selected.", 10,
            lotteryId = "lot-l1res", reservationId = "")

        assertTrue(notificationLogKeyOf(left) != notificationLogKeyOf(right))
    }

    @Test
    fun messageThreadStaysOneEntryPerMessage() {
        val first = record("m1", "camper-1", NotificationType.MESSAGE, "New message",
            "You received a new message: first", 100, reservationId = "res-3")
        val second = record("m2", "camper-1", NotificationType.MESSAGE, "New message",
            "You received a new message: second", 120, reservationId = "res-3")

        val groups = notificationLogGroupsFor(listOf(first, second))

        assertEquals(2, groups.count())
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
            listOf(25, 35, 40),
            groups.map { it.recipientIds.count() }.sorted()
        )
    }

    @Test
    fun threeBroadcastsClosedDrawThreeBars() {
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
        val lines = notificationLogLinesFor(groups, emptySet<String>()) { userId -> userId }

        assertEquals(3, lines.count())
        assertEquals(3, lines.filter { it.style == LogLineStyle.DROPDOWN }.count())
        assertEquals(0, lines.filter { it.style != LogLineStyle.DROPDOWN }.count())
    }

    @Test
    fun unknownProfileReferenceIsLabelledAsSuch() {
        assertEquals("unknown (camper-7)", profileLabelOf("camper-7", emptyList()))
    }

    @Test
    fun stableDefaultResolverLabelsUnresolvedReference() {
        assertEquals("unknown (camper-7)", unresolvableProfileLabel("camper-7"))
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
}
