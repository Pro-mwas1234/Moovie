package com.Moovie.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.Moovie.app.data.local.AccentColor
import com.Moovie.app.data.local.ThemeMode

// Moovie palette
val MoovieYellow = Color(0xFFF5C518)
val MoovieRed = Color(0xFFE23E57)
val MooviePurple = Color(0xFF8E7CC3)

/** Per-accent primary colors: bright for dark surfaces, deepened for light. */
private data class AccentPalette(
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
)

private val ACCENTS: Map<AccentColor, AccentPalette> = mapOf(
    AccentColor.YELLOW to AccentPalette(
        darkPrimary = Color(0xFFF5C518), darkOnPrimary = Color(0xFF1A1023),
        lightPrimary = Color(0xFF8A6D00), lightOnPrimary = Color.White,
    ),
    AccentColor.RED to AccentPalette(
        darkPrimary = Color(0xFFE23E57), darkOnPrimary = Color.White,
        lightPrimary = Color(0xFFB32239), lightOnPrimary = Color.White,
    ),
    AccentColor.PURPLE to AccentPalette(
        darkPrimary = Color(0xFF8E7CC3), darkOnPrimary = Color(0xFF1A1023),
        lightPrimary = Color(0xFF6A5299), lightOnPrimary = Color.White,
    ),
    AccentColor.TEAL to AccentPalette(
        darkPrimary = Color(0xFF4DD0C4), darkOnPrimary = Color(0xFF06211F),
        lightPrimary = Color(0xFF00897B), lightOnPrimary = Color.White,
    ),
)

private fun darkScheme(accent: AccentColor) = darkColorScheme(
    primary = ACCENTS.getValue(accent).darkPrimary,
    onPrimary = ACCENTS.getValue(accent).darkOnPrimary,
    secondary = when (accent) {
        AccentColor.RED -> MoovieYellow
        else -> MoovieRed
    },
    onSecondary = Color.White,
    tertiary = when (accent) {
        AccentColor.PURPLE -> MoovieRed
        else -> MooviePurple
    },
    background = Color(0xFF121016),
    onBackground = Color(0xFFEDE9F0),
    surface = Color(0xFF1C1822),
    onSurface = Color(0xFFEDE9F0),
    surfaceVariant = Color(0xFF2A2433),
    onSurfaceVariant = Color(0xFFB9B2C2),
    outline = Color(0xFF544C60),
)

private fun lightScheme(accent: AccentColor) = lightColorScheme(
    primary = ACCENTS.getValue(accent).lightPrimary,
    onPrimary = ACCENTS.getValue(accent).lightOnPrimary,
    secondary = when (accent) {
        AccentColor.RED -> Color(0xFF8A6D00)
        else -> MoovieRed
    },
    onSecondary = Color.White,
    tertiary = when (accent) {
        AccentColor.PURPLE -> Color(0xFFB32239)
        else -> Color(0xFF6A5299)
    },
    background = Color(0xFFFAF7F2),
    onBackground = Color(0xFF201A28),
    surface = Color.White,
    onSurface = Color(0xFF201A28),
    surfaceVariant = Color(0xFFEFE9F2),
    onSurfaceVariant = Color(0xFF514A5C),
    outline = Color(0xFFA79FB2),
)

@Composable
fun MoovieTheme(
    darkTheme: Boolean = true,
    accent: AccentColor = AccentColor.YELLOW,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme(accent) else lightScheme(accent),
        content = content,
    )
}

/** Preview color for the accent picker UI (the dark-theme primary). */
fun accentSwatch(accent: AccentColor): Color = ACCENTS.getValue(accent).darkPrimary

/** Convenience overload that resolves System mode against the OS setting. */
@Composable
fun MoovieTheme(
    themeMode: ThemeMode,
    accent: AccentColor,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MoovieTheme(darkTheme = dark, accent = accent, content = content)
}
