package com.example.campbnb.ui.admin

/*
 * AdminImageRemoval.kt
 *
 * Role: view. The admin's image settings panel (US 03.03.01), shared by every place an admin
 * can click a picture: the campsite details header, the Profiles list and the uploaded
 * images browser. The panel shows the picture with a red "Remove image" button; the button
 * opens the shared confirmation pop-up (the removal cannot be undone) with a required reason.
 */

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.campbnb.models.RemovalReason
import com.example.campbnb.ui.components.GradientButton
import com.example.campbnb.ui.shared.StoredImage
import com.example.campbnb.ui.theme.DeleteGradient

/**
 * The image settings panel and, behind its red button, the confirmation pop-up.
 *
 * @param title What the picture belongs to, such as the campsite's or person's name.
 * @param stored The picture as stored text.
 * @param onRemove Called with the reason the admin picked once they confirm the removal.
 * @param onDismiss Called when the panel closes, whether the admin cancelled or confirmed.
 */
@Composable
fun AdminImageRemoval(
    title: String,
    stored: String,
    onRemove: (RemovalReason?) -> Unit,
    onDismiss: () -> Unit
) {
    var confirming by remember { mutableStateOf(false) }

    if (!confirming) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Image settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title)
                    StoredImage(
                        stored = stored,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .testTag("image_preview"),
                        contentScale = ContentScale.Crop
                    )
                    GradientButton(
                        text = "Remove image",
                        onClick = { confirming = true },
                        brush = DeleteGradient,
                        modifier = Modifier.fillMaxWidth().testTag("remove_image")
                    )
                }
            },
            confirmButton = {
                TextButton(modifier = Modifier.testTag("image_settings_close"), onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    } else {
        ConfirmRemovalDialog(
            title = "Remove this image?",
            message = "The image of " + title + " will be permanently deleted and whoever " +
                    "uploaded it will be told why. This cannot be undone.",
            confirmLabel = "Remove",
            reasonRequired = true,
            onConfirm = { reason ->
                onRemove(reason)
                onDismiss()
            },
            onDismiss = onDismiss
        )
    }
}