package com.example.campbnb.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.ui.shared.observeModel
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel
import com.example.campbnb.ui.components.BackArrow

/**
 * One browsed profile row: the three fields the admin sees, each already turned
 * into its display label. Not an Identifiable: this is a view over the saved
 * users, nothing here is written to the database.
 *
 * @property nameLabel The display name, or a placeholder when none was given.
 * @property emailLabel The email address, or a placeholder when none was given.
 * @property rolesLabel The held roles separated by slashes, or a placeholder
 * when none are held.
 */
class ProfileRow(val nameLabel: String, val emailLabel: String, val rolesLabel: String) {}

/**
 * The order a browsed profile takes: the display name lowercased, so listing
 * order never depends on capitalization; users with no name sort last behind a
 * trailing marker no name carries. PLACEHOLDER NOTE: the request does not say
 * how rows must be ordered; this deterministic name-first order is the choice
 * made here.
 *
 * @param user One saved user.
 * @return The sort key its row takes.
 */
private fun profileSortKeyOf(user: User): String {
    val name = user.name.trim()
    if (name.isEmpty()) {
        return "zz~" + user.email
    }
    return name.lowercase()
}

/**
 * The display name of a saved user, with a placeholder when none was given.
 *
 * @param user One saved user.
 * @return The name label.
 */
private fun nameLabelOf(user: User): String {
    val name = user.name.trim()
    if (name.isEmpty()) {
        return "(no name)"
    }
    return name
}

/**
 * The email of a saved user, with a placeholder when none was given.
 *
 * @param user One saved user.
 * @return The email label.
 */
private fun emailLabelOf(user: User): String {
    if (user.email.isEmpty()) {
        return "(no email)"
    }
    return user.email.trim()
}

/**
 * The held roles of a saved user, separated by slashes, with a placeholder
 * when none are held.
 *
 * @param user One saved user.
 * @return The roles label.
 */
private fun roleLabelOf(user: User): String {
    if (user.roles.isEmpty()) {
        return "(no role)"
    }
    var label = ""
    for (role in user.roles) {
        if (label.isEmpty()) {
            label = role.name
        } else {
            label = label.plus("/") + role.name
        }
    }
    return label
}

/**
 * The browsed profile rows, from the saved users: one row per user, ordered by
 * their display name, each carrying exactly the three fields the admin browses.
 *
 * @param users The saved users.
 * @return One row per user; no users means no rows.
 */
fun profileRowsFor(users: List<User>): List<ProfileRow> =
    users.sortedBy { user -> profileSortKeyOf(user) }
        .map { user -> ProfileRow(nameLabelOf(user), emailLabelOf(user), roleLabelOf(user)) }

/**
 * The one-line text of a browsed profile row: name, email and roles, in the
 * order the admin reads them.
 *
 * @param row The row to describe.
 * @return The line the screen draws.
 */
fun profileRowText(row: ProfileRow): String =
    "${row.nameLabel}  -  ${row.emailLabel}  -  ${row.rolesLabel}"

/**
 * The Profiles subview: a back button in the top left, a Profiles header in the
 * top center, and every saved user as a browsed row under a scroll, so the
 * list keeps its order while the admin scrolls through it.
 *
 * The screen redraws whenever the user model reports a new list, using the
 * shared observe helper; the caller passes that model and the controller keeps
 * filling it. Registration is done separately: this composable is only the
 * body, and it is not wired into navigation yet.
 *
 * @param back Called when the back button is pressed, so the admin returns to
 * the screen just on.
 * @param userModel The user model whose current list is browsed.
 */
@Composable
fun AdminProfilesScreen(back: () -> Unit, userModel: UserModel) {
    observeModel(userModel)
    val rows = profileRowsFor(userModel.allUsers)

    Column {
        AdminProfilesTopBar(back)
        Box(Modifier.verticalScroll(rememberScrollState())) {
            Column(
                verticalArrangement = spacedBy(8.dp)
            )
            {
                for (row in rows) {
                    ProfileEntry(profileRowText(row))
                }
            }
        }
    }
}

/** The back button in the top left and the Profiles header in the top center. */
@Composable
private fun AdminProfilesTopBar(back: () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Box(
        Modifier
            .clickable(onClick = { back() })
            .padding(12.dp)
    ) {
        BackArrow()
    }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text("Profiles", fontSize = 26.sp)
        }

        // secret spacer to move it back
        Box(
            modifier = Modifier
                .padding(12.dp)
        ) {
            Text("<", fontSize = 26.sp, color = Color.Transparent)
        }
    }
}

@Composable
private fun ProfileEntry(entry: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2))
            .background(FieldGrey)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .padding(vertical = 12.dp)
        ) {
            Text(entry, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
}