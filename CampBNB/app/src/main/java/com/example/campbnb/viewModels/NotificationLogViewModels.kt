package com.example.campbnb.viewModels

// AI-assisted (re-pull of US 03.08.01, view state): written by Qwen 3.8 Flash Next Coder
// IQ1_M. No disclosure-table row: the user asked to stop logging prompts at this point.

import androidx.lifecycle.ViewModel
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.models.NotificationLogGroup
import com.example.campbnb.models.notificationLogGroupsFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The state of the notification log: nothing read yet, the log entries, or a read failure.
 *
 * @memberproperty logs The log entries, newest broadcast first.
 * @memberproperty message Why the read failed, for the screen to show.
 */
sealed interface LogViewState {
    data object Loading : LogViewState
    data class Ready(val logs: List<NotificationLogGroup>) : LogViewState
    data class Error(val message: String) : LogViewState
}

/**
 * Owns the live notification log for the debug screen.
 *
 * The log reads every saved notification, across every user, through [NotificationController]
 * and groups it into one entry per broadcast, newest first. Which dropdowns are open is kept
 * as one readonly [StateFlow] of open log names, so the screen re-renders when a tap opens or
 * closes one.
 *
 * @memberproperty state The readonly [StateFlow] of what the log currently shows.
 * @memberproperty openLogs The readonly [StateFlow] of which log dropdowns are open.
 *
 * @param controller Where notifications are read from; the app hands its one instance.
 * @param onBack Called whenever [pressBack] runs: the screen hands this its back lambda, so
 *     leaving Notification Debugging returns to the screen just on.
 */
class NotificationLogViewModel(
    private val controller: NotificationController,
    private val onBack: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow<LogViewState>(LogViewState.Loading)
    val state: StateFlow<LogViewState> = _state.asStateFlow()

    private val _openLogs = MutableStateFlow(emptySet<String>())
    val openLogs: StateFlow<Set<String>> = _openLogs.asStateFlow()

    private var stopListening: (() -> Unit)? = null

    init {
        start()
    }

    /**
     * Starts the all-notifications listener again, stopping any listener still running so
     * one is ever live.
     */
    fun start() {
        stopListening?.invoke()
        _state.value = LogViewState.Loading

        stopListening = controller.listenAll(
            onUpdate = { update ->
                _state.value = LogViewState.Ready(
                    notificationLogGroupsFor(NotificationController.sortNewestFirst(update.all))
                )
            },
            onError = { error ->
                _state.value = LogViewState.Error(
                    error.message ?: "Could not read the notification log."
                )
            }
        )
    }

    /**
     * Opens the named log dropdown if it was closed, or closes it if it was open. An empty
     * name matches no dropdown, so it changes nothing.
     *
     * @param logName The stable name of the log dropdown.
     */
    fun toggleLog(logName: String) {
        if (logName.isEmpty()) {
            return
        }
        _openLogs.value =
            if (logName in _openLogs.value) _openLogs.value - logName else _openLogs.value + logName
    }

    /**
     * Runs the back navigation: presses the screen&apos;s back button by calling the back
     * lambda it was handed.
     */
    fun pressBack() {
        onBack()
    }

    /**
     * Stops the live listener, so the screen can turn the read off when it goes away.
     * Running it twice changes nothing.
     */
    fun stop() {
        stopListening?.invoke()
        stopListening = null
    }

    /** Stops the listener when the screen goes away, so it never keeps running. */
    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
