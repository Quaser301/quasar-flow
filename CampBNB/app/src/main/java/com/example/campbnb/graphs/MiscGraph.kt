package com.example.campbnb.graphs

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.example.campbnb.Screen
import com.example.campbnb.data.AppSettingsService
import com.example.campbnb.data.NotificationPreferencesService
import com.example.campbnb.models.Role
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.screens.PlaceholderTab
import com.example.campbnb.ui.screens.QrOldCodeNoticeScreen
import com.example.campbnb.ui.settings.SettingsScreen
import com.example.campbnb.ui.shared.toUserRole
import com.example.campbnb.viewModels.AppViewSetting
import com.example.campbnb.viewModels.AppViewSettingController
import com.example.campbnb.viewModels.EntrantSettingsController
import com.example.campbnb.viewModels.OwnerSettingsController
import kotlinx.serialization.Serializable


@Serializable
data object MiscGraph


fun NavGraphBuilder.MiscGraph(
    onNavBack: () -> Unit,
    navToCampsiteDetails: (role:UserRole, id: String) -> Unit,

    userModel: UserModel,

    appViewSettings: AppViewSettingController,
    entrantSettings: EntrantSettingsController,
    ownerSettings: OwnerSettingsController,
    notificationPreferences: NotificationPreferencesService,
    appSettings: AppSettingsService,
    userIdForSettings: String,
) {
    composable<Screen.Settings> { entry ->
        // need to know our role so we can show
        // the relevant settings
        val role = entry.toRoute<Screen.Settings>().role
        SettingsScreen(
            back = onNavBack,
            role = role,
            viewSettings = appViewSettings,
            entrantSettings = entrantSettings,
            ownerSettings = ownerSettings,
            notificationPreferences = notificationPreferences,
            appSettings = appSettings,
            userIdForSettings = userIdForSettings
        )
    }

    // Tabs whose real screens do not exist yet.
    composable<Screen.Profile> {
        PlaceholderTab("Profile")
    }

    composable<Screen.Reports> {
        PlaceholderTab("Reports")
    }

    composable<Screen.Flagged> {
        PlaceholderTab("Flagged")
    }

    // The phone's own camera opens a CampBNB code's link here: MainActivity has
    // already resolved the code's text into one of these routes and queued it on
    // pendingScreen, the same bridge a notification tap uses, and the sign-in ran
    // before this first render, so the user's data populates. A live code names
    // its campsite: this invisible typed frame opens the details page and pops
    // itself out of the stack like the reservations bridge does, so the first
    // back press goes to Home, not to this blank frame (a white screen). The role
    // carried into the key is the signed-in user's own privileges, the same
    // derivation the login flow uses.
    composable<Screen.QrLinkRoute> { entry ->
        val campsiteID = entry.toRoute<Screen.QrLinkRoute>().campsiteID
        LaunchedEffect(campsiteID) {
            if (campsiteID.isNotBlank()) {
                val privileges = userModel.currentUser?.roles ?: emptyList()
                val role = when {
                    Role.ADMIN in privileges -> Role.ADMIN
                    Role.OWNER in privileges -> Role.OWNER
                    else -> Role.ENTRANT
                }
                onNavBack()
                navToCampsiteDetails(toUserRole(role), campsiteID)
            }
        }
    }

    // A dead code's link — its stop already past, or no expiry at all — answers
    // the old-code notice in place of any destination; back goes where the link
    // would otherwise have been.
    composable<Screen.OldQRCode> {
        QrOldCodeNoticeScreen(back = onNavBack)
    }
}