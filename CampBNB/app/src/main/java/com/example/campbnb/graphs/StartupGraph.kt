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
import com.example.campbnb.database.Database
import com.example.campbnb.models.BookingCardData
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
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
                // TODO: maybe extract to a model class?
                onSignUp = { email, username, phoneNumber ->
                    val newUser = User(
                        deviceId = deviceID,
                        name = username,
                        email = email,
                        phone = phoneNumber,
                        roles = listOf(Role.ENTRANT) // was admin: why????
                    )
                    Database.Users
                        .writeEngine()
                        .save(newUser, { /* TODO: handle the complete */ })
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
                    changeAppViewTo(targetView)
                    navToHomePage(role)
                }
            )
        }


    }
}