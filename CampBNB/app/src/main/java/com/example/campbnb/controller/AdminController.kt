package com.example.campbnb.controller

/*
 * AdminController.kt
 *
 * Role: controller for admin removals (US 03.02.01 so far; 03.07.01, 03.01.01, 03.03.01,
 * 03.09.01 and 03.10.01 are meant to be added here the same way).
 * Role checks are not repeated here: it asks UserController whether the caller is an admin.
 */

import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.models.Rating
import com.example.campbnb.models.WaitingListEntry
import com.example.campbnb.models.Role
import com.example.campbnb.models.Campsite
import com.example.campbnb.data.CampsiteRemovalRepository
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.ReservationModel

/**
 * Control: lets an admin remove things that break app policy.
 *
 * @property userController Answers "is the signed-in user an admin?" and reloads the list of users.
 * @property userRepo Deletes the profile itself.
 * @property campsiteRepo Finds and deletes the profile's ratings.
 * @property lotteryRepo Finds and deletes the profile's waiting-list entries.
 * @property notifier Tells the removed user what happened.
 * @property removalRepo Cancels a campsite's reservations and deletes or updates its lotteries.
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

    /** The deletion itself, once the campsite is known to exist. */
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