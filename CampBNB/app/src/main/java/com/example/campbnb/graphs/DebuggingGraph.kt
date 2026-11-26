package com.example.campbnb.graphs

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.campbnb.Screen
import com.example.campbnb.controller.AdminController
import com.example.campbnb.controller.UserController
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.NotificationCommand
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.admin.AdminProfilesScreen
import com.example.campbnb.ui.components.profileLabelOf
import com.example.campbnb.ui.notification.NotificationLogScreen


fun NavGraphBuilder.DebugGraph(
    onNavBack: () -> Unit,

    notificationRepository: NotificationRepository,

    userController: UserController,

    userModel: UserModel,

    adminController: AdminController
) {
    // Both debug views are registered bare, not under a graph node: a caller that opens
    // one of them from the bottom bar names the screen it wants, and entering a node
    // always loads that graph's own start destination — which here was the profile view,
    // so the notification debugger could never be opened at all. Registered bare, the
    // screen itself is the start, the way the settings and profile tabs already are.
    // The admin's debug tool can remove any entry it lists: the record is gone, so it
    // stops appearing in every listing. A failed removal leaves the entry and the admin
    // taps again, so nothing here reports it.
    val removeNotice: (String) -> Unit = { id ->
        notificationRepository.run(
            NotificationCommand.Delete(
                notificationId = id,
                onDone = {}
            )
        )
    }

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
            returnFunc = onNavBack,
            // The debug log holds profile references, so it names the users they stand
            // for: the saved users are looked up once above and every reference resolves
            // against them. The log watches the model, so the names show even though the
            // view opens before the users arrive.
            profileText = { userId ->
                profileLabelOf(userId, userModel.allUsers)
            },
            savedUsers = userModel,
            deleteNotice = removeNotice
        )
    }
}