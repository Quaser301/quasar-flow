package com.example.campbnb.models

/*
 * RemovalReason.kt
 *
 * Role: The app-policy violations an admin can pick when removing something
 * (a profile, an owner, a campsite, a rating, a review or an image). This list is shared by
 * every admin removal flow (US 03.01.01, 03.02.01, 03.03.01, 03.07.01, 03.09.01, 03.10.01).
 *
 * Outstanding issues: the wording of the reasons is a placeholder until further implementation
 */

/**
 * Why an admin removed something.
 *
 * @property label The text shown to the admin in the reason picker and to the affected
 * user in their notice.
 */
enum class RemovalReason(val label: String) {
    INAPPROPRIATE_CONTENT("Inappropriate content"),
    HARASSMENT("Harassment or abuse"),
    SPAM_OR_FAKE("Spam or fake information"),
    FRAUD("Fraud or scam"),
    OTHER("Other policy violation")
}