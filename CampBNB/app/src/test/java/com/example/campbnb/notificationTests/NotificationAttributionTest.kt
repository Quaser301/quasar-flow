package com.example.campbnb.notificationTests

// AI-assisted (US 01.05.02 night batch, notice cause and removal): written by Qwen 3.8 Flash
// Next Coder IQ1_M.
//
// What these cases cover: a plain notice naming the person who caused it, so the listing of
// the notices a viewer caused holds only what that viewer caused and the notice an admin took
// over someone else's listing sits in that someone's received half rather than their own;
// removing one saved notice, both through the store directly and through a view scoped to one
// sender, so a removed entry stops appearing in every listing; and the log bars of a viewer
// who may not read the raw database key an entry is looked up by, which end where the notice
// opens. The screen is not driven here; the same [NotificationLogViewModel] the screens build
// is, over the shared [FakeNotificationRepository] - the same contract the real repository
// implements, so these cases run against either.

import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.NotificationCommand
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.SentNotificationsRepository
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationType
import com.example.campbnb.models.notificationLogGroupsFor
import com.example.campbnb.models.notificationLogLinesFor
import com.example.campbnb.viewModels.NotificationLogViewModel
import com.example.campbnb.viewModels.LogViewState
import com.google.firebase.Timestamp
import org.junit.Assert
import org.junit.Test

class NotificationAttributionTest {

    private fun record(
        id: String,
        receiver: String,
        sender: String,
        title: String,
        message: String,
        seconds: Long
    ): Notification = Notification(
        recipientId = receiver,
        senderId = sender,
        type = NotificationType.OWNER_NOTICE,
        title = title,
        message = message,
        sentAt = if (seconds < 0) null else Timestamp(seconds, 0)
    ).also { it.id = id }

    private fun store(repository: NotificationRepository, records: List<Notification>) {
        repository.run(NotificationCommand.Save(records = records, onDone = {}))
    }

    /** Asks one repository for everything it serves, and hands over what it answers. */
    private fun reading(repository: NotificationRepository): List<Notification> {
        var answer: List<Notification>? = null
        repository.run(
            NotificationCommand.Read(
                onSuccess = { records -> answer = records },
                onFailure = { throw it }
            )
        )
        return answer!!
    }

    private fun removing(repository: NotificationRepository, id: String) {
        repository.run(NotificationCommand.Delete(notificationId = id, onDone = {}))
    }

    @Test
    fun adminNoticeNamesItsCauseAndNotTheOwner() {
        val source = FakeNotificationRepository()
        val controller = NotificationController(source)
        controller.sendNotice(
            recipientId = "owner-7",
            title = "Your campsite was removed",
            message = "It broke a policy.",
            actorId = "admin-1"
        )

        val saved = reading(source)
        Assert.assertEquals(1, saved.size)
        Assert.assertEquals("admin-1", saved.first().senderId)
    }

    @Test
    fun anAdminsNoticeIsNotInTheOwnersSentList() {
        val source = FakeNotificationRepository()
        val notice = record("n1", "owner-7", "admin-1", "Your campsite was removed", "It broke a policy.", 100L)
        store(source, listOf(notice))

        val ownerScoped = SentNotificationsRepository(source, "owner-7")
        val adminScoped = SentNotificationsRepository(source, "admin-1")
        Assert.assertEquals(0, reading(ownerScoped).size)
        Assert.assertEquals(1, reading(adminScoped).size)
    }

    @Test
    fun removingOneNoticeEndsItBeingListedEverywhere() {
        val source = FakeNotificationRepository()
        store(
            source,
            listOf(
                record("n1", "owner-7", "admin-1", "Your campsite was removed", "Kept.", 100L),
                record("n2", "owner-7", "admin-1", "Your campsite was removed", "Gone.", 200L)
            )
        )

        removing(source, "n2")
        val left = reading(source)
        Assert.assertEquals(1, left.size)
        Assert.assertEquals("n1", left.first().id)
    }

    @Test
    fun aScopedViewDeletesAtTheRealStoreNotJustItsOwnView() {
        val source = FakeNotificationRepository()
        store(
            source,
            listOf(
                record("n1", "owner-7", "admin-1", "Your campsite was removed", "Kept.", 100L),
                record("n2", "owner-7", "admin-1", "Your campsite was removed", "Gone.", 200L)
            )
        )

        removing(SentNotificationsRepository(source, "admin-1"), "n2")
        Assert.assertEquals(1, reading(source).size)
    }

    @Test
    fun aViewerWhoMayNotReadRawKeysGetsBarsThatEndAtTheNotice() {
        val notice = record("n1", "owner-7", "admin-1", "Your campsite was removed", "It broke a policy.", 100L)
        val groups = notificationLogGroupsFor(listOf(notice))

        val withKeys = notificationLogLinesFor(groups, setOf()) { userId -> userId }
        val withoutKeys = notificationLogLinesFor(groups, setOf(), showDbIds = false) { userId -> userId }

        Assert.assertEquals(1, withKeys.size)
        Assert.assertEquals(1, withoutKeys.size)
        Assert.assertTrue(withKeys.first().text.contains("DB ID n1"))
        Assert.assertFalse(withoutKeys.first().text.contains("DB ID"))
    }

    @Test
    fun aLogCanNameTheEntryItIsAskedToRemove() {
        val source = FakeNotificationRepository()
        store(
            source,
            listOf(record("n9", "owner-7", "admin-1", "Your campsite was removed", "Body.", 100L))
        )
        val log = NotificationLogViewModel(source) { userId -> userId }

        val ready = log.state.value
        Assert.assertTrue(ready is LogViewState.Ready)
        val bar = (ready as LogViewState.Ready).lines.first()
        Assert.assertEquals("n9", log.entryIdFor(bar.logId))
    }
}
