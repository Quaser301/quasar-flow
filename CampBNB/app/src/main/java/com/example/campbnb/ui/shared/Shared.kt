package com.example.campbnb.ui.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campbnb.ui.components.BackArrow
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

/**
 * What it says on the tin.
 *
 * Creates a Header row, with a back chevron on the left side, followed a Header Title. An invisible
 * back chevron is used to recenter the header.
 */
@Composable
fun BarWithBackChevronAndHeaderCentered(
    returnFunc: () -> Unit,
    headerTitle: String
) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .clickable(onClick = returnFunc)
                .padding(12.dp)
        ) {
            BackArrow()
        }

        Box(
            Modifier.weight(1.0f),
            contentAlignment = Alignment.Center
        ) {
            Text(headerTitle, fontSize = 26.sp, textAlign = TextAlign.Center)
        }

        Box(
            modifier = Modifier
                .padding(12.dp)
        ) {
            BackArrow(color = Color.Transparent)
        }
    }
}