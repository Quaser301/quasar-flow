package com.example.campbnb.ui.components

// AI-assisted (re-pull of US 03.08.01, shared profile label): written by Qwen 3.8 Flash
// Next Coder IQ1_M. No disclosure-table row: the user asked to stop logging prompts.

import com.example.campbnb.models.User

/**
 * The display label one profile reference takes in a listing a reader cannot resolve.
 *
 * A debug listing holds user ids, not profiles, so the label is looked up in the saved
 * users: the display name when the profile is known and has one, a placeholder when the
 * profile is known but unnamed, and the reference itself when no such profile was found.
 *
 * @param userId The profile reference the listing carries.
 * @param users The saved users the reference is looked up in.
 * @return The label that reference draws.
 */
fun profileLabelOf(userId: String, users: List<User>): String {
    if (userId.isEmpty()) {
        return "(no profile)"
    }
    val user = users.firstOrNull { it.id == userId }
    if (user == null) {
        return "unknown ($userId)"
    }
    val name = user.name.trim()
    if (name.isEmpty()) {
        return "(no name)"
    }
    return name
}

/** Stable resolver for a listing that has no saved users to look up. */
val unresolvableProfileLabel: (String) -> String = { userId -> profileLabelOf(userId, emptyList()) }
