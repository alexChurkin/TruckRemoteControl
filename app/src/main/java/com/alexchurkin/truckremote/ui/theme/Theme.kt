package com.alexchurkin.truckremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Complete Material 3 dark scheme generated from the brand green #2CC75F
 * (Material Color Utilities, "fidelity" scheme, spec 2025): the brand color stays the primary container,
 * all the other roles are derived from it, so no component falls back to the baseline purple colors.
 * The same colors are used by the Material 3 dialogs of the main screen (values/colors.xml, m3_*).
 */
// The app is always dark: it is used in the dark while driving
private val TruckRemoteColors = darkColorScheme(
    primary = Color(0xFF51E478),
    onPrimary = Color(0xFF003914),
    primaryContainer = Color(0xFF2CC75F),
    onPrimaryContainer = Color(0xFF004D1D),
    inversePrimary = Color(0xFF006E2D),
    secondary = Color(0xFF97D59C),
    onSecondary = Color(0xFF003914),
    secondaryContainer = Color(0xFF195428),
    onSecondaryContainer = Color(0xFF8AC78F),
    tertiary = Color(0xFFFFB8AD),
    onTertiary = Color(0xFF5F150D),
    tertiaryContainer = Color(0xFFFF8E7E),
    onTertiaryContainer = Color(0xFF76261C),
    background = Color(0xFF0E150E),
    onBackground = Color(0xFFDCE5D9),
    surface = Color(0xFF0E150E),
    onSurface = Color(0xFFDCE5D9),
    surfaceVariant = Color(0xFF3D4A3D),
    onSurfaceVariant = Color(0xFFBCCBB9),
    surfaceTint = Color(0xFF4EE175),
    inverseSurface = Color(0xFFDCE5D9),
    inverseOnSurface = Color(0xFF2B322A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF869584),
    outlineVariant = Color(0xFF3D4A3D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF333B33),
    surfaceContainer = Color(0xFF1A211A),
    surfaceContainerHigh = Color(0xFF242C24),
    surfaceContainerHighest = Color(0xFF2F372E),
    surfaceContainerLow = Color(0xFF161D16),
    surfaceContainerLowest = Color(0xFF091009),
    surfaceDim = Color(0xFF0E150E),
)

@Composable
fun TruckRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TruckRemoteColors, content = content)
}
