package com.example.campbnb.ui.Screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campbnb.controller.UserController
import com.example.campbnb.ui.components.GradientButton
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.viewModels.DeviceSignInController
import com.example.campbnb.ui.components.AuthScaffold



/**
 * The device sign-on body (US 01.09.01): a Sign on header, and one Continue
 * affordance. Nothing here ever asks for a username or a password: the entrant
 * is identified by their device, which the caller reads through the shared
 * device reader, and the same identity carries through the rest of the app use.
 *
 * The first readable read of the device is written down once per session in the
 * sign-on ViewModel; every later render reuses it. An unreadable device shows
 * why sign-on is unavailable instead. Continuing runs the existing identification:
 * a returning device finds its profile straight away, and a brand-new one enters
 * while its profile details are filled by the profile story. PLACEHOLDER NOTE:
 * new-device entry deliberately does not collect any details here.
 *
 * @param readDevice Supplies this device's id; the caller wires it to the
 * shared reader.
 * @param userController The existing control that identifies a device.
 * @param onEntered Runs once the sign-on is done: the caller moves to the screen
 * just after signing on.
 * @param onNotFound Runs if the provided Device ID does not exist in the Firestore Database.
 * @param headerImage An image to act as the backdrop. See [AuthScaffold].
 */
@Composable()
fun DeviceSignInScreen(
    readDevice: () -> String,
    userController: UserController,
    onEntered: () -> Unit,
    onNotFound: () -> Unit,
    @DrawableRes headerImage: Int? = null,
) {
    val display = remember { DeviceSignInController() }
    val currentId = display.deviceId.collectAsStateWithLifecycle()
    val notice = display.notice.collectAsStateWithLifecycle()

    if (currentId.value.isBlank()) {
        val read = readDevice()
        if (read.isBlank()) {
            display.noticeFrom("Could not read this device's id, so sign-on is unavailable.")
        } else {
            display.rememberDevice(read)
        }
    }

    AuthScaffold(headerImage) {
        Text("Sign on", fontSize = 24.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        if (notice.value.isNotBlank()) {
            Text(notice.value, fontSize = 14.sp, color = FieldGrey)
        }
        Spacer(modifier = Modifier.height(8.dp))
        GradientButton(
            "Continue",
            enabled = currentId.value.isNotBlank(),
            onClick = {
                userController.identify(
                    deviceId = currentId.value,
                    onFound = { user -> onEntered() },
                    onNotFound = { onNotFound() },
                    onError = { message -> display.noticeFrom(message) }
                )
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}