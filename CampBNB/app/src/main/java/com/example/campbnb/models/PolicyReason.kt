package com.example.campbnb.models

// AI-assisted (night run, admin policy vocabulary): written by Qwen 3.8 Flash Next Coder
// IQ1_M.

/**
 * The reason an admin names when the app's own policy removes part of someone else's
 * listing.
 *
 * A policy action never happens without one of these, because the action is not the
 * admin's judgement left to the site's creator to guess at: the named reason is what the
 * creator is told, so the reason's words belong here, next to the reason itself.
 */
enum class PolicyReason {
    /** The listing repeats another campsite already on the app. */
    DUPLICATE_LISTING,
    /** The listing offers something the app does not allow. */
    NOT_ALLOWED,
    /** The listing's details no longer hold: moved, closed, or mis-stated. */
    OUTDATED_LISTING,
    /** The listing was never complete enough to be bookable. */
    INCOMPLETE_LISTING
}

/** The words the reason is told in, as part of one of the app's own notices. */
fun policyReasonText(reason: PolicyReason): String = when (reason) {
    PolicyReason.DUPLICATE_LISTING -> "it repeated another campsite already on the app"
    PolicyReason.NOT_ALLOWED -> "it offers something the app does not allow"
    PolicyReason.OUTDATED_LISTING -> "its details no longer hold"
    PolicyReason.INCOMPLETE_LISTING -> "it was never complete enough to book"
}

/** The short name the reason is chosen by in a menu. */
fun policyReasonLabel(reason: PolicyReason): String = when (reason) {
    PolicyReason.DUPLICATE_LISTING -> "Repeated listing"
    PolicyReason.NOT_ALLOWED -> "Not allowed on the app"
    PolicyReason.OUTDATED_LISTING -> "Out of date"
    PolicyReason.INCOMPLETE_LISTING -> "Never complete"
}
