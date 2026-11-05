package com.example.campbnb.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting
import kotlinx.coroutines.flow.StateFlow


// Developer Note: this is a shared window component: each screen that has views it
// cannot stay in calls this with its own invalid-view list and its own backward
// navigation, instead of every window re-implementing the watching logic.

/**
 * Watches the live view setting of an [AppViewSetting] and calls [returnTo] whenever a
 * flagged [AppView] becomes the app's current view.
 *
 * [collectAsStateWithLifecycle] turns the [StateFlow] into a value that updates on each
 * render, and [LaunchedEffect] keyed on it re-runs its body whenever that value changes,
 * so the check runs once per view change rather than once per frame: every change to a
 * [AppView] listed in [returnIfAppViewIs] triggers exactly one [returnTo] call.
 *
 * @param returnTo The backward navigation for the current window, invoked when the
 * current [AppView] is invalid for this window.
 * @param viewSetting The readonly [AppViewSetting] holding the live [AppView] flow to
 * watch.
 * @param returnIfAppViewIs The [AppView]s that are invalid for this window; each one
 * observed in the flow calls [returnTo].
 */
@Composable
fun AppViewModificationIsValidForCurrentWindowChecker(returnTo: () -> Unit, viewSetting: AppViewSetting, returnIfAppViewIs:List<AppView>) {
    val currentViewType = viewSetting.setting.collectAsStateWithLifecycle()
    LaunchedEffect(currentViewType) {
        if (returnIfAppViewIs.contains(currentViewType.value)) {
            returnTo()
        }
    }
}