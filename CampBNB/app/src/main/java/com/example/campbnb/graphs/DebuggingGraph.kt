package com.example.campbnb.graphs

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.campbnb.Screen
import com.example.campbnb.controller.AdminController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.admin.AdminProfilesScreen
import com.example.campbnb.ui.components.profileLabelOf
import com.example.campbnb.ui.notification.NotificationLogScreen
import com.example.campbnb.ui.admin.AdminImagesScreen

fun NavGraphBuilder.DebugGraph(
    onNavBack: () -> Unit,

    notificationRepository: NotificationRepository,

    userController: UserController,

    userModel: UserModel,

    adminController: AdminController
) {
    navigation<NavGraphNodes.DebugGraph>(startDestination = Screen.AdminProfileView) {

        composable<Screen.AdminProfileView> {
            LaunchedEffect(Unit) {
                userController.loadAllUsers()
            }
            AdminProfilesScreen(
                onNavBack,
                userModel,
                adminController
            )
        }

        composable<Screen.AdminNotificationView> {
            LaunchedEffect(Unit) {
                userController.loadAllUsers()
            }
            NotificationLogScreen(
                title = "Notification Debugging",
                notificationSource = notificationRepository,
                returnFunc =onNavBack,
                // The debug log holds profile references, so it names the
                // users they stand for: the saved users are looked up once
                // above and every reference resolves against them. The log
                // watches the model, so the names show even though the view
                // opens before the users arrive.
                profileText = { userId ->
                    profileLabelOf(userId, userModel.allUsers)
                },
                savedUsers = userModel
            )
        }

        // The admin's Images tab: every uploaded picture, tap one to remove it
        // (US 03.06.01, US 03.03.01).
        composable<Screen.AdminImages> {
            AdminImagesScreen(
                back = onNavBack,
                adminController = adminController
            )
        }
    }
}