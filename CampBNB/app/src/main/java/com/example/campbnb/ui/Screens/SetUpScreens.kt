package com.example.campbnb.ui.Screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campbnb.ui.components.*
import com.example.campbnb.ui.theme.RoleGradient

@Composable
fun StartScreen(onGetStarted: () -> Unit) {
    AuthScaffold {
        Text("CampBNB", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "secure the spot\nof your dreams.",
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        GradientButton("Get started", onGetStarted)
    }
}

@Composable
fun LoginScreen(
    onLogin: (username: String, role: UserRole, rememberMe: Boolean) -> Unit,
    onSignUpClick: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var role by remember { mutableStateOf<UserRole?>(null) }
    var rememberMe by remember { mutableStateOf(false) }

    AuthScaffold {
        Text("LogIn", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        LinkRow("Don't have an account yet? ", "Sign Up", onSignUpClick)
        Spacer(Modifier.height(16.dp))

        GreyField(username, { username = it }, "Username")
        Spacer(Modifier.height(12.dp))
        RoleDropdown(role, { role = it })
        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
            Text("Remember me", fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))

        GradientButton(
            text = "Login",
            enabled = username.isNotBlank() && role != null,
            onClick = { onLogin(username, role!!, rememberMe) }
        )
        Spacer(Modifier.height(24.dp))
        Text("something nice at the bottom", fontSize = 12.sp)
    }
}

@Composable
fun SignUpScreen(
    onSignUp: (email: String, username: String) -> Unit,
    onLoginClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    AuthScaffold {
        Text("Sign up", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        LinkRow("Already have an account? ", "Login", onLoginClick)
        Spacer(Modifier.height(16.dp))

        GreyField(email, { email = it }, "Enter email address...", KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        GreyField(username, { username = it }, "Create Username...")
        Spacer(Modifier.height(20.dp))

        GradientButton(
            text = "Sign Up",
            enabled = email.isNotBlank() && username.isNotBlank(),
            onClick = { onSignUp(email, username) }
        )
        Spacer(Modifier.height(24.dp))
        Text("something nice at the bottom", fontSize = 12.sp)
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
                Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Are you a...", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                UserRole.values().forEach { role ->
                    GradientButton(role.label, onClick = { onRoleSelected(role) })
                }
            }
        }
    }
}