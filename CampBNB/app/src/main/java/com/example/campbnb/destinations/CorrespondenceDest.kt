package com.example.campbnb.destinations

import androidx.compose.runtime.Composable
import com.example.campbnb.Screen
import com.example.campbnb.controller.MessageController
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.tabs.MessagesTab
import com.example.campbnb.tabs.ReceivedNoticesTab
import com.example.campbnb.tabs.SentNoticesTab
import com.example.campbnb.ui.components.unresolvableProfileLabel
import com.example.campbnb.ui.inbox.CorrespondenceScreen
import com.example.campbnb.ui.inbox.CorrespondenceTabs


/**
 * The Correspondence screen, with the tabs this viewer has.
 *
 * The tabs and their contents are chosen here, where the view the app is in is known: a
 * viewer whose view acts as an owner is shown the notices they caused under "Notifications"
 * and the notices they received under "Received", while every other viewer has the one
 * notices tab they can see. The view, not the signed-in role, decides this: switching views
 * moves the tabs, so an entrant never reaches the sent notices and an owner in the entrant
 * view does not either. The screen itself only draws what it is handed.
 *
 * @param openTab Which tab opens, named in CorrespondenceTabs.
 * @param openReservationId If set, the Messages tab opens straight into this reservation's chat.
 * @param onNavBack Runs when the screen's Back control is pressed: the caller that opened
 *     this destination owns where back goes.
 * @param onNavToNotif Runs when a notice is tapped, with the typed [Screen] route that
 *     notice names: the caller that opened this destination owns the navigation.
 * @param deleteNotice Runs when a notice is removed, with its database key and what to do
 *     once the store confirms it: the caller that opened this destination owns the store and
 *     what a failed removal says.
 * @param unresolvedProfileText How a profile reference the sent listing cannot look up is
 *     drawn. Only a viewer whose view may read raw database keys gets the key.
 */
@Composable
fun CorrespondenceDestination(
    openTab: String,
    openReservationId: String? = null,
    onNavBack: () -> Unit = {},
    // Asked again each time the screen draws: the live view setting decides the shape, and
    // it changes while the app is running.
    ownerViewActive: () -> Boolean,
    userId: String,
    repo: NotificationRepository,
    notifController: NotificationController,
    msgController: MessageController,
    onNavToNotif: (screen: Screen) -> Unit,
    deleteNotice: (String, (removed: Boolean) -> Unit) -> Unit = { _, _ -> },
    unresolvedProfileText: (String) -> String = unresolvableProfileLabel,
) {
    if (ownerViewActive()) {
        CorrespondenceScreen(
            tabLabels = listOf(
                CorrespondenceTabs.NOTIFICATIONS_TAB,
                CorrespondenceTabs.RECEIVED_TAB,
                CorrespondenceTabs.MESSAGES_TAB
            ),
            openTab = openTab,
            notificationsTabContent = {
                SentNoticesTab(userId, repo, deleteNotice, unresolvedProfileText)
            },
            receivedTabContent = {
                ReceivedNoticesTab(
                    userId,
                    notifController,
                    onNavToNotif,
                    deleteNotice
                )
             },
            messagesTabContent = { MessagesTab(openReservationId, userId, msgController) },
            onNavBack = onNavBack
        )
    } else {
        CorrespondenceScreen(
            tabLabels = listOf(
                CorrespondenceTabs.NOTIFICATIONS_TAB,
                CorrespondenceTabs.MESSAGES_TAB
            ),
            openTab = openTab,
            notificationsTabContent = {
                ReceivedNoticesTab(
                    userId,
                    notifController,
                    onNavToNotif,
                    deleteNotice
                )
            },
            messagesTabContent = { MessagesTab(openReservationId, userId, msgController) },
            onNavBack = onNavBack
        )
    }
}
