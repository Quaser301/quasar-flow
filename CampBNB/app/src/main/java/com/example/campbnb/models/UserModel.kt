package com.example.campbnb.models

/**
 * Observable state for users: the signed-in user, every user (for the admin), and the
 * latest status message.
 */
class UserModel : ObservableModel<UserModel>() {
    var currentUser: User? = null
        private set
    var allUsers: List<User> = emptyList()
        private set
    var statusMessage: String = ""
        private set

    /** Records the signed-in user; null means signed out. */
    fun setCurrentUser(user: User?) {
        currentUser = user
        notifyViews()
    }

    /** Replaces the full user list shown on the admin profiles screen. */
    fun setAllUsers(list: List<User>) {
        allUsers = list
        notifyViews()
    }

    /** Records the last load failure message for the user screens to show. */
    fun setStatusMessage(message: String) {
        statusMessage = message
        notifyViews()
    }

    /** The signed-in user's id, or an empty string if nobody is signed in yet. */
    fun currentUserId(): String {
        val user = currentUser
        if (user == null) {
            return ""
        }
        return user.id
    }
}