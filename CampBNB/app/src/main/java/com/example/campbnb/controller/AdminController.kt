package com.example.campbnb.controller

// AI-assisted (night run, admin policy actions): written by Qwen 3.8 Flash Next Coder
// IQ1_M.

/*
 * AdminController.kt
 *
 * Role: controller for admin removals (US 03.02.01 so far; 03.07.01, 03.01.01, 03.03.01,
 * 03.09.01 and 03.10.01 are meant to be added here the same way) and for the policy
 * actions over someone else's listing. Role checks are not repeated here for the
 * deletions: it asks UserController whether the caller is an admin.
 */

import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.CampsiteRemovalRepository
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.NotificationIntent
import com.example.campbnb.models.PolicyReason
import com.example.campbnb.models.Rating
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.models.ReservationModel
import com.example.campbnb.models.Role
import com.example.campbnb.models.WaitingListEntry
import com.example.campbnb.models.ImageKind
import com.example.campbnb.models.UploadedImage
import com.example.campbnb.models.uploadedImagesFor


/**
 * Control: lets an admin remove things that break app policy.
 *
 * @property userController Answers "is the signed-in user an admin?" and reloads the list of users.
 * @property userRepo Deletes the profile itself.
 * @property campsiteRepo Finds and deletes the profile's ratings.
 * @property lotteryRepo Finds and deletes the profile's waiting-list entries.
 * @property notifier Tells the removed user what happened.
 * @property removalRepo Cancels a campsite's reservations and deletes or updates its lotteries.
 *
 * A policy action over someone else's listing (removing the whole listing or one tag of
 * it) always names an app-policy reason and always tells the listing's own creator what
 * happened and why: the action is not the admin's judgement left to the creator to guess
 * at. Only the storage half of such an action decides its own failure; a notice the
 * creator chose to opt out of still counts as a done action.
 */
