package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AgroColorScheme = lightColorScheme(
    primary = AgroGreenPrimary,
    onPrimary = AgroGreenOnPrimary,
    primaryContainer = AgroGreenContainer,
    onPrimaryContainer = AgroGreenOnContainer,
    secondary = AgroEarthBrownSecondary,
    onSecondary = AgroEarthBrownOnSecondary,
    secondaryContainer = AgroEarthBrownContainer,
    onSecondaryContainer = AgroEarthBrownOnContainer,
    background = AgroBackground,
    onBackground = AgroOnBackground,
    surface = AgroSurface,
    onSurface = AgroOnSurface,
    surfaceVariant = AgroSurfaceVariant,
    onSurfaceVariant = AgroOnSurfaceVariant,
    outline = AgroOutline,
    outlineVariant = AgroOutlineVariant,
    error = AgroError,
    onError = AgroOnError,
    errorContainer = AgroErrorContainer,
    onErrorContainer = AgroOnErrorContainer,
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Light earthy palette strictly enforced per design spec
    MaterialTheme(
        colorScheme = AgroColorScheme,
        typography = Typography,
        content = content
    )
}
