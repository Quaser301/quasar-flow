package com.example.campbnb.viewModels

// AI-assisted (re-pull of US 03.08.01, view state): written by Qwen 3.8 Flash Next Coder
// IQ1_M.

import androidx.lifecycle.ViewModel
import com.example.campbnb.controller.NotificationController
import com.example.campbnb.models.Notification
import com.example.campbnb.models.NotificationLogGroup
import com.example.campbnb.models.NotificationLogLine
import com.example.campbnb.models.notificationLogGroupsFor
import com.example.campbnb.models.notificationLogLinesFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The state of the notification log: nothing read yet, the log itself, or a read failure.
 *
 * The ready state carries both what the log holds and the lines it draws, so the screen has
 * one thing to observe and the lines it draws are exactly what changed since the last read.
 *
 * @memberproperty logs The log entries, newest broadcast first.
 * @memberproperty lines The lines those entries draw, dropdown detail included.
 * @memberproperty message Why the read failed, for the screen to show.
 */
sealed interface LogViewState {
    data object Loading : LogViewState
    data class Ready(val logs: List<NotificationLogGroup>, val lines: List<NotificationLogLine>) : LogViewState
    data class Error(val message: String) : LogViewState
}

/**
 * Owns the notification log for the debug screen.
 *
 * The log reads every saved notification, across every user, once when the screen opens and
 * again only when the screen taps [readLog]. It is a record of broadcasts that have already
 * been sent, so nothing is subscribed: a whole-collection listener would re-deliver every
 * saved record on every write anywhere, which is what makes opening the screen crawl.
 *
 * What the log reads is grouped into one entry per broadcast with [notificationLogGroupsFor]
 * after [NotificationController] orders it newest first, and turned into lines once here, so
 * a tap that opens or closes a dropdown hands the screen a new list and only the lines that
 * differ are drawn again.
 *
 * @memberproperty state The readonly [StateFlow] of what the log currently shows.
 * @memberproperty openLogs The readonly [StateFlow] of which log dropdowns are open.
 *
 * @param controller Where notifications are read from; the app hands its one instance.
 * @param profileLabel How a profile reference is drawn; the screen hands a stable resolver, so a
 *     caller that can resolve references against the saved users gets real names and one that
 *     cannot gets references labelled unknown. Resolved once per receiver while the lines are
 *     built.
 */
class NotificationLogViewModel(
    private val controller: NotificationController,
    private val profileLabel: (String) -> String,
) : ViewModel() {
    private val _state = MutableStateFlow<LogViewState>(LogViewState.Loading)
    val state: StateFlow<LogViewState> = _state.asStateFlow()

    private val _openLogs = MutableStateFlow(emptySet<String>())
    val openLogs: StateFlow<Set<String>> = _openLogs.asStateFlow()

    /** The entries of the last successful read, so opening a dropdown can draw them again. */
    private var readLogs: List<NotificationLogGroup>? = null

    init {
        println("NotificationLogRecomposition: NotificationLogViewModel constructed")
        readLog()
    }

    /**
     * Reads the whole log again: every saved notification, once, with no subscription kept
     * open. The screen runs this when it opens and again whenever its refresh button is
     * tapped; a read that is already showing the log is asked for again on a tap.
     */
    fun readLog() {
        println("NotificationLogRecomposition: NotificationLogViewModel.readLog")
        _state.value = LogViewState.Loading
        controller.readAll(
            onSuccess = { records ->
                showNotificationLog(notificationLogGroupsFor(NotificationController.sortNewestFirst(records)))
            },
            onFailure = { error ->
                _state.value = LogViewState.Error(error.message ?: "Could not read the notification log.")
            }
        )
    }

    /**
     * Puts the entries of a successful read into the state, with their lines.
     *
     * @param groups The entries, already ordered newest first.
     */
    private fun showNotificationLog(groups: List<NotificationLogGroup>) {
        readLogs = groups
        _state.value = LogViewState.Ready(groups, notificationLogLinesFor(groups, _openLogs.value, profileLabel))
    }

    /**
     * Opens the named log dropdown if it was closed, or closes it if it was open. An empty
     * name matches no dropdown, so it changes nothing. The open state is kept even before a
     * read has succeeded, so the next read draws it; before any read there is nothing to
     * draw, so no ready state is invented.
     *
     * @param logName The stable name of the log dropdown.
     */
    fun toggleLog(logName: String) {
        println("NotificationLogRecomposition: NotificationLogViewModel.toggleLog $logName")
        if (logName.isEmpty()) {
            return
        }
        val open = _openLogs.value
        val nowOpen = if (logName in open) open - logName else open + logName
        _openLogs.value = nowOpen

        val read = readLogs
        if (read != null) {
            _state.value = LogViewState.Ready(read, notificationLogLinesFor(read, nowOpen, profileLabel))
        }
    }

}
