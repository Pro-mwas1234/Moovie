package com.Moovie.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Moovie palette
val MoovieYellow = Color(0xFFF5C518)
val MoovieRed = Color(0xFFE23E57)
val MooviePurple = Color(0xFF8E7CC3)

private val DarkScheme = darkColorScheme(
    primary = MoovieYellow,
    onPrimary = Color(0xFF1A1023),
    secondary = MoovieRed,
    onSecondary = Color.White,
    tertiary = MooviePurple,
    background = Color(0xFF121016),
    onBackground = Color(0xFFEDE9F0),
    surface = Color(0xFF1C1822),
    onSurface = Color(0xFFEDE9F0),
    surfaceVariant = Color(0xFF2A2433),
    onSurfaceVariant = Color(0xFFB9B2C2),
    outline = Color(0xFF544C60),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF8A6D00),
    onPrimary = Color.White,
    secondary = MoovieRed,
    onSecondary = Color.White,
    tertiary = Color(0xFF6A5299),
    background = Color(0xFFFAF7F2),
    onBackground = Color(0xFF201A28),
    surface = Color.White,
    onSurface = Color(0xFF201A28),
    surfaceVariant = Color(0xFFEFE9F2),
    onSurfaceVariant = Color(0xFF514A5C),
    outline = Color(0xFFA79FB2),
)

@Composable
fun MoovieTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
