// ===== ui/shared/RoleMapping.kt =====
package com.example.campbnb.ui.shared

import com.example.campbnb.models.Role
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.viewModels.AppView

/** Converts a stored [Role] to the [UserRole] the home screen uses to pick its tabs. */
fun toUserRole(role: Role): UserRole {
    if (role == Role.OWNER) {
        return UserRole.OWNER
    }
    if (role == Role.ADMIN) {
        return UserRole.ADMIN
    }
    return UserRole.CAMPER
}

/** Converts the home screen's [UserRole] back to a stored [Role]. */
fun toRole(userRole: UserRole): Role {
    if (userRole == UserRole.OWNER) {
        return Role.OWNER
    }
    if (userRole == UserRole.ADMIN) {
        return Role.ADMIN
    }
    return Role.ENTRANT
}

/** The [AppView] to switch the app to when a user enters as [role]. */
fun viewFor(role: Role): AppView {
    if (role == Role.OWNER) {
        return AppView.OwnerView
    }
    if (role == Role.ADMIN) {
        return AppView.AdminView
    }
    return AppView.EntrantView
}

/** The [UserRole] whose tabs and buttons go with this app view. */
fun roleFor(view: AppView): UserRole {
    if (view == AppView.OwnerView) {
        return UserRole.OWNER
    }
    if (view == AppView.AdminView) {
        return UserRole.ADMIN
    }
    return UserRole.CAMPER
}