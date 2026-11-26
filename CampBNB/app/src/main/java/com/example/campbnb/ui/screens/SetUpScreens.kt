package com.example.campbnb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.ui.components.*
import com.example.campbnb.ui.theme.RoleGradient

/**
 * 25-11-26: Used Gemini 3.6 to make minor UI changes mostly spacing and font changes.
 */

@Composable
fun StartScreen(onGetStarted: () -> Unit) {
    AuthScaffold {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GET AMPED TO CAMP",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp,
                    color = Color(0xFF2E7D32),
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(Modifier.height(20.dp))

            GradientButton("Get started", onGetStarted)

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Discover & reserve Alberta campsite lotteries",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SignUpScreen(
    onSignUp: (email: String, username: String, phoneNumber: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }

    AuthScaffold {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Create Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "We couldn't find an existing profile for this device. Fill in your details below to get started.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(20.dp))

            GreyField(email, { email = it }, "Enter email address...", KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            GreyField(username, { username = it }, "Create Username...")
            Spacer(Modifier.height(12.dp))
            GreyField(phoneNumber, { phoneNumber = it }, "Enter phone number...", KeyboardType.Phone)
            Spacer(Modifier.height(24.dp))

            GradientButton(
                text = "Sign Up",
                enabled = email.isNotBlank() && username.isNotBlank() && phoneNumber.isNotBlank(),
                onClick = { onSignUp(email, username, phoneNumber) }
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Your profile will be securely linked to this device",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RoleSelectScreen(onRoleSelected: (UserRole) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(RoleGradient)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(color = Color.White, shape = RoundedCornerShape(32.dp)) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select Role",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose how you'd like to use CampBNB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                UserRole.entries.forEach { role ->
                    GradientButton(role.label, onClick = { onRoleSelected(role) })
                }
            }
        }
    }
}
