package com.example.campbnb.viewModels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Provides a concrete [ViewModel] holding the dropdown expansion state of the
 * Notification Debugging screen and the wiring of its back navigation.
 *
 * Unlike the settings display state, log dropdowns are not a known fixed set: one
 * exists per notification log entry, so their names come from the data at runtime.
 * Which of them are open is therefore kept as one immutable `StateFlow` of the open
 * log names, newline-delimited, so the screen re-renders when a tap opens or closes
 * one. PLACEHOLDER NOTE: a log name that itself contains a newline could match
 * falsely; the names the model produces are built from record fields joined without
 * newlines, and no such name exists yet.
 *
 * @param onBack Called whenever [pressBack] runs: the screen hands this its
 * back lambda, so leaving Notification Debugging returns to the screen just on.
 * @property openedLogNames The readonly [StateFlow] of which log dropdowns are open.
 */
class NotificationLogDisplayController(val onBack: () -> Unit): ViewModel() {
    // one delimited value of open log names instead of a per-group flow: the groups
    // are dynamic, and no container constructor form is used so this stays in the
    // same family of literals as everything else here.
    private val _openedLogNames = MutableStateFlow("\n")

    /**
     * Provides which log dropdowns are currently open.
     *
     * @return A readonly [StateFlow] of the open log dropdown names, one per line.
     */
    fun openedLogNames(): StateFlow<String> = _openedLogNames.asStateFlow()

    /**
     * Opens the named log dropdown if it was closed, or closes it if it was open.
     *
     * @param logName The stable name of the log dropdown.
     */
    fun toggleGroup(logName: String) {
        val marker = "\n" + logName + "\n"
        val opened = _openedLogNames.value
        _openedLogNames.value =
            if (marker in opened) opened.minus(marker) else opened.plus(marker)
    }

    /**
     * Runs the back navigation: presses the screen&apos;s back button by calling the
     * back lambda it was handed.
     */
    fun pressBack() {
        onBack()
    }
}
