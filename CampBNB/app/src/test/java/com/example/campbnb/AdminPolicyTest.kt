package com.example.campbnb

import com.example.campbnb.controller.AdminController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.NotificationCommand
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationType
import com.example.campbnb.models.PolicyReason
import com.example.campbnb.models.policyReasonText
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * NOTICE: This file was AI-assisted. The verbatim prompts for this work are recorded in
 * AI_Disclosure/Wyatt_AI_Use.md.
 */

/** A listing store that keeps records in memory and never touches Firestore. */
class FakeListingStore : CampsiteRepository() {
    val deletedIds = mutableSetOf<String>()
    val savedListings = mutableMapOf<String, Campsite>()
    var failNextWrite = false

    override fun deleteCampsite(
        campsiteId: String,
        onComplete: (Result<Exception?>) -> Unit
    ) {
        if (failNextWrite) {
            failNextWrite = false
            onComplete(Result.failure(Exception("offline")))
        } else {
            deletedIds.add(campsiteId)
            onComplete(Result.success(null))
        }
    }

    override fun SaveCampsite(
        campsite: Campsite,
        onComplete: (Result<Exception?>) -> Unit
    ) {
        if (failNextWrite) {
            failNextWrite = false
            onComplete(Result.failure(Exception("offline")))
        } else {
            savedListings[campsite.id] = campsite
            onComplete(Result.success(null))
        }
    }
}

/** A notification store that keeps records in memory and never touches Firestore. */
class NoticeStore : NotificationRepository {
    val saved = mutableMapOf<String, Notification>()

    override fun run(command: NotificationCommand) {
        when (command) {
            is NotificationCommand.Save -> {
                command.records.forEach { saved[it.id] = it }
                command.onDone.invoke(Result.success(Unit))
            }

            is NotificationCommand.Read -> command.onSuccess.invoke(saved.values.toList())

            // Policy cases never follow a user live; the request is accepted and left
            // alone.
            else -> {}
        }
    }
}

class AdminPolicyTest {
    private lateinit var listings: FakeListingStore
    private lateinit var notices: NoticeStore
    private lateinit var adminController: AdminController

    @Before
    fun setup() {
        listings = FakeListingStore()
        notices = NoticeStore()
        adminController = AdminController(listings, NotificationController(notices))
    }

    /** The listing every case below describes: someone else's, with two tags. */
    private fun listedByOther() = Campsite(
        ownerId = "owner1",
        campName = "Pine Hollow",
        campLoc = "somewhere",
        tags = listOf("lakeview", "quiet")
    )

    @Test
    fun removedListing_isToldToItsCreatorWithTheReason() {
        val campsite = listedByOther()
        val reason = PolicyReason.OUTDATED_LISTING
        var done = false
        adminController.removeCampsite(campsite, reason, "admin9") { ok ->
            done = ok
        }

        Assert.assertTrue(done)
        Assert.assertEquals(1, listings.deletedIds.size)
        Assert.assertEquals(campsite.id, listings.deletedIds.first())
        Assert.assertEquals(1, notices.saved.size)

        val notice = notices.saved.values.first()
        Assert.assertEquals("owner1", notice.recipientId)
        Assert.assertEquals("admin9", notice.senderId)
        Assert.assertEquals(NotificationType.OWNER_NOTICE, notice.type)
        Assert.assertTrue(notice.message.contains(policyReasonText(reason)))
        Assert.assertTrue(notice.message.contains("Pine Hollow"))
    }

    @Test
    fun failedRemoval_tellsNobody() {
        listings.failNextWrite = true
        val campsite = listedByOther()
        var done = false
        adminController.removeCampsite(campsite, PolicyReason.NOT_ALLOWED, "admin9") { ok ->
            done = ok
        }

        Assert.assertFalse(done)
        Assert.assertEquals(0, listings.deletedIds.size)
        Assert.assertEquals(0, notices.saved.size)
    }

