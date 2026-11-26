package com.example.campbnb.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campbnb.controller.MessageController
import com.example.campbnb.ui.inbox.MessagesScreen
import com.example.campbnb.viewModels.MessageViewModel
import com.example.campbnb.viewModels.simpleViewModelFactory


/** The chat list and chat threads. [openReservationId] jumps straight into one chat. */
@Composable
fun MessagesTab(openReservationId: String? = null, userId: String, controller: MessageController) {
    val messageViewModel: MessageViewModel = viewModel(
        key = "message-inbox-$userId",
        factory = simpleViewModelFactory {
            MessageViewModel(controller, userId)
        }
    )
    LaunchedEffect(openReservationId) {
        if (!openReservationId.isNullOrBlank()) {
            messageViewModel.open(openReservationId)
        }
    }
    MessagesScreen(viewModel = messageViewModel, currentUserId = userId)
}