class AdminController(
    private val userController: UserController,
    private val userRepo: UserRepository,
    private val campsiteRepo: CampsiteRepository,
    private val lotteryRepo: LotteryRepository,
    private val notifier: NotificationController,
    private val removalRepo: CampsiteRemovalRepository = CampsiteRemovalRepository()
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
        campsiteRepo.deleteCampsite(
            campsiteId = campsite.id,
            onComplete = { result ->
                if (result.isFailure) {
                    onDone(false)
                } else {
                    notifier.notify(
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

        campsiteRepo.SaveCampsite(
            campsite = kept,
            onComplete = { result ->
                if (result.isFailure) {
                    onDone(false)
                } else {
                    notifier.notify(
                        NotificationIntent.CampsiteTagRemoved(campsite, tag, reason, actorUserId)
                    )
                    onDone(true)
                }
            }
        )
    }

    /**
     * Deletes the profile with [userId] and the data that belongs to it (US 03.02.01), then
     * notifies the owner of the profile and reloads the admin's list of users.
     *
     * Refused (nothing is changed) if the signed-in user is not an admin, the id is blank,
     * the admin is trying to delete their own profile, the profile no longer exists, or the
     * profile belongs to another admin. If removing the profile's data fails, the profile
     * itself is kept so the admin can try again.
     *
     * @param userId The id of the profile to delete.
     * @param reason Why it is being deleted. Optional for profiles; shown in the notice.
     * @param onDone Called once with whether it worked and a message the screen can show.
     */
    fun deleteProfile(
        userId: String,
        reason: RemovalReason?,
        onDone: (Boolean, String) -> Unit
    ) {
        if (!userController.isAdmin()) {
            onDone(false, "Only an admin can delete profiles")
            return
        }
        if (userId.isBlank()) {
            onDone(false, "No profile selected")
            return
        }
        if (userId == userController.currentUserId()) {
            onDone(false, "You cannot delete your own profile here")
            return
        }

        // Read the profile fresh from the database, so the admin check does not depend on
        // what the list on screen happened to hold.
        userRepo.findByDeviceId(
            userId,
            onSuccess = { target ->
                if (target == null) {
                    onDone(false, "That profile no longer exists")
                } else if (target.hasRole(Role.ADMIN)) {
                    onDone(false, "Admin profiles cannot be deleted")
                } else {
                    deleteChecked(userId, reason, onDone)
                }
            },
            onFailure = { onDone(false, "Could not check the profile, so it was not deleted") }
        )
    }

    /**
     * Deletes the campsite with [campsiteId] and its ratings (US 03.01.01), then notifies the
     * campsite's owner.
     *
     * Refused (nothing is changed) if the signed-in user is not an admin, the id is blank,
     * or the campsite no longer exists. If removing the ratings fails, the campsite itself
     * is kept so the admin can try again.
     *
     * @param campsiteId The id of the campsite to delete.
     * @param reason Why it is being deleted; shown in the owner's notice.
     * @param onDone Called once with whether it worked and a message the screen can show.
     */
    fun deleteCampsite(
        campsiteId: String,
        reason: RemovalReason?,
        onDone: (Boolean, String) -> Unit
    ) {
        if (!userController.isAdmin()) {
            onDone(false, "Only an admin can delete campsites")
            return
        }
        if (campsiteId.isBlank()) {
            onDone(false, "No campsite selected")
            return
        }

        // Read the campsite fresh from the database: we need its owner to send the notice,
        // and it may already have been deleted.
        campsiteRepo.getById(
            campsiteId,
            onSuccess = { campsite ->
                if (campsite == null) {
                    onDone(false, "That campsite no longer exists")
                } else {
                    deleteCampsiteChecked(campsite, reason, onDone)
                }
            },
            onFailure = { onDone(false, "Could not check the campsite, so it was not deleted") }
        )
    }

    /**
     * Deletes one rating together with the review attached to it (US 03.09.01, 03.10.01), then
     * notifies the user who wrote it.
     *
     * Refused (nothing is changed) if the signed-in user is not an admin, the id is blank,
     * or the rating no longer exists.
     *
     * @param ratingId The id of the rating to delete (see [Rating.makeId]).
     * @param reason Why it is being deleted; shown in the user's notice.
     * @param onDone Called once with whether it worked and a message the screen can show.
     */
    fun deleteRating(
        ratingId: String,
        reason: RemovalReason?,
        onDone: (Boolean, String) -> Unit
    ) {
        if (!userController.isAdmin()) {
            onDone(false, "Only an admin can delete ratings and reviews")
            return
        }
        if (ratingId.isBlank()) {
            onDone(false, "No rating selected")
            return
        }

        // Read the rating fresh from the database: we need its author to send the notice,
        // and it may already have been deleted.
        campsiteRepo.getRatingById(
            ratingId,
            onSuccess = { rating ->
                if (rating == null) {
                    onDone(false, "That rating no longer exists")
                } else {
                    deleteRatingChecked(rating, reason, onDone)
                }
            },
            onFailure = { onDone(false, "Could not check the rating, so it was not deleted") }
        )
    }

    /**
     * Reads every uploaded picture, campsite and profile, for the admin to browse (US 03.06.01).
     *
     * @param onDone Called once with the pictures, or with the reason it could not load.
     */
    fun loadUploadedImages(onDone: (List<UploadedImage>, String?) -> Unit) {
        if (!userController.isAdmin()) {
            onDone(emptyList(), "Only an admin can browse uploaded images")
            return
        }
        campsiteRepo.getAll(
            onSuccess = { campsites ->
                userRepo.getAll(
                    onSuccess = { users -> onDone(uploadedImagesFor(campsites, users), null) },
                    onFailure = { onDone(emptyList(), "Could not load the profile pictures") }
                )
            },
            onFailure = { onDone(emptyList(), "Could not load the campsite pictures") }
        )
    }

    /**
     * Removes one uploaded picture that breaks app policy (US 03.03.01): the picture text is
     * emptied, so it is gone for entrants and owners alike, and whoever uploaded it is told why.
     * The campsite or profile itself is kept.
     *
     * Refused (nothing is changed) if the signed-in user is not an admin, the id is blank, the
     * campsite or profile no longer exists, or it has no picture.
     *
     * @param kind Whether it is a campsite's picture or a profile's.
     * @param sourceId The id of that campsite or profile.
     * @param reason Why it is being removed; shown in the notice.
     * @param onDone Called once with whether it worked and a message the screen can show.
     */
    fun removeImage(
        kind: ImageKind,
        sourceId: String,
        reason: RemovalReason?,
        onDone: (Boolean, String) -> Unit
    ) {
        if (!userController.isAdmin()) {
            onDone(false, "Only an admin can remove images")
            return
        }
        if (sourceId.isBlank()) {
            onDone(false, "No image selected")
            return
        }
        // Read fresh from the database: we need the uploader to send the notice, and the
        // picture may already be gone.
        if (kind == ImageKind.CAMPSITE) {
            campsiteRepo.getById(
                sourceId,
                onSuccess = { campsite ->
                    if (campsite == null) {
                        onDone(false, "That campsite no longer exists")
                    } else if (campsite.campImg.isBlank()) {
                        onDone(false, "That image was already removed")
                    } else {
                        campsiteRepo.saveCampsiteImage(sourceId, "") { result ->
                            if (result.isSuccess) {
                                notifier.sendNotice(
                                    campsite.ownerId,
                                    "Your campsite image was removed",
                                    imageRemovalText("the image of your campsite \"" + campsite.campName + "\"", reason)
                                )
                                onDone(true, "Image removed")
                            } else {
                                onDone(false, "Could not remove the image")
                            }
                        }
                    }
                },
                onFailure = { onDone(false, "Could not check the image, so it was not removed") }
            )
        } else {
            userRepo.findByDeviceId(
                sourceId,
                onSuccess = { user ->
                    if (user == null) {
                        onDone(false, "That profile no longer exists")
                    } else if (user.profileImg.isBlank()) {
                        onDone(false, "That image was already removed")
                    } else {
                        userRepo.saveProfileImage(sourceId, "") { result ->
                            if (result.isSuccess) {
                                notifier.sendNotice(
                                    sourceId,
                                    "Your profile picture was removed",
                                    imageRemovalText("your profile picture", reason)
                                )
                                userController.loadAllUsers()
                                onDone(true, "Image removed")
                            } else {
                                onDone(false, "Could not remove the image")
                            }
                        }
                    }
                },
                onFailure = { onDone(false, "Could not check the image, so it was not removed") }
            )
        }
    }

    /** The message the uploader receives; [what] finishes "An admin removed ...". */
    private fun imageRemovalText(what: String, reason: RemovalReason?): String {
        val base = "An admin removed " + what + " for violating the app policy."
        if (reason == null) {
            return base
        }
        return base + " Reason: " + reason.label + "."
    }

    /** The deletion itself, once the rating is known to exist. */
    private fun deleteRatingChecked(
        rating: Rating,
        reason: RemovalReason?,
        onDone: (Boolean, String) -> Unit
    ) {
        campsiteRepo.deleteRating(rating.id) { result ->
            if (result.isSuccess) {
                sendRatingNotice(rating, reason)
                onDone(true, "Rating and review deleted")
            } else {
                onDone(false, "Could not delete the rating")
            }
        }
    }

    /** Looks up the campsite's name for the notice; a campsite that cannot be read is called "a campsite". */
    private fun sendRatingNotice(rating: Rating, reason: RemovalReason?) {
        campsiteRepo.getById(
            rating.campsiteId,
            onSuccess = { campsite ->
                var campsiteName = "a campsite"
                if (campsite != null) {
                    campsiteName = "\"" + campsite.campName + "\""
                }
                notifier.sendNotice(
                    rating.userId,
                    "Your rating was removed",
                    ratingRemovalText(rating, campsiteName, reason)
                )
            },
            onFailure = {
                notifier.sendNotice(
                    rating.userId,
                    "Your rating was removed",
                    ratingRemovalText(rating, "a campsite", reason)
                )
            }
        )
    }

    /** The message the rating's author receives. */
    private fun ratingRemovalText(rating: Rating, campsiteName: String, reason: RemovalReason?): String {
        var what = "your rating"
        if (rating.review.isNotBlank()) {
            what = "your rating and review"
        }
        val base = "An admin removed " + what + " of " + campsiteName + " for violating the app policy."
        if (reason == null) {
            return base
        }
        return base + " Reason: " + reason.label + "."
    }

    /** The deletion itself, once the campsite is known to exist. */
    private fun deleteCampsiteChecked(
        campsite: Campsite,
        reason: RemovalReason?,
        onDone: (Boolean, String) -> Unit
    ) {
        val job = RemovalJob(campsite, reason, onDone)
        campsiteRepo.getRatingsByCampsite(
            campsite.id,
            onSuccess = { ratings ->
                deleteRatings(ratings, 0) { ratingsGone ->
                    if (!ratingsGone) {
                        onDone(false, "Could not remove this campsite's ratings, so it was not deleted")
                    } else {
                        cancelReservationsOf(job)
                    }
                }
            },
            onFailure = { onDone(false, "Could not remove this campsite's ratings, so it was not deleted") }
        )
    }

    /** Step 2: cancels the campsite's active reservations, queueing a notice for each entrant. */
    private fun cancelReservationsOf(job: RemovalJob) {
        removalRepo.getActiveReservations(
            job.campsite.id,
            onSuccess = { reservations -> cancelReservations(job, reservations, 0) },
            onFailure = { job.onDone(false, "Could not check this campsite's reservations, so it was not deleted") }
        )
    }

    /** Cancels reservations[index] onward, one after another; stops on the first failure. */
    private fun cancelReservations(job: RemovalJob, reservations: List<ReservationModel>, index: Int) {
        if (index >= reservations.size) {
            cleanUpLotteriesOf(job)
            return
        }
        val reservation = reservations[index]
        removalRepo.cancelReservation(reservation.lotteryEntryId) { result ->
            if (result.isSuccess) {
                job.notices.add(
                    PendingNotice(
                        reservation.entrantId,
                        "Your reservation was cancelled",
                        "Your reservation at " + job.campsite.campName + " (" + reservation.startDate +
                                " to " + reservation.endDate + ") was cancelled because an admin " +
                                "removed the campsite from the app."
                    )
                )
                cancelReservations(job, reservations, index + 1)
            } else {
                job.onDone(false, "Could not cancel a reservation, so the campsite was not deleted")
            }
        }
    }

    /** Step 3: finds every lottery that includes the campsite and deals with each one. */
    private fun cleanUpLotteriesOf(job: RemovalJob) {
        lotteryRepo.getAllLotteries(
            onSuccess = { allLotteries ->
                val affected = mutableListOf<Lottery>()
                for (lottery in allLotteries) {
                    if (job.campsite.id in lottery.campsiteIds) {
                        affected.add(lottery)
                    }
                }
                cleanUpLotteries(job, affected, 0)
            },
            onFailure = { job.onDone(false, "Could not check this campsite's lotteries, so it was not deleted") }
        )
    }

    /**
     * Handles lotteries[index] onward, one after another. A lottery for this campsite alone is
     * deleted with its waiting list; a lottery with several campsites just loses this one.
     */
    private fun cleanUpLotteries(job: RemovalJob, lotteries: List<Lottery>, index: Int) {
        if (index >= lotteries.size) {
            finishCampsiteDeletion(job)
            return
        }
        val lottery = lotteries[index]
        lotteryRepo.getEntries(
            lottery.id,
            onSuccess = { entries ->
                if (lottery.campsiteIds.size <= 1) {
                    deleteWholeLottery(job, lottery, entries, lotteries, index)
                } else {
                    removeCampsiteFromLottery(job, lottery, entries, lotteries, index)
                }
            },
            onFailure = { job.onDone(false, "Could not read a lottery's waiting list, so the campsite was not deleted") }
        )
    }

    /** The lottery is only for this campsite: delete its waiting list, then the lottery. */
    private fun deleteWholeLottery(
        job: RemovalJob,
        lottery: Lottery,
        entries: List<WaitingListEntry>,
        lotteries: List<Lottery>,
        index: Int
    ) {
        deleteEntries(entries, 0) { entriesGone ->
            if (!entriesGone) {
                job.onDone(false, "Could not update a lottery, so the campsite was not deleted")
            } else {
                removalRepo.deleteLottery(lottery.id) { result ->
                    if (result.isSuccess) {
                        for (entry in entries) {
                            job.notices.add(
                                PendingNotice(
                                    entry.userId,
                                    "Your lottery entry was removed",
                                    "The lottery for " + job.campsite.campName + " was cancelled " +
                                            "because an admin removed the campsite from the app. " +
                                            "You are no longer on its waiting list."
                                )
                            )
                        }
                        cleanUpLotteries(job, lotteries, index + 1)
                    } else {
                        job.onDone(false, "Could not update a lottery, so the campsite was not deleted")
                    }
                }
            }
        }
    }

    /** The lottery has other campsites too: keep it, but take this campsite out of it. */
    private fun removeCampsiteFromLottery(
        job: RemovalJob,
        lottery: Lottery,
        entries: List<WaitingListEntry>,
        lotteries: List<Lottery>,
        index: Int
    ) {
        val remaining = mutableListOf<String>()
        for (campsiteId in lottery.campsiteIds) {
            if (campsiteId != job.campsite.id) {
                remaining.add(campsiteId)
            }
        }
        // copy() gives the new object a fresh id, so the old id is put back to replace the lottery.
        val updated = lottery.copy(campsiteIds = remaining)
        updated.id = lottery.id

        removalRepo.saveLottery(updated) { result ->
            if (result.isSuccess) {
                for (entry in entries) {
                    job.notices.add(
                        PendingNotice(
                            entry.userId,
                            "A campsite was removed from your lottery",
                            "An admin removed " + job.campsite.campName + " from the app, so it is no " +
                                    "longer part of the lottery you entered. The lottery is still " +
                                    "open for its other campsites."
                        )
                    )
                }
                cleanUpLotteries(job, lotteries, index + 1)
            } else {
                job.onDone(false, "Could not update a lottery, so the campsite was not deleted")
            }
        }
    }

    /** Last step: delete the campsite itself, then send the owner's notice and every queued notice. */
    private fun finishCampsiteDeletion(job: RemovalJob) {
        campsiteRepo.deleteCampsite(job.campsite.id) { result ->
            if (result.isSuccess) {
                notifier.sendNotice(
                    job.campsite.ownerId,
                    "Your campsite was removed",
                    campsiteRemovalText(job.campsite, job.reason)
                )
                for (notice in job.notices) {
                    notifier.sendNotice(notice.recipientId, notice.title, notice.text)
                }
                job.onDone(true, "Campsite deleted")
            } else {
                job.onDone(false, "Could not delete the campsite")
            }
        }
    }

    /** The message the campsite's owner receives. */
    private fun campsiteRemovalText(campsite: Campsite, reason: RemovalReason?): String {
        val base = "An admin removed your campsite \"" + campsite.campName +
                "\" for violating the app policy."
        if (reason == null) {
            return base
        }
        return base + " Reason: " + reason.label + "."
    }

    /** The deletion itself, once the profile is known to exist and not to be an admin's. */
    private fun deleteChecked(
        userId: String,
        reason: RemovalReason?,
        onDone: (Boolean, String) -> Unit
    ) {
        removeProfileData(userId) { dataRemoved ->
            if (!dataRemoved) {
                onDone(false, "Could not remove this profile's data, so it was not deleted")
            } else {
                userRepo.delete(userId) { result ->
                    if (result.isSuccess) {
                        notifier.sendNotice(userId, "Your profile was removed", removalText(reason))
                        userController.loadAllUsers()
                        onDone(true, "Profile deleted")
                    } else {
                        onDone(false, "Could not delete the profile")
                    }
                }
            }
        }
    }

    /** The message the removed user receives. */
    private fun removalText(reason: RemovalReason?): String {
        val base = "An admin removed your profile for violating the app policy."
        if (reason == null) {
            return base
        }
        return base + " Reason: " + reason.label + "."
    }

    /** Deletes the user's ratings, then their waiting-list entries. [onDone] gets true if both worked. */
    private fun removeProfileData(userId: String, onDone: (Boolean) -> Unit) {
        campsiteRepo.getRatingsByUser(
            userId,
            onSuccess = { ratings ->
                deleteRatings(ratings, 0) { ratingsGone ->
                    if (!ratingsGone) {
                        onDone(false)
                    } else {
                        removeWaitingListEntries(userId, onDone)
                    }
                }
            },
            onFailure = { onDone(false) }
        )
    }

    private fun removeWaitingListEntries(userId: String, onDone: (Boolean) -> Unit) {
        lotteryRepo.getEntriesByUser(
            userId,
            onSuccess = { entries ->
                deleteEntries(entries, 0) { entriesGone ->
                    if (!entriesGone) {
                        onDone(false)
                    } else {
                        cancelReservationsOfUser(userId, onDone)
                    }
                }
            },
            onFailure = { onDone(false) }
        )
    }

    /** Cancels the user's upcoming reservations, which frees their dates. */
    private fun cancelReservationsOfUser(userId: String, onDone: (Boolean) -> Unit) {
        removalRepo.getActiveReservationsOfEntrant(
            userId,
            onSuccess = { reservations -> cancelUserReservations(reservations, 0, onDone) },
            onFailure = { onDone(false) }
        )
    }

    /** Cancels reservations[index] onward, one after another; stops and reports false on the first failure. */
    private fun cancelUserReservations(
        reservations: List<ReservationModel>,
        index: Int,
        onDone: (Boolean) -> Unit
    ) {
        if (index >= reservations.size) {
            onDone(true)
            return
        }
        removalRepo.cancelReservation(reservations[index].lotteryEntryId) { result ->
            if (result.isSuccess) {
                cancelUserReservations(reservations, index + 1, onDone)
            } else {
                onDone(false)
            }
        }
    }

    /** Deletes ratings[index] onward, one after another; stops and reports false on the first failure. */
    private fun deleteRatings(ratings: List<Rating>, index: Int, onDone: (Boolean) -> Unit) {
        if (index >= ratings.size) {
            onDone(true)
            return
        }
        campsiteRepo.deleteRating(ratings[index].id) { result ->
            if (result.isSuccess) {
                deleteRatings(ratings, index + 1, onDone)
            } else {
                onDone(false)
            }
        }
    }

    /** Deletes entries[index] onward, one after another; stops and reports false on the first failure. */
    private fun deleteEntries(entries: List<WaitingListEntry>, index: Int, onDone: (Boolean) -> Unit) {
        if (index >= entries.size) {
            onDone(true)
            return
        }
        val entry = entries[index]
        lotteryRepo.removeEntry(entry.lotteryId, entry.userId) { result ->
            if (result.isSuccess) {
                deleteEntries(entries, index + 1, onDone)
            } else {
                onDone(false)
            }
        }
    }
}

/** A notice waiting to be sent once the campsite has actually been deleted. */
private class PendingNotice(val recipientId: String, val title: String, val text: String)

/** Everything the steps of one campsite deletion share. */
private class RemovalJob(
    val campsite: Campsite,
    val reason: RemovalReason?,
    val onDone: (Boolean, String) -> Unit
) {
    val notices = mutableListOf<PendingNotice>()
}
