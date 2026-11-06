package com.example.campbnb.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.example.campbnb.ui.theme.*
import kotlinx.serialization.Serializable

/** The role a user signs in or registers as. */
@Serializable // make Serializable so that Navigation works
enum class UserRole(val label: String) { CAMPER("Camper"), OWNER("Owner"), ADMIN("Admin") }

/** Pill-shaped button with a gradient background (green by default). */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = ButtonGradient,
    height: Dp = 44.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(50))
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 18.sp)
    }
}

/** Flat grey text field matching the design. */
@Composable
fun GreyField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 14.sp) },
        singleLine = true,
        shape = RoundedCornerShape(4.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FieldGrey,
            unfocusedContainerColor = FieldGrey,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** "Don't have an account yet? Sign Up" style line with a clickable blue link. */
@Composable
fun LinkRow(prefix: String, link: String, onClick: () -> Unit) {
    Row {
        Text(prefix, fontSize = 12.sp)
        Text(
            link,
            fontSize = 12.sp,
            color = LinkBlue,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}

/** Small downward-pointing triangle for dropdown fields. */
@Composable
fun DropdownArrow(modifier: Modifier = Modifier, color: Color = Color.DarkGray) {
    Canvas(modifier.size(width = 14.dp, height = 8.dp)) {
        val triangle = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        drawPath(triangle, color)
    }
}

/** Grey dropdown that shows "Role" until the user picks one. */
@Composable
fun RoleDropdown(selected: UserRole?, onSelected: (UserRole) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(FieldGrey)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                selected?.label ?: "Role",
                fontSize = 14.sp,
                color = if (selected == null) Color.DarkGray else Color.Black,
                modifier = Modifier.weight(1f)
            )
            DropdownArrow()
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UserRole.entries.forEach { role ->
                DropdownMenuItem(
                    text = { Text(role.label) },
                    onClick = { onSelected(role); expanded = false }
                )
            }
        }
    }
}

/** A grey circular button with a short text label, like Settings and QR Code. */
@Composable
fun CircleLabelButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(FieldGrey)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, lineHeight = 11.sp, textAlign = TextAlign.Center, color = Color.Black)
    }
}

/**
 * Shared layout for Start, Login and Sign Up: gradient background, campsite picture area
 * at the top, and a white card with large rounded top corners at the bottom.
 *
 * @param headerImage Drawable for the campsite picture. Shows a placeholder when null.
 * @param cardContent Content placed inside the white card.
 */
@Composable
fun AuthScaffold(
    @DrawableRes headerImage: Int? = null,
    cardContent: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ScreenGradient)
    ) {
        if (headerImage != null) {
            Image(
                painter = painterResource(headerImage),
                contentDescription = "Campsite",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.TopCenter)
            )
        } else {
            Text(
                "(Campsite pic)",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 160.dp)
            )
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = cardContent
            )
        }
    }
}