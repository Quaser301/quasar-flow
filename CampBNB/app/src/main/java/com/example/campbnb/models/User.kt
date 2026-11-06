package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

/**
 * Entity: one app user, identified by their device (US 01.09.01).
 * The document id is the device id, so the same device always finds the same profile.
 *
 * @property deviceId The Android device id this profile belongs to.
 * @property name The user's display name.
 * @property email The user's email address.
 * @property phone The user's phone number (optional).
 * @property roles The roles this user holds.
 * @property notificationsEnabled False if the user opted out of notifications (US 01.10.01).
 */
data class User(
    val deviceId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val roles: List<Role> = emptyList(),
    val notificationsEnabled: Boolean = true
) : Identifiable() {

    init {
        if (deviceId.isNotEmpty()) {
            id = deviceId
        }
    }

    /** True if this user holds [role]. */
    fun hasRole(role: Role): Boolean {
        return roles.contains(role)
    }
}