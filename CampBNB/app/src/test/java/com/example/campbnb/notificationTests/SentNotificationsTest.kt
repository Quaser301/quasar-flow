package com.example.campbnb.notificationTests

// AI-assisted (US 02.10.02, owner-sent correspondence): written by Qwen 3.8 Flash Next Coder
// IQ1_M.
//
// What these cases cover: the ideal flow (records caused by two users, one viewer's list
// holding only what they caused and keeping the newest-first order the caller gave, a log
// opened through the scoped view drawing only their entries), the extremes (nothing saved
// at all, one sender across sixty records, a notice whose only recipient is its sender),
// wrongly entered data (a blank sender name, a sender with no records, a record the app
// created on its own, a scoped read that fails), and the parts of the repository contract
// a scoped view must not change (saving still stores every record, marking and listening
// still reach the source). The screen is not driven here; the same [NotificationLogViewModel]
// the screens build is, over the shared [FakeNotificationRepository] - the same contract the
// real repository implements, so these cases run against either. The cases keep what the
// fake holds between them by saving explicitly, so nothing here depends on an earlier case.

import com.example.campbnb.data.NotificationUpdate
import com.example.campbnb.data.NotificationCommand
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.SentNotificationsRepository
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationStatus
import com.example.campbnb.models.NotificationType
import com.example.campbnb.models.notificationLogKeyOf
import com.example.campbnb.models.sentNotificationsFor
import com.example.campbnb.viewModels.LogViewState
import com.example.campbnb.viewModels.NotificationLogViewModel
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentNotificationsTest {
    private val source = FakeNotificationRepository()

    private fun record(
        id: String,
        receiver: String,
        sender: String,
        type: NotificationType,
        title: String,
        message: String,
        seconds: Long
    ): Notification = Notification(
        recipientId = receiver,
        senderId = sender,
        type = type,
        title = title,
        message = message,
        sentAt = if (seconds < 0) null else Timestamp(seconds, 0)
    ).also { it.id = id }

    /** Asks one repository for everything it serves, and hands over what it answers. */
    private fun serving(
        repository: NotificationRepository,
        onSuccess: (List<Notification>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        repository.run(NotificationCommand.Read(onSuccess = onSuccess, onFailure = onFailure))
    }

    /** Keeps records through whichever repository the case is exercising. */
    private fun store(
        repository: NotificationRepository,
        records: List<Notification>
    ) = repository.run(NotificationCommand.Save(records = records, onDone = {}))

    /** The notices one viewer caused, out of what a repository holds. */
    private fun savedById(sender: String): List<Notification> {
        var listed: List<Notification>? = null
        serving(
            SentNotificationsRepository(source, sender),
            onSuccess = { records -> listed = records },
            onFailure = { _ -> listed = null }
        )
        return listed ?: emptyList()
    }

    /** What one repository serves in one read. */
    private fun servedBy(repository: NotificationRepository): List<Notification> {
        var listed: List<Notification>? = null
        serving(
            repository,
            onSuccess = { records -> listed = records },
            onFailure = { _ -> listed = null }
        )
        return listed ?: emptyList()
    }

    @Test
    fun sentRecordsMatchOnlyTheirOwnSender() {
        val caused = record(
            "o1", "camper-1", "owner-9", NotificationType.OWNER_NOTICE, "Camp closing",
            "The camp closes Friday.", 300
        )
        val another = record(
            "o2", "camper-2", "owner-9", NotificationType.MESSAGE, "New message",
            "You received a new message: hi", 100
        )
        val causedBySomeoneElse = record(
            "c1", "owner-9", "camper-3", NotificationType.CANCELLATION, "Cancelled",
            "Your reservation was cancelled.", 200
        )

        val sent = sentNotificationsFor(listOf(caused, causedBySomeoneElse, another), "owner-9")

        assertEquals(2, sent.count())
        assertEquals(listOf("o1", "o2"), sent.map { it.id })
    }

    @Test
    fun appMadeRecordsBelongToNoSender() {
        val madeByApp = record(
            "s1", "camper-1", "", NotificationType.CONFIRMATION, "Confirmed",
            "Your reservation has been confirmed.", 100
        )

        assertEquals(0, sentNotificationsFor(listOf(madeByApp), "owner-9").count())
        assertEquals(0, sentNotificationsFor(listOf(madeByApp), "camper-1").count())
    }

    @Test
    fun blankSenderNameMatchesNothing() {
        val caused = record(
            "o1", "camper-1", "owner-9", NotificationType.OWNER_NOTICE, "Camp closing",
            "The camp closes Friday.", 300
        )

        assertEquals(0, sentNotificationsFor(listOf(caused), "").count())
        assertEquals(0, sentNotificationsFor(listOf(caused), "   ").count())
    }

    @Test
    fun nothingSavedMatchesNothing() {
        assertEquals(0, sentNotificationsFor(emptyList(), "owner-9").count())
    }

    @Test
    fun senderWithNoRecordsReadsEmpty() {
        val caused = record(
            "o1", "camper-1", "owner-9", NotificationType.OWNER_NOTICE, "Camp closing",
            "The camp closes Friday.", 300
        )

        assertEquals(0, sentNotificationsFor(listOf(caused), "camper-40").count())
    }

    @Test
    fun aNoticeToItsOwnSenderStillCounts() {
        val toHerself = record(
            "o1", "owner-9", "owner-9", NotificationType.MESSAGE, "New message",
            "You received a new message: hi", 100
        )

        assertEquals(1, sentNotificationsFor(listOf(toHerself), "owner-9").count())
    }

    @Test
    fun aNameOnlyDifferingByCaseMatchesNothing() {
        val caused = record(
            "o1", "camper-1", "owner-9", NotificationType.OWNER_NOTICE, "Camp closing",
            "The camp closes Friday.", 300
        )

        assertEquals(0, sentNotificationsFor(listOf(caused), "OWNER-9").count())
    }

    @Test
    fun theCallersOrderSurvivesTheFilter() {
        val newest = record(
            "n1", "camper-1", "owner-9", NotificationType.OWNER_NOTICE, "Newest",
            "Newest notice.", 300
        )
        val middle = record(
            "m1", "camper-2", "camper-3", NotificationType.CANCELLATION, "Other",
            "Other notice.", 200
        )
        val oldest = record(
            "o1", "camper-4", "owner-9", NotificationType.OWNER_NOTICE, "Oldest",
            "Oldest notice.", 100
        )

        val sent = sentNotificationsFor(listOf(newest, middle, oldest), "owner-9")

        assertEquals(2, sent.count())
        assertEquals(listOf("n1", "o1"), sent.map { it.id })
        assertEquals("Newest", sent.first().title)
    }

    @Test
    fun sixtyRecordsKeepOnlyTheirOwn() {
        val caused = (1..40).map { position ->
            record(
                "o$position", "camper-$position", "owner-9", NotificationType.OWNER_NOTICE,
                "Camp closing", "The camp closes Friday.", 100
            )
        }
        val causedByOthers = (1..20).map { position ->
            record(
                "c$position", "owner-9", "camper-$position", NotificationType.CANCELLATION,
                "Cancelled", "Your reservation was cancelled.", 200
            )
        }

        val sent = sentNotificationsFor(caused + causedByOthers, "owner-9")

        assertEquals(40, sent.count())
        assertEquals(1, sent.map { it.senderId }.distinct().count())
        assertEquals("owner-9", sent.map { it.senderId }.distinct().first())
        assertEquals(0, sent.count { it.recipientId == "owner-9" })
    }

    @Test
    fun scopedReadServesOnlyTheirOwnRecords() {
        source.forgetSaved()
        val caused = record(
            "o1", "camper-1", "owner-9", NotificationType.OWNER_NOTICE, "Camp closing",
            "The camp closes Friday.", 300
        )
        val causedBySomeoneElse = record(
            "c1", "owner-9", "camper-3", NotificationType.CANCELLATION, "Cancelled",
            "Your reservation was cancelled.", 200
        )
        store(source, listOf(caused, causedBySomeoneElse))

        assertEquals(listOf("o1"), savedById("owner-9").map { it.id })
        assertEquals(1, savedById("owner-9").count())
        assertEquals(2, servedBy(source).count())
    }

    @Test
    fun scopedWriteStillStoresEveryRecord() {
        source.forgetSaved()
        val caused = record(
            "o1", "camper-1", "owner-9", NotificationType.OWNER_NOTICE, "Camp closing",
            "The camp closes Friday.", 300
        )
        val madeByApp = record(
            "s1", "camper-1", "", NotificationType.CONFIRMATION, "Confirmed",
            "Your reservation has been confirmed.", 100
        )

        store(SentNotificationsRepository(source, "owner-9"), listOf(caused, madeByApp))

        assertEquals(2, servedBy(source).count())
        assertEquals(listOf("o1"), savedById("owner-9").map { it.id })
    }

    @Test
    fun scopedFailureStillReachesTheFailure() {
        source.forgetSaved()
        val caused = record(
            "o1", "camper-1", "owner-9", NotificationType.OWNER_NOTICE, "Camp closing",
            "The camp closes Friday.", 300
        )
        store(source, listOf(caused))
        source.failNextReadAll = true

        var served: List<Notification>? = null
        var failure: String? = null
        serving(
            SentNotificationsRepository(source, "owner-9"),
            onSuccess = { records -> served = records },
            onFailure = { error -> failure = error.message }
        )

        assertTrue(served == null)
        assertEquals("fake read failure", failure)
    }

    @Test
    fun scopedMarkReadReachesTheSource() {
        source.forgetSaved()
        val caused = record(
            "o1", "camper-1", "owner-9", NotificationType.OWNER_NOTICE, "Camp closing",
            "The camp closes Friday.", 300
        )
        store(source, listOf(caused))

        SentNotificationsRepository(source, "owner-9").run(
            NotificationCommand.MarkRead(notificationId = "o1", onDone = {})
        )

        assertEquals(NotificationStatus.READ, source.saved["o1"]?.status)
    }

    @Test
    fun scopedListenerStillHearsTheSource() {
        val caused = record(
            "o1", "camper-1", "owner-9", NotificationType.OWNER_NOTICE, "Camp closing",
            "The camp closes Friday.", 300
        )
        var updatesHeard = 0
        var stopsHeard = 0
        var stop: (() -> Unit)? = null
        SentNotificationsRepository(source, "owner-9").run(
            NotificationCommand.Watch(
                userId = "camper-1",
                onUpdate = { _ -> updatesHeard++ },
                onError = { _ -> stopsHeard++ },
                onListening = { handle -> stop = handle }
            )
        )

        source.emit(NotificationUpdate(listOf(caused), listOf(caused)))
        assertTrue(1 == updatesHeard)

        stop?.invoke()
        source.emit(NotificationUpdate(listOf(caused), listOf(caused)))
        assertEquals(1, updatesHeard)
    }

    @Test
    fun logThroughScopedViewDrawsOnlyTheirOwnEntries() {
        source.forgetSaved()
        val firstNotice = record(
            "o1", "camper-1", "owner-9", NotificationType.OWNER_NOTICE, "Camp closing",
            "The camp closes Friday.", 300
        )
        val secondNotice = record(
            "o2", "camper-2", "owner-9", NotificationType.OWNER_NOTICE, "Rates updated",
            "Nightly rates changed.", 100
        )
        val causedBySomeoneElse = record(
            "c1", "owner-9", "camper-3", NotificationType.CANCELLATION, "Cancelled",
            "Your reservation was cancelled.", 200
        )
        store(source, listOf(firstNotice, secondNotice, causedBySomeoneElse))

        val log = NotificationLogViewModel(SentNotificationsRepository(source, "owner-9")) { userId -> userId }
        val shown = log.state.value as LogViewState.Ready

        assertEquals(2, shown.logs.count())
        assertTrue(shown.logs.all { it.senderId == "owner-9" })
        assertEquals("Camp closing", shown.logs.first().title)
        assertEquals(2, shown.lines.count())
        assertEquals(0, shown.lines.count { notificationLogKeyOf(causedBySomeoneElse) == it.logId })
    }

    @Test
    fun scopedLogStillGroupsOneBroadcastPerEntry() {
        source.forgetSaved()
        val winners = (1..3).map { position ->
            record(
                "w$position", "camper-$position", "owner-9", NotificationType.WIN,
                "You won!", "Selected in the draw.", 160
            )
        }
        store(source, winners)

        val log = NotificationLogViewModel(SentNotificationsRepository(source, "owner-9")) { userId -> userId }
        log.toggleLog(notificationLogKeyOf(winners.first()))
        val shown = log.state.value as LogViewState.Ready

        assertEquals(1, shown.logs.count())
        assertEquals(3, shown.logs.first().recipientIds.count())
        assertEquals(6, shown.lines.count())
    }

    @Test
    fun scopedLogWithNothingTheirOwnDrawsNoEntries() {
        source.forgetSaved()
        val causedBySomeoneElse = record(
            "c1", "owner-9", "camper-3", NotificationType.CANCELLATION, "Cancelled",
            "Your reservation was cancelled.", 200
        )
        store(source, listOf(causedBySomeoneElse))

        val log = NotificationLogViewModel(SentNotificationsRepository(source, "owner-9")) { userId -> userId }
        val shown = log.state.value as LogViewState.Ready

        assertEquals(0, shown.logs.count())
        assertEquals(0, shown.lines.count())
    }
}