    @Test
    fun removedTag_leavesTheRestOfTheListingUnderTheSameRecord() {
        val campsite = listedByOther()
        var done = false
        adminController.removeTag(
            campsite,
            "quiet",
            PolicyReason.DUPLICATE_LISTING,
            "admin9"
        ) { ok ->
            done = ok
        }

        Assert.assertTrue(done)
        Assert.assertEquals(1, listings.savedListings.size)

        val kept = listings.savedListings[campsite.id]!!
        Assert.assertEquals(campsite.id, kept.id)
        Assert.assertEquals(listOf("lakeview"), kept.tags)
        Assert.assertEquals("owner1", kept.ownerId)

        val notice = notices.saved.values.first()
        Assert.assertTrue(notice.message.contains("quiet"))
        Assert.assertTrue(notice.message.contains(policyReasonText(PolicyReason.DUPLICATE_LISTING)))
    }

    @Test
    fun tagNotOnTheListing_isRefusedWithoutWritingOrTelling() {
        val campsite = listedByOther()
        var done = false
        adminController.removeTag(campsite, "fireside", PolicyReason.NOT_ALLOWED, "admin9") { ok ->
            done = ok
        }

        Assert.assertFalse(done)
        Assert.assertEquals(0, listings.savedListings.size)
        Assert.assertEquals(0, notices.saved.size)
    }

    @Test
    fun blankTagOnAnUntaggedListing_isRefused() {
        val untagged = Campsite(
            ownerId = "owner1",
            campName = "Bare Rock",
            campLoc = "somewhere",
            tags = emptyList()
        )
        var done = false
        adminController.removeTag(untagged, "", PolicyReason.INCOMPLETE_LISTING, "admin9") { ok ->
            done = ok
        }

        Assert.assertFalse(done)
        Assert.assertEquals(0, listings.savedListings.size)
        Assert.assertEquals(0, notices.saved.size)
    }

    @Test
    fun blankTagOnATaggedListing_isTheSameRefusal() {
        var done = false
        adminController.removeTag(listedByOther(), "", PolicyReason.NOT_ALLOWED, "admin9") { ok ->
            done = ok
        }

        Assert.assertFalse(done)
        Assert.assertEquals(0, listings.savedListings.size)
        Assert.assertEquals(0, notices.saved.size)
    }

    // The notice copy is stated exactly, not just named after: the whole listing and
    // one tag each have their own title and body.
    @Test
    fun removal_notice_uses_the_exact_whole_listing_copy() {
        val reason = PolicyReason.OUTDATED_LISTING
        adminController.removeCampsite(listedByOther(), reason, "admin9") {}

        val notice = notices.saved.values.single()
        Assert.assertEquals("Your campsite was removed", notice.title)
        Assert.assertEquals(
            "Your campsite Pine Hollow was removed from the app because " +
                    policyReasonText(reason) + ".",
            notice.message
        )
    }

    @Test
    fun tag_removal_notice_names_the_tag_and_the_place() {
        val reason = PolicyReason.DUPLICATE_LISTING
        adminController.removeTag(listedByOther(), "quiet", reason, "admin9") {}

        val notice = notices.saved.values.single()
        Assert.assertEquals("A tag was removed from your campsite", notice.title)
        Assert.assertEquals(
            "The tag quiet was removed from your campsite Pine Hollow because " +
                    policyReasonText(reason) + ".",
            notice.message
        )
        Assert.assertEquals("owner1", notice.recipientId)
        Assert.assertEquals("admin9", notice.senderId)
        Assert.assertEquals("Pine Hollow", notice.campsiteName)
    }

    // An un-named listing is still told about, as "the campsite".
    @Test
    fun un_named_listing_is_still_told_about_as_the_campsite() {
        val nameless = Campsite(
            ownerId = "owner1",
            campName = "",
            campLoc = "somewhere"
        )
        val reason = PolicyReason.INCOMPLETE_LISTING
        adminController.removeCampsite(nameless, reason, "admin9") {}

        val notice = notices.saved.values.single()
        Assert.assertEquals("Your campsite was removed", notice.title)
        Assert.assertEquals(
            "Your campsite the campsite was removed from the app because " +
                    policyReasonText(reason) + ".",
            notice.message
        )
        Assert.assertEquals("the campsite", notice.campsiteName)
    }
}
