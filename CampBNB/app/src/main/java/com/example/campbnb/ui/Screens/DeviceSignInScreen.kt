package com.example.campbnb.ui.Screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campbnb.controller.UserController
import com.example.campbnb.ui.components.GradientButton
import com.example.campbnb.ui.theme.FieldGrey
import com.example.campbnb.viewModels.DeviceSignInController

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
 * @param readDevice Supplies this device&apos;s id; the caller wires it to the
 * shared reader.
 * @param userController The existing control that identifies a device.
 * @param onEntered Runs once the sign-on is done: the caller moves to the screen
 * just after signing on.
 */
@Composable
fun DeviceSignInScreen(
    readDevice: () -> String,
    userController: UserController,
    onEntered: () -> Unit,
) {
    val display = remember { DeviceSignInController() }
    val currentId = display.deviceId().collectAsStateWithLifecycle()
    val notice = display.notice().collectAsStateWithLifecycle()

    if (currentId.value.isBlank()) {
        val read = readDevice()
        if (read.isBlank()) {
            display.noticeFrom("Could not read this device's id, so sign-on is unavailable.")
        } else {
            display.rememberDevice(read)
        }
    }

    Column {
        Box(
            Modifier.weight(1.0f),
            contentAlignment = Alignment.Center
        ) {
            Text("Sign on", fontSize = 26.sp)
            if (notice.value.isNotBlank()) {
                Text(notice.value, fontSize = 14.sp, color = FieldGrey)
            }
        }
        Spacer(Modifier.height(16.dp))
        GradientButton(
            "Continue",
            enabled = currentId.value.isNotBlank(),
            onClick = {
                userController.identify(
                    deviceId = currentId.value,
                    onFound = { user -> onEntered() },
                    onNotFound = { onEntered() },
                    onError = { message -> display.noticeFrom(message) }
                )
            }
        )
    }
}
