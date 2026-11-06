package com.example.campbnb.controller

import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel

// Todo: deleting a profile does not yet delete the user's waiting-list entries, ratings or reservations.

// Prompted Claude Sonnet 5.5 for UserController implementation, providing it the relevant constraints
// and design decisions.

/**
 * Control: identifies the user by device, signs them up, grants roles, and manages the
 * profile (US 01.09.01, 01.07.03, 01.08.01, 01.10.01, 03.05.01). It is also the
 * notification opt-out check.
 */
class UserController(
    private val model: UserModel,
    private val repo: UserRepository
) : OptOutChecker {

    /**
     * Looks up the user for this device. Calls [onFound] with the user (and sets it as the
     * current user), [onNotFound] for a new device, or [onError] if the lookup fails.
     */
    fun identify(
        deviceId: String,
        onFound: (User) -> Unit,
        onNotFound: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (deviceId.isBlank()) {
            onError("Could not read this device's id")
            return
        }
        repo.findByDeviceId(
            deviceId,
            onSuccess = { user ->
                if (user == null) {
                    onNotFound()
                } else {
                    model.setCurrentUser(user)
                    onFound(user)
                }
            },
            onFailure = { error -> onError("Could not reach the server: " + error.message) }
        )
    }

    /** Creates a profile for this device with no roles yet. [onDone] gets true on success. */
    fun register(
        deviceId: String,
        name: String,
        email: String,
        phone: String = "",
        onDone: (Boolean) -> Unit
    ) {
        val problem = validateProfile(name, email)
        if (problem != null) {
            model.setStatusMessage(problem)
            onDone(false)
            return
        }
        val user = User(
            deviceId = deviceId,
            name = name.trim(),
            email = email.trim(),
            phone = phone.trim()
        )
        saveUser(user, "Profile created", "Could not create your profile", onDone)
    }

    /**
     * Adds [role] to the user and saves it. ENTRANT and OWNER can be taken freely; ADMIN
     * is refused unless the user already holds it. [onDone] gets true if the role is usable.
     */
    fun chooseRole(role: Role, onDone: (Boolean) -> Unit) {
        val user = model.currentUser
        if (user == null) {
            model.setStatusMessage("Please start again")
            onDone(false)
            return
        }
        if (user.hasRole(role)) {
            onDone(true)
            return
        }
        if (role == Role.ADMIN) {
            model.setStatusMessage("This device is not registered as an admin")
            onDone(false)
            return
        }
        val newRoles = user.roles.toMutableList()
        newRoles.add(role)
        saveUser(user.copy(roles = newRoles), "Role added", "Could not save your role", onDone)
    }

    /** Updates the signed-in user's name, email and phone (US 01.07.03). */
    fun updateProfile(name: String, email: String, phone: String, onDone: (Boolean) -> Unit) {
        val user = model.currentUser
        if (user == null) {
            model.setStatusMessage("Please start again")
            onDone(false)
            return
        }
        val problem = validateProfile(name, email)
        if (problem != null) {
            model.setStatusMessage(problem)
            onDone(false)
            return
        }
        val updated = user.copy(name = name.trim(), email = email.trim(), phone = phone.trim())
        saveUser(updated, "Profile saved", "Could not save your profile", onDone)
    }

    /** Turns notifications on or off for the signed-in user (US 01.10.01). */
    fun setNotificationsEnabled(enabled: Boolean, onDone: (Boolean) -> Unit) {
        val user = model.currentUser
        if (user == null) {
            model.setStatusMessage("Please start again")
            onDone(false)
            return
        }
        val updated = user.copy(notificationsEnabled = enabled)
        saveUser(updated, "Notification setting saved", "Could not save the setting", onDone)
    }

    /** Deletes the signed-in user's profile (US 01.08.01). */
    fun deleteProfile(onDone: (Boolean) -> Unit) {
        val user = model.currentUser
        if (user == null) {
            onDone(false)
            return
        }
        repo.delete(
            user.id,
            onComplete = { result ->
                if (result.isSuccess) {
                    model.setCurrentUser(null)
                    model.setStatusMessage("Profile deleted")
                    onDone(true)
                } else {
                    model.setStatusMessage("Could not delete your profile")
                    onDone(false)
                }
            }
        )
    }

    /** Loads every user into the model for the admin (US 03.05.01). */
    fun loadAllUsers() {
        repo.getAll(
            onSuccess = { users -> model.setAllUsers(users) },
            onFailure = { error -> model.setStatusMessage("Could not load users: " + error.message) }
        )
    }

    /**
     * Opt-out check for NotificationController: returns only the ids of users who have
     * not turned notifications off. If the check fails, nobody is allowed, so a failure
     * can never push to someone who opted out.
     */
    override fun filterAllowed(userIds: List<String>, onResult: (List<String>) -> Unit) {
        repo.getOptedOut(
            onSuccess = { optedOut ->
                val blocked = mutableSetOf<String>()
                for (user in optedOut) {
                    blocked.add(user.id)
                }
                val allowed = mutableListOf<String>()
                for (id in userIds) {
                    if (!blocked.contains(id)) {
                        allowed.add(id)
                    }
                }
                onResult(allowed)
            },
            onFailure = { onResult(emptyList()) }
        )
    }

    /** Returns a message describing what is wrong with the name or email, or null if both are fine. */
    fun validateProfile(name: String, email: String): String? {
        if (name.trim().isEmpty()) {
            return "Please enter a name"
        }
        if (!isValidEmail(email)) {
            return "Please enter a valid email address"
        }
        return null
    }

    private fun saveUser(
        user: User,
        successMessage: String,
        errorMessage: String,
        onDone: (Boolean) -> Unit
    ) {
        repo.save(
            user,
            onComplete = { result ->
                if (result.isSuccess) {
                    model.setCurrentUser(user)
                    model.setStatusMessage(successMessage)
                    onDone(true)
                } else {
                    model.setStatusMessage(errorMessage)
                    onDone(false)
                }
            }
        )
    }

    companion object {
        /** True for text like name@site.com: something before the @, and a dot with text on both sides after it. */
        fun isValidEmail(email: String): Boolean {
            val text = email.trim()
            val at = text.indexOf("@")
            if (at < 1) {
                return false
            }
            val dot = text.indexOf(".", at)
            return dot > at + 1 && dot < text.length - 1
        }
    }
}