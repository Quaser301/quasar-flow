package com.example.campbnb.ui.profile

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.ui.components.UserRole

private val CampGreen = Color(0xFF3B6D11)
private val CampGreenDark = Color(0xFF27500A)
private val CampGreenLight = Color(0xFFEAF3DE)

/**
 * Profile tab for campers (entrants) and owners. Lets the user edit their name, email and
 * phone number (US 01.05.02). The screen only validates and reports; saving is done by [onSave].
 *
 * @param role Decides the role chip and whether the owner row is shown.
 * @param onSave Called with the validated name, email and phone when "Save changes" is tapped.
 * @param onOpenSettings Opens Settings (where the app policy lives).
 * @param onManageCampsites Owner only: opens the owner's campsite list.
private val DeleteRed = Color(0xFFA32D2D)

private const val DELETE_PHRASE = "DELETE"

/**
 * Profile tab for campers (entrants) and owners. Lets the user edit their name, email and
 * phone number (US 01.05.02) and, for campers, delete their profile (US 01.07.03).
 * The screen only validates and reports; saving and deleting are done by the caller.
 *
 * @param role Decides the role chip, the owner row, and whether the delete button is shown.
 * @param initialName Current name stored for the user.
 * @param initialEmail Current email stored for the user.
 * @param initialPhone Current phone number stored for the user.
 * @param onSave Called with the validated name, email and phone when "Save changes" is tapped.
 * @param onOpenSettings Opens Settings (where the app policy lives).
 * @param onManageCampsites Owner only: opens the owner's campsite list.
 * @param onDeleteProfile Called once the user has typed the confirmation phrase and confirmed.
 * @param isSaving True while a save is in flight; taps are ignored and the button shows "Saving…".
 * @param isDeleting True while a delete is in flight; the dialog's confirm button is locked.
 *
 * @author Zino
 */
@Composable
fun ProfileScreen(
    role: UserRole,
    initialName: String,
    initialEmail: String,
    initialPhone: String,
    onSave: (name: String, email: String, phone: String) -> Unit,
    onOpenSettings: () -> Unit,
    onManageCampsites: () -> Unit
    onManageCampsites: () -> Unit,
    onDeleteProfile: () -> Unit,
    isSaving: Boolean = false,
    isDeleting: Boolean = false
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    var phone by remember(initialPhone) { mutableStateOf(initialPhone) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val initials = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

    val roleLabel = when (role) {
        UserRole.CAMPER -> "Camper"
        UserRole.OWNER -> "Owner"
        UserRole.ADMIN -> "Admin"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Profile", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(CampGreenLight),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = CampGreenDark)
        }
        Spacer(Modifier.height(8.dp))
        Text(name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            roleLabel,
            fontSize = 12.sp,
            color = CampGreenDark,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CampGreenLight)
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Personal information",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = null },
            label = { Text("Full name") },
            isError = nameError != null,
            supportingText = { nameError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            label = { Text("Email") },
            isError = emailError != null,
            supportingText = { emailError?.let { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it; phoneError = null },
            label = { Text("Phone number") },
            isError = phoneError != null,
            supportingText = { phoneError?.let { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (isSaving) return@Button

                nameError = if (name.isBlank()) "Enter your name" else null
                emailError =
                    if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) "Enter a valid email" else null
                phoneError =
                    if (phone.count { it.isDigit() } < 10) "Enter a valid phone number" else null

                if (nameError == null && emailError == null && phoneError == null) {
                    onSave(name.trim(), email.trim(), phone.trim())
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = CampGreen, contentColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Save changes")
            Text(if (isSaving) "Saving…" else "Save changes")
        }

        Spacer(Modifier.height(16.dp))
        if (role == UserRole.OWNER) {
            ProfileRow("Manage my campsites", onManageCampsites)
        }
        ProfileRow("Settings and app policy", onOpenSettings)

        if (role == UserRole.CAMPER || role == UserRole.OWNER || role == UserRole.ADMIN) {
            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete my profile", color = DeleteRed)
            }
        }
    }

    if (showDeleteDialog) {
        DeleteProfileDialog(
            isDeleting = isDeleting,
            onConfirm = onDeleteProfile,
            onDismiss = { if (!isDeleting) showDeleteDialog = false }
        )
    }
}

@Composable
private fun ProfileRow(label: String, onClick: () -> Unit) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp)
        Text("›", fontSize = 18.sp, color = Color.Gray)
    }
}

@Composable
private fun DeleteProfileDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete your profile?") },
        text = {
            Column {
                Text("This can't be undone. Your information will be permanently removed.")
                Spacer(Modifier.height(12.dp))
                Text("Type $DELETE_PHRASE to confirm.", fontSize = 13.sp)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                // case-sensitive match, like GitHub's repo deletion
                enabled = typed == DELETE_PHRASE && !isDeleting
            ) {
                Text(if (isDeleting) "Deleting…" else "Delete", color = DeleteRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}