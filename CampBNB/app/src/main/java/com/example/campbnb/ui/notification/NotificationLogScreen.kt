package com.example.campbnb.ui.notification

// AI-assisted (re-pull of US 03.08.01, debug screen): written by Qwen 3.8 Flash Next Coder
// IQ1_M.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campbnb.data.NotificationRepository
import com.example.campbnb.models.LogLineStyle
import com.example.campbnb.models.NotificationLogLine
import com.example.campbnb.models.UserModel
import com.example.campbnb.models.ViewObserver
import com.example.campbnb.ui.components.BackArrow
import com.example.campbnb.ui.components.unresolvableProfileLabel
import com.example.campbnb.viewModels.LogViewState
import com.example.campbnb.viewModels.NotificationLogViewModel
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.ui.screens.ExploreScreen
import com.example.campbnb.ui.shared.BarWithBackChevronAndHeaderCentered
import com.example.campbnb.ui.theme.DataColor
import com.example.campbnb.ui.theme.DataColorImportant
import com.example.campbnb.ui.theme.DataColorUseful
import com.example.campbnb.ui.theme.AdminInfoColor
import com.example.campbnb.ui.theme.AdminInteractionColor

/**
 * A notification log body: a header, a button that reads the log again, and the log itself
 * below them.
 *
 * The header names the log and the caller hands the repository to read, so one composable
 * draws every view that lists notifications: the whole-collection debug log and an owner's
 * own sent notices are the same drawing with a different title and a different repository.
 * Which notices a view may list is decided by whoever hands over that repository - the app
 * graph hands the plain one where a view may see everything, and the scoped one where a
 * viewer may list only their own. Nothing here knows about roles or permissions.
 *
 * The screen only draws. What the log holds and the lines it shows come from
 * [NotificationLogViewModel], which reads whatever that repository serves once when the log
 * opens and turns it into lines; the screen never reaches the records themselves and does no
 * formatting of its own. Opening a dropdown is a tap on its bar: the view adds that entry's
 * receivers and its notice to the lines, and the keyed list draws only the lines that differ.
 *
 * Registration is done separately: this composable is only the body.
 *
 * @param title The header text naming this log.
 * @param notificationSource Where the notices this view may list are read from.
 * @param returnFunc The return function used by the back button. Not passing one leaves the
 * header without a back control, which is how a view set inside another screen gets its
 * title while that screen owns going back. Passing the direct Nav reference is not done,
 * since the current navigation method (from the [ExploreScreen]) would then return to the
 * sign-in page.
 * @param profileText How a profile reference is drawn. The default is the stable resolver for
 *     listings that cannot look up saved users, so it labels unknown references as such. A
 *     caller that can resolve references hands this its own stable lookup.
 * @param savedUsers The saved-user model the log watches, so a late arrival of profiles
 *     re-resolves the receiver lines of what was already read. The default is a private model
 *     nothing ever notifies, which resolves only at the read time.
 */
@Composable
fun NotificationLogScreen(
    title: String,
    notificationSource: NotificationRepository,
    returnFunc: (() -> Unit)? = null,
    profileText: (String) -> String = unresolvableProfileLabel,
    savedUsers: UserModel = UserModel(),
) {
    val display = viewModel {
        NotificationLogViewModel(notificationSource, profileText)
    }
    val state by display.state.collectAsState()

    // Profiles arriving after this log opened re-name the receivers already drawn: the view
    // model keeps the entries, so only the lines are derived again.
    DisposableEffect(savedUsers) {
        val profileWatcher = object : ViewObserver<UserModel> {
            override fun update(changedUsers: UserModel) {
                display.reflowLines()
            }
        }
        savedUsers.addView(profileWatcher)
        onDispose {
            savedUsers.removeView(profileWatcher)
        }
    }

    Column(
        modifier = Modifier.padding(top = 32.dp, start = 12.dp, end = 12.dp)
    ) {
        if (returnFunc == null) {
            Text(
                title,
                fontSize = 26.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            BarWithBackChevronAndHeaderCentered(returnFunc, title)
        }
        Box(
            modifier = Modifier
                .testTag("log_refresh")
                .clickable(onClick = {
                    display.readLog()
                })
                .padding(12.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Text(
                "Refresh",
                fontSize = 14.sp,
                color = AdminInteractionColor,
                textAlign = TextAlign.Center
            )
        }
        when (val shown = state) {
            is LogViewState.Loading -> {
                Text("Reading Notifications...", fontSize = 18.sp, color = AdminInfoColor)
            }

            is LogViewState.Error -> {
                Text(shown.message, fontSize = 18.sp, color = AdminInfoColor)
            }

            is LogViewState.Ready -> {
                if (shown.lines.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No logs to show!", fontSize = 22.sp, color = AdminInfoColor)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = shown.lines, key = { it.lineKey }) { line ->
                            NotificationLogLineDisplay(display, line)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws one log line in the look its style names: a dropdown bar that opens or closes that
 * entry on a tap, a receiver of it, or the title and body of the notice it carried.
 *
 * The text is already written, so this only gives it a size, an indent and a color.
 *
 * @param display The holder of the dropdown expansion state, so a bar can open its entry.
 * @param line The line to draw.
 */
@Composable
private fun NotificationLogLineDisplay(
    display: NotificationLogViewModel,
    line: NotificationLogLine,
) {
    when (line.style) {
        LogLineStyle.DROPDOWN -> {
            Box(
                modifier = Modifier
                    .testTag("log_dropdown")
                    .clickable(onClick = {
                        display.toggleLog(line.logId)
                    })
                    .padding(6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(line.text, fontSize = 18.sp, color = DataColor)
            }
        }

        // TODO: make a factory for the titles in case this is more than just this file
        LogLineStyle.RECIPIENT -> {
            Text(
                "Recipients: ${line.text}",
                fontSize = 16.sp,
                color = DataColorImportant,
                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)
            )
        }

        LogLineStyle.NOTICE_TITLE -> {
            Text(
                "Title: ${line.text}",
                fontSize = 18.sp,
                modifier = Modifier
                    .padding(start = 24.dp),
                color = DataColorUseful)
        }

        LogLineStyle.NOTICE_BODY -> {
            Text(
                "Body: ${line.text}",
                fontSize = 16.sp,
                color = DataColor,
                modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
            )
        }
    }
}
