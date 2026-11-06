package com.example.campbnb.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CampColors = lightColorScheme(
    primary = ButtonEnd,
    secondary = ButtonStart,
    tertiary = LinkBlue
)

@Composable
fun CampBnbTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CampColors, content = content)
}