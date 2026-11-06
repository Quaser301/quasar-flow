package com.example.campbnb.ui.components

import com.example.campbnb.models.Role
import com.example.campbnb.viewModels.AppView

/** Database role to the UI role. */
fun Role.toUserRole(): UserRole = when (this) {
    Role.ENTRANT -> UserRole.CAMPER
    Role.OWNER -> UserRole.OWNER
    Role.ADMIN -> UserRole.ADMIN
}

/** UI role to the database role. */
fun UserRole.toRole(): Role = when (this) {
    UserRole.CAMPER -> Role.ENTRANT
    UserRole.OWNER -> Role.OWNER
    UserRole.ADMIN -> Role.ADMIN
}

/** UI role to the app view it corresponds to. */
fun UserRole.toAppView(): AppView = when (this) {
    UserRole.CAMPER -> AppView.EntrantView
    UserRole.OWNER -> AppView.OwnerView
    UserRole.ADMIN -> AppView.AdminView
}

/** App view to the UI role it corresponds to. */
fun AppView.toUserRole(): UserRole = when (this) {
    AppView.EntrantView -> UserRole.CAMPER
    AppView.OwnerView -> UserRole.OWNER
    AppView.AdminView -> UserRole.ADMIN
}