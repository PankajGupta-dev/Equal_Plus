package com.bridgefy.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BridgeFyColorScheme = darkColorScheme(
    primary = MeshBlue,
    onPrimary = Color.White,
    primaryContainer = MeshNavySurface,
    onPrimaryContainer = MeshBlueBright,
    secondary = MeshGreen,
    onSecondary = Color.White,
    secondaryContainer = MeshGreenDim,
    onSecondaryContainer = MeshGreenLight,
    tertiary = BleBadge,
    background = MeshNavy,
    onBackground = TextPrimary,
    surface = MeshNavyLight,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceDivider,
    error = StatusFailed,
    onError = Color.White
)

@Composable
fun BridgeFyTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = BridgeFyColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MeshNavy.toArgb()
            window.navigationBarColor = MeshNavy.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
