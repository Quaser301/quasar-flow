package com.example.campbnb.ui.admin

/*
 * ConfirmRemovalDialog.kt
 *
 * Role: view. The "are you sure?" pop-up every admin removal uses (profile, owner, campsite,
 * rating, review, image), with the reason picker. Written once so each story only supplies
 * its own text and says whether a reason is required.
 *
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.models.RemovalReason

private val DangerRed = Color(0xFFB00020)

/**
 * Pop-up asking an admin to confirm an irreversible removal, with a reason picker.
 *
 * The confirm button is disabled until a reason is picked when [reasonRequired] is true.
 *
 * @param title The pop-up title, such as "Delete this profile?".
 * @param message What will happen, including that it cannot be undone.
 * @param confirmLabel The text of the red confirm button.
 * @param reasonRequired True if the admin must pick a reason before confirming.
 * @param onConfirm Called with the picked reason (null if none was picked).
 * @param onDismiss Called when the admin cancels or taps outside the pop-up.
 */
@Composable
fun ConfirmRemovalDialog(
    title: String,
    message: String,
    confirmLabel: String,
    reasonRequired: Boolean,
    onConfirm: (RemovalReason?) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf<RemovalReason?>(null) }
    val canConfirm = !reasonRequired || reason != null

    var reasonHeading = "Reason (optional)"
    if (reasonRequired) {
        reasonHeading = "Reason (required)"
    }

    // Alert Dialog function explained and implemented by Claude Sonnet 5.5
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(message)
                Spacer(Modifier.height(12.dp))
                Text(reasonHeading, fontSize = 14.sp)
                for (option in RemovalReason.values()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reason_" + option.name)
                            .clickable { reason = option },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == option, onClick = { reason = option })
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason) },
                enabled = canConfirm,
                modifier = Modifier.testTag("confirm_removal")
            ) {
                Text(confirmLabel, color = DangerRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}