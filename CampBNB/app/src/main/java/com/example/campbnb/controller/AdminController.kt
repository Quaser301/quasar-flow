package com.example.campbnb.controller

/*
 * AdminController.kt
 *
 * Role: controller for admin removals (US 03.02.01 so far; 03.07.01, 03.01.01, 03.03.01,
 * 03.09.01 and 03.10.01 are meant to be added here the same way).
 * Role checks are not repeated here: it asks UserController whether the caller is an admin.
 *
 * Outstanding issues:
 *  - A deleted profile's reservations and messages are not removed yet. Only its ratings
 *    and waiting-list entries are.
 *  - The deleted user sees the notice only after they sign up again (the notice is saved
 *    under their device id).
 */

import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.models.Rating
import com.example.campbnb.models.WaitingListEntry
import com.example.campbnb.models.Role

/**
 * Control: lets an admin remove things that break app policy.
 *
 * @property userController Answers "is the signed-in user an admin?" and reloads the list of users.
 * @property userRepo Deletes the profile itself.
 * @property campsiteRepo Finds and deletes the profile's ratings.
 * @property lotteryRepo Finds and deletes the profile's waiting-list entries.
 * @property notifier Tells the removed user what happened.
 */
class AdminController(
    private val userController: UserController,
    private val userRepo: UserRepository,
    private val campsiteRepo: CampsiteRepository,
    private val lotteryRepo: LotteryRepository,
    private val notifier: NotificationController
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
            onSuccess = { entries -> deleteEntries(entries, 0, onDone) },
            onFailure = { onDone(false) }
        )
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