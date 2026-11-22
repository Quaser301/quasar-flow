package com.example.campbnb.controller

// AI-assisted (night run, admin policy actions): written by Qwen 3.8 Flash Next Coder
// IQ1_M.

import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.NotificationIntent
import com.example.campbnb.models.PolicyReason

/**
 * Control: an admin's policy actions over someone else's listing.
 *
 * A policy action always names an app-policy reason, and always tells the listing's own
 * creator what happened and why: the action is not the admin's judgement left to the
 * creator to guess at. Only the storage half of each action decides its own failure; a
 * notice the creator chose to opt out of still counts as a done action.
 */
class AdminController(
    private val campsiteRepository: CampsiteRepository,
    private val notificationController: NotificationController
) {
    /**
     * Removes a whole listing: the record is deleted and the listing's creator is told the
     * reason. [onDone] holds true once the record is gone; a store failure holds false
     * and no notice is made for an action that did not happen.
     */
    fun removeCampsite(
        campsite: Campsite,
        reason: PolicyReason,
        actorUserId: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        campsiteRepository.deleteCampsite(
            campsiteId = campsite.id,
            onComplete = { result ->
                if (result.isFailure) {
                    onDone(false)
                } else {
                    notificationController.notify(
                        NotificationIntent.CampsitePolicyRemoved(campsite, reason, actorUserId)
                    )
                    onDone(true)
                }
            }
        )
    }

    /**
     * Removes one tag from a listing and saves the rest of it unchanged: a tag the listing
     * does not carry is refused with [onDone] false, and nothing is written for it. The
     * saved listing keeps the record's id, so this edits the listing rather than creating
     * a second one. A removed tag is told to the listing's creator with the reason, like
     * the whole-listing action.
     */
    fun removeTag(
        campsite: Campsite,
        tag: String,
        reason: PolicyReason,
        actorUserId: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        if (tag !in campsite.tags) {
            onDone(false)
            return
        }

        val kept = Campsite(
            ownerId = campsite.ownerId,
            campName = campsite.campName,
            campLoc = campsite.campLoc,
            campDesc = campsite.campDesc,
            campAmenities = campsite.campAmenities,
            geoLocation = campsite.geoLocation,
            campImg = campsite.campImg,
            maxOccupancy = campsite.maxOccupancy,
            tags = campsite.tags.filter { it != tag },
            cancelPolicy = campsite.cancelPolicy,
            campPrice = campsite.campPrice,
            availableStartDate = campsite.availableStartDate,
            availableEndDate = campsite.availableEndDate,
            blockedDates = campsite.blockedDates
        )
        // Same record, one tag fewer: the id is carried over because an edit must not
        // open a second listing under a new one.
        kept.id = campsite.id

        campsiteRepository.SaveCampsite(
            campsite = kept,
            onComplete = { result ->
                if (result.isFailure) {
                    onDone(false)
                } else {
                    notificationController.notify(
                        NotificationIntent.CampsiteTagRemoved(campsite, tag, reason, actorUserId)
                    )
                    onDone(true)
                }
            }
        )
    }
}
