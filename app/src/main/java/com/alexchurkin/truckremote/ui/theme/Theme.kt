package com.alexchurkin.truckremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF2CC75F)
private val DarkGreen = Color(0xFF1E7A3F)
private val Red = Color(0xFFDA4343)
private val Background = Color(0xFF242424)
private val SurfaceHigh = Color(0xFF2E2E2E)
private val SurfaceHighest = Color(0xFF383838)

// The app is always dark: it is used in the dark while driving
private val TruckRemoteColors = darkColorScheme(
    primary = Green,
    onPrimary = Color.Black,
    primaryContainer = DarkGreen,
    onPrimaryContainer = Color.White,
    secondaryContainer = DarkGreen,
    onSecondaryContainer = Color.White,
    error = Red,
    background = Background,
    surface = Background,
    surfaceContainer = SurfaceHigh,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
)

@Composable
fun TruckRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TruckRemoteColors, content = content)
}
