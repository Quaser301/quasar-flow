package com.example.campbnb.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.example.campbnb.controller.UserController
import com.example.campbnb.models.User
import com.example.campbnb.models.UserModel

// Todo: no profile image; the screen does not redraw if the model changes elsewhere.

/**
 * View: the signed-in user's profile (CRC ProfileActivity). Edit name, email and phone,
 * turn notifications on or off, switch role, or delete the profile
 * (US 01.07.03, 01.10.01, 01.08.01).
 */
@Composable
fun ProfileActivity(
    model: UserModel,
    controller: UserController,
    onSwitchRole: () -> Unit,
    onDeleted: () -> Unit
) {
    val user = model.currentUser
    if (user == null) {
        Text("No profile loaded", modifier = Modifier.padding(16.dp))
    } else {
        ProfileForm(user, model, controller, onSwitchRole, onDeleted)
    }
}

@Composable
private fun ProfileForm(
    user: User,
    model: UserModel,
    controller: UserController,
    onSwitchRole: () -> Unit,
    onDeleted: () -> Unit
) {
    var name by remember { mutableStateOf(user.name) }
    var email by remember { mutableStateOf(user.email) }
    var phone by remember { mutableStateOf(user.phone) }
    var notificationsOn by remember { mutableStateOf(user.notificationsEnabled) }
    var message by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)
        Text("Roles: " + user.roles.joinToString(", "), modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Receive notifications", modifier = Modifier.weight(1f))
            Switch(
                checked = notificationsOn,
                onCheckedChange = { on ->
                    controller.setNotificationsEnabled(on, onDone = { ok ->
                        if (ok) {
                            notificationsOn = on
                        }
                        message = model.statusMessage
                    })
                }
            )
        }

        Button(
            onClick = {
                controller.updateProfile(name, email, phone, onDone = { ok ->
                    message = model.statusMessage
                })
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text("Save profile")
        }
        OutlinedButton(
            onClick = onSwitchRole,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Switch role")
        }
        Button(
            onClick = { confirmDelete = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Delete profile")
        }

        if (message.isNotBlank()) {
            Text(message, modifier = Modifier.padding(top = 12.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete profile?") },
            text = { Text("This removes your profile and cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        controller.deleteProfile(onDone = { ok ->
                            if (ok) {
                                onDeleted()
                            } else {
                                message = model.statusMessage
                            }
                        })
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}