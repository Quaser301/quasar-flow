package com.example.campbnb.ui.admin

/*
 * ProfileRemovedDialog.kt
 *
 * Role: view. The pop-up a user sees when an admin deletes their profile while the app is
 * open (US 03.02.01). It cannot be dismissed any way except the OK button, so the app only
 * returns to the sign-in screen after the user has seen it.
 */

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Pop-up telling the user an admin deleted their profile.
 *
 * Tapping outside it or pressing back does nothing; only OK closes it.
 *
 * @param onOk Called when the user taps OK.
 */
@Composable
fun ProfileRemovedDialog(onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Your profile was deleted") },
        text = {
            Text(
                "An admin deleted your profile for violating the app policy. Your upcoming " +
                        "reservations were cancelled and your lottery entries were removed. " +
                        "Tap OK to go back to the sign-in screen."
            )
        },
        confirmButton = {
            TextButton(onClick = onOk, modifier = Modifier.testTag("profile_removed_ok")) {
                Text("OK")
            }
        }
    )
}