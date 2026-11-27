package com.example.campbnb.graphs

import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.campbnb.Screen
import com.example.campbnb.controller.UserController
import com.example.campbnb.database.Database
import com.example.campbnb.data.AppSettingsService
import com.example.campbnb.data.NotificationPreferencesService
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.Role
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.screens.ProfileScreen
import com.example.campbnb.ui.screens.PlaceholderTab
import com.example.campbnb.ui.shared.observeModel
import com.example.campbnb.ui.shared.toUserRole
import com.example.campbnb.viewModels.AppViewSettingController
import com.example.campbnb.viewModels.EntrantSettingsController
import com.example.campbnb.viewModels.OwnerSettingsController


fun NavGraphBuilder.MiscGraph(
    onNavBack: () -> Unit,
    navToCampsiteDetails: (role:UserRole, id: String) -> Unit,
    navToMyCampsites: () -> Unit,
    navToStart: () -> Unit,

    userModel: UserModel,

    appViewSettings: AppViewSettingController,
    entrantSettings: EntrantSettingsController,
    ownerSettings: OwnerSettingsController,
    notificationPreferences: NotificationPreferencesService,
    appSettings: AppSettingsService,
    userIdForSettings: String,
) {
    // The Settings screen is no longer a screen of its own: the whole settings
    // surface — a role's own settings, the app view switch and the app policy —
    // is drawn on the Profile view below, which is why the settings controllers
    // are handed to that mount.
    // The signed-in user's own profile (US 03.09.01, US 03.10.01): the three shown
    // fields are written through the database and the delete drops the profile and
    // returns to the start. The role the profile shows is the signed-in user's own
    // privileges, the same derivation the sign-in and the code links use, so the
    // profile never shows a role the user does not hold. The signed-in user's own
    // settings are shown here too, which is why the settings controllers are handed
    // to this mount rather than to the Settings screen.
    composable<Screen.Profile> {
        val context = LocalContext.current
        var isSaving by remember { mutableStateOf(false) }
        var isDeleting by remember { mutableStateOf(false) }
        val userController = remember { UserController(userModel, UserRepository()) }
        // Redraw when the model changes, so a new or removed profile image shows right away.
        observeModel(userModel)
        val privileges = userModel.currentUser?.roles ?: emptyList()
        val role = when {
            Role.ADMIN in privileges -> Role.ADMIN
            Role.OWNER in privileges -> Role.OWNER
            else -> Role.ENTRANT
        }
        ProfileScreen(
            role = toUserRole(role),
            initialName = userModel.currentUser?.name ?: "",
            initialEmail = userModel.currentUser?.email ?: "",
            initialPhone = userModel.currentUser?.phone ?: "",
            profileImage = userModel.currentUser?.profileImg ?: "",
            isSaving = isSaving,
            isDeleting = isDeleting,
            onSave = { name, email, phone ->
                val current = userModel.currentUser
                when {
                    current == null || isSaving -> Unit

                    current.name == name && current.email == email && current.phone == phone ->
                        Toast.makeText(context, "No changes to save", Toast.LENGTH_SHORT).show()

                    else -> {
                        isSaving = true
                        // Only these three fields are written to Firestore
                        val changes = mapOf(
                            "name" to name,
                            "email" to email,
                            "phone" to phone
                        )
                        Database.Users.writeEngine().update(current.id, changes) { result ->
                            isSaving = false
                            if (result.isSuccess) {
                                // copy() keeps every other field, such as the profile image
                                userModel.setCurrentUser(
                                    current.copy(name = name, email = email, phone = phone)
                                )
                                Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Couldn't save your profile. Try again.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            },
            entrantSettings = entrantSettings,
            ownerSettings = ownerSettings,
            notificationPreferences = notificationPreferences,
            userIdForSettings = userIdForSettings,
            onDeleteProfile = {
                val current = userModel.currentUser
                if (current != null && !isDeleting) {
                    isDeleting = true
                    Database.Users.writeEngine().delete(current.id) { result ->
                        isDeleting = false
                        if (result.isSuccess) {
                            userModel.setCurrentUser(null)
                            navToStart()
                        } else {
                            Toast.makeText(
                                context,
                                "Couldn't delete your profile. Try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            },
            viewSettings = appViewSettings,
            appSettings = appSettings,
            onManageCampsites = { navToMyCampsites() },
            onImageChanged = { image ->
                userController.setProfileImage(image) { saved ->
                    if (!saved) {
                        Toast.makeText(
                            context,
                            "Couldn't save your photo. Try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    // Tabs whose real screens do not exist yet.

    composable<Screen.Reports> {
        PlaceholderTab("Reports")
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
}