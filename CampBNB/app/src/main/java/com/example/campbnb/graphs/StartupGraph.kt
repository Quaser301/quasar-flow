package com.example.campbnb.graphs

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.campbnb.Screen
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.CampsiteRepository
import com.example.campbnb.models.BookingCardData
import com.example.campbnb.models.Role
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.components.UserRole
import com.example.campbnb.ui.screens.CampsiteCardData
import com.example.campbnb.ui.screens.DeviceSignInScreen
import com.example.campbnb.ui.screens.ExploreScreen
import com.example.campbnb.ui.screens.RoleSelectScreen
import com.example.campbnb.ui.screens.SignUpScreen
import com.example.campbnb.ui.screens.StartScreen
import com.example.campbnb.ui.shared.toUserRole
import com.example.campbnb.ui.shared.viewFor
import com.example.campbnb.viewModels.AppView
import kotlinx.serialization.Serializable


@Serializable
object StartupGraph // Route representing the whole sub-graph


/**
 * Defines Navigation routes for startup systems - Login, Role Selection, and sign up.
 *
 * @param onGetStarted A navigation lambda to navigate to the desired screen after the user presses
 * the "Get Started" Button.
 */

fun NavGraphBuilder.startupGraph(
    deviceID: String,
    userController: UserController,
    userModel: UserModel,

    navToSignUp: () -> Unit,
    navToHomePage: (UserRole) -> Unit,
    navToRoleSelector: () -> Unit,



    changeAppViewTo: (AppView) -> Unit,
    onGetStartedPressed: () -> Unit,

    ) {
    navigation<StartupGraph>(startDestination = Screen.Start) {
        composable<Screen.Start> {
            StartScreen(onGetStartedPressed)
        }

        composable<Screen.Login> {
            DeviceSignInScreen(
                deviceID,
                userController = userController,
                onEntered = {
                    val privileges = userModel.currentUser?.roles ?: emptyList()
                    val role = when {
                        Role.ADMIN in privileges -> Role.ADMIN
                        Role.OWNER in privileges -> Role.OWNER
                        else -> Role.ENTRANT
                    }
                    changeAppViewTo(viewFor(role))
                    navToHomePage(toUserRole(role))
                },
                onNotFound = navToSignUp
            )
        }

        composable<Screen.SignUp> {
            SignUpScreen(
                // The profile is created through the controller rather than written
                // inline, so it becomes the signed-in user the moment it is saved:
                // the role the next screen picks is recorded ON it, and every role
                // check in the app reads the saved profile, not the app view.
                // The profile starts with no roles; the role screen grants one.
                onSignUp = { email, username, phoneNumber ->
                    userController.register(deviceID, username, email, phoneNumber) { }
                    navToRoleSelector()
                }
            )
        }

        composable<Screen.Role> { // TODO: have admin accounts only be made by other admins
            RoleSelectScreen(
                onRoleSelected = { role ->
                    val targetView = when (role) {
                        UserRole.ADMIN -> AppView.AdminView
                        UserRole.OWNER -> AppView.OwnerView
                        UserRole.CAMPER -> AppView.EntrantView
                    }
                    // The role the user names is recorded on their profile through the
                    // controller, so the owner's own sent notices and an admin's
                    // abilities follow the profile and not only the app view (an
                    // owner who signed up here was being built as an entrant).
                    // The save is best-effort: the view still switches if it fails.
                    val chosenRole = when (role) {
                        UserRole.ADMIN -> Role.ADMIN
                        UserRole.OWNER -> Role.OWNER
                        UserRole.CAMPER -> Role.ENTRANT
                    }
                    // The view switch and Home wait for the save, so the profile is
                    // already carrying the role by the time Home first draws its tabs
                    // (on a failed save the view still switches, as before).
                    userController.chooseRole(chosenRole) { granted ->
                        changeAppViewTo(targetView)
                        navToHomePage(role)
                    }
                }
            )
        }


    }
}