package com.example.campbnb.tabs

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.data.SentNotificationsRepository
import com.example.campbnb.notifications.NotificationRoutes
import com.example.campbnb.Screen
import com.example.campbnb.ui.inbox.NotificationInboxScreen
import com.example.campbnb.ui.inbox.NotificationViewModel
import com.example.campbnb.ui.notification.NotificationLogScreen
import com.example.campbnb.viewModels.simpleViewModelFactory


/**
 * The notices the current viewer received. viewModel() keeps the same inbox (and its
 * Firestore listener) across recompositions. Tapping a notice runs the app's own bridge:
 * the typed screen that notice names, so an owner's tap opens the same destination any
 * other entry to that notice would.
 */
@Composable
fun ReceivedNoticesTab(
    userId: String,
    notificationController: NotificationController,
    onNavToNotif: (screen: Screen) -> Unit,
    deleteNotice: (String) -> Unit = {},
) {
    val inboxViewModel: NotificationViewModel = viewModel(
        key = "notification-inbox-$userId",
        factory = simpleViewModelFactory {
            NotificationViewModel(notificationController, userId)
        }
    )
    NotificationInboxScreen(viewModel = inboxViewModel, navToNotification = { notification ->
        onNavToNotif(NotificationRoutes.screenFor(notification))
    }, deleteNotice = deleteNotice)
}

/**
 * The "Sent Notifications" of the current viewer: the notices they caused, read through a
 * repository scoped to their own id, drawn by the notification log. A viewer listing their
 * own correspondence is not shown the raw database keys an admin looks entries up by.
 */
@Composable
fun SentNoticesTab(
    userId: String,
    repo: NotificationRepository,
    deleteNotice: (String) -> Unit = {},
) {
    NotificationLogScreen(
        title = "Sent Notifications",
        notificationSource = SentNotificationsRepository(repo, userId),
        rawIdsAllowed = false,
        deleteNotice = deleteNotice
    )
}