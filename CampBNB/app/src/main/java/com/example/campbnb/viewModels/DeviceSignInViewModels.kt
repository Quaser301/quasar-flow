package com.example.campbnb.viewModels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Provides a concrete [ViewModel] holding the device identity of a sign-on for the
 * duration of one app session, plus any notice the sign-on wants shown (US 01.09.01).
 *
 * The identity is written down once per session: the first readable device id wins,
 * so later renders never ask the user for anything and never re-read or replace it.
 * An unreadable device leaves nothing behind, and its reason goes to the notice so
 * the screen can say why sign-on is unavailable.
 *
 * @property deviceId The readonly [StateFlow] of the remembered device id, empty
 * until one is written down.
 * @property notice The readonly [StateFlow] of the current notice, empty when
 * there is none.
 */
class DeviceSignInController : ViewModel() {
    private val _deviceId = MutableStateFlow("")
    private val _notice = MutableStateFlow("")

    /**
     * The remembered device id.
     */
    val deviceId = _deviceId.asStateFlow()

    /**
     * Provides the current NOTICE string. Blank if no notice is set.
     */
    val notice = _notice.asStateFlow()

    /**
     * Writes down a readable device id if none is remembered yet. A blank read is
     * never remembered, and once an id is down no later read replaces it.
     *
     * @param readId The device id just read, possibly blank.
     */
    fun rememberDevice(readId: String) {
        if (_deviceId.value.isBlank() && readId.isNotBlank()) {
            _deviceId.value = readId
        }
    }

    /**
     * Sets the notice the sign-on wants shown.
     *
     * @param message The notice text, or an empty string for none.
     */
    fun noticeFrom(message: String) {
        _notice.value = message
    }

    /** Drops the current notice. */
    fun clearNotice() {
        _notice.value = ""
    }
}